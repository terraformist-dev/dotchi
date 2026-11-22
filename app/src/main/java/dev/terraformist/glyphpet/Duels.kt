package dev.terraformist.glyphpet

import kotlin.math.abs
import kotlin.random.Random

/**
 * Games two Dotchi can play together when they meet (see [MeetActivity]).
 *
 * Both phones play the same game at the same time, side by side. They agree on a shared random
 * number first, so every round is identical on both phones (same speed, same bushes, same
 * waits). Each phone times its own player's taps, so a slow Bluetooth message can never make
 * it unfair; only scores (and, for In Sync, each round's pick) are swapped.
 */
enum class DuelGame(val code: Int, val title: String, val coop: Boolean = false, val lowerWins: Boolean = false) {
    STOP_DOT(1, "STOP THE DOT"),          // stop the running light in the bright zone; most hits wins
    PEEKABOO(2, "PEEKABOO"),              // tap the bush Dotchi peeks from; most catches wins
    QUICK_DRAW(3, "QUICK DRAW", lowerWins = true), // tap as soon as "!" shows; fastest total wins
    TAP_RACE(4, "TAP RACE"),              // tap as fast as you can for 6 seconds; most taps wins
    IN_SYNC(5, "IN SYNC", coop = true);   // both secretly pick left or right; score together when you match

    companion object {
        fun of(code: Int): DuelGame? = entries.firstOrNull { it.code == code }
    }
}

/** What the friend's phone last said about its side of the game. */
class FriendSide(val round: Int, val score: Int, val choice: Int, val prevChoice: Int)

/** One player's side of a duel. */
class DuelPlay(val game: DuelGame, seed: Int, val startAt: Long) {

    private val rnd = Random(seed)

    // Rolled up front from the shared number, so both phones get exactly the same rounds.
    private val peekBush = IntArray(ROUNDS) { rnd.nextInt(3) }
    private val peekDelay = LongArray(ROUNDS) { 600L + rnd.nextLong(1_000L) }
    private val drawDelay = LongArray(ROUNDS) { 1_500L + rnd.nextLong(2_500L) }

    val rounds = if (game == DuelGame.TAP_RACE) 1 else ROUNDS

    /** The round being played, from 0; equals [rounds] once finished. */
    var round = 0
        private set
    var score = 0
        private set

    /** In Sync: my pick this round (1 left, 2 right, 0 not yet) and last round's. */
    var choice = 0
        private set
    var prevChoice = 0
        private set

    /** A short note about the last round, e.g. "0.23 SECONDS" or "TOO SOON!". */
    var note = ""
        private set

    val done: Boolean get() = round >= rounds

    private var roundStart = startAt
    private var resultUntil = 0L
    private var lastResult: Boolean? = null
    private var stoppedAt = -1
    private var taps = 0

    fun buttons(): List<String> = when (game) {
        DuelGame.STOP_DOT -> listOf("STOP")
        DuelGame.PEEKABOO -> listOf("LEFT", "MIDDLE", "RIGHT")
        DuelGame.QUICK_DRAW -> listOf("DRAW!")
        DuelGame.TAP_RACE -> listOf("TAP!")
        DuelGame.IN_SYNC -> listOf("LEFT", "RIGHT")
    }

    private fun inRound(now: Long) = !done && now >= roundStart && resultUntil == 0L

    /** A button was pressed. */
    fun press(i: Int, now: Long) {
        if (!inRound(now)) return
        when (game) {
            DuelGame.STOP_DOT -> if (stoppedAt < 0) {
                stoppedAt = dotPosition(now)
                val hit = abs(stoppedAt - TRACK_CENTER) <= zoneHalf()
                if (hit) score++
                finishRound(hit, now)
            }
            DuelGame.PEEKABOO -> {
                if (now < peekFrom()) return // nothing showing yet: ignore early taps
                val caught = now < peekUntil() && i == peekBush[round]
                if (caught) score++
                finishRound(caught, now)
            }
            DuelGame.QUICK_DRAW -> {
                if (now < signalAt()) {
                    score += FOUL_MS
                    note = "TOO SOON!"
                    finishRound(false, now)
                } else {
                    val ms = (now - signalAt()).toInt()
                    score += ms
                    note = "%.2f SECONDS".format(ms / 1000f)
                    finishRound(true, now)
                }
            }
            DuelGame.TAP_RACE -> if (now < roundStart + TAP_RACE_MS) {
                taps++
                score = taps
            }
            DuelGame.IN_SYNC -> if (choice == 0) choice = if (i == 0) 1 else 2
        }
    }

    /** The picture was tapped, [x] from 0 (left) to 1 (right). */
    fun tapPicture(x: Float, now: Long) {
        val i = when (game) {
            DuelGame.PEEKABOO -> (x * 3).toInt().coerceIn(0, 2)
            DuelGame.IN_SYNC -> if (x < 0.5f) 0 else 1
            else -> 0
        }
        press(i, now)
    }

    /** Called every frame: moves rounds on, and notices timeouts and the friend's picks. */
    fun tick(now: Long, friend: FriendSide?) {
        if (done || now < startAt) return
        if (resultUntil > 0L) {
            if (now >= resultUntil) nextRound(now)
            return
        }
        when (game) {
            DuelGame.PEEKABOO -> if (now >= peekUntil()) finishRound(false, now)
            DuelGame.QUICK_DRAW -> if (now >= signalAt() + DRAW_TIMEOUT_MS) {
                score += DRAW_TIMEOUT_MS
                note = "TOO SLOW!"
                finishRound(false, now)
            }
            DuelGame.TAP_RACE -> if (now >= roundStart + TAP_RACE_MS) finishRound(true, now, 1_500L)
            DuelGame.IN_SYNC -> if (choice != 0 && friend != null) {
                // Their pick for this round: still on it, or already moved on to the next one.
                val theirs = when {
                    friend.round == round -> friend.choice
                    friend.round == round + 1 -> friend.prevChoice
                    else -> 0
                }
                if (theirs != 0) {
                    val match = theirs == choice
                    if (match) score++
                    note = if (match) "IN SYNC!" else "OUT OF SYNC"
                    finishRound(match, now)
                }
            }
            DuelGame.STOP_DOT -> {}
        }
    }

    private fun finishRound(result: Boolean, now: Long, show: Long = 1_300L) {
        lastResult = result
        resultUntil = now + show
    }

    private fun nextRound(now: Long) {
        prevChoice = choice
        choice = 0
        round++
        roundStart = now
        resultUntil = 0L
        lastResult = null
        stoppedAt = -1
        note = ""
    }

    // ---- Timings -------------------------------------------------------------------------

    private fun r() = round.coerceIn(0, ROUNDS - 1)
    private fun zoneHalf() = DOT_ZONE_HALF[r()]
    private fun peekFrom() = roundStart + peekDelay[r()]
    private fun peekUntil() = peekFrom() + PEEK_SHOW_MS[r()]
    private fun signalAt() = roundStart + drawDelay[r()]

    private fun dotPosition(now: Long): Int {
        val span = TRACK_RIGHT - TRACK_LEFT
        val steps = (((now - roundStart).coerceAtLeast(0L)) / DOT_STEP_MS[r()]).toInt() % (span * 2)
        return TRACK_LEFT + if (steps <= span) steps else span * 2 - steps
    }

    // ---- Drawing -------------------------------------------------------------------------

    /** The picture for this moment, with my own Dotchi. */
    fun render(now: Long, me: PetState): DotGrid {
        val counting = now < startAt
        val countdown = (((startAt - now) / 1000L) + 1).toString()
        if (game == DuelGame.PEEKABOO && !counting) return peekaboo(now)

        val mood = when {
            lastResult == true -> Mood.HAPPY
            lastResult == false -> Mood.SAD
            game == DuelGame.TAP_RACE && !counting && !done -> Mood.EATING
            else -> Mood.OK
        }
        val look = if (game == DuelGame.IN_SYNC && choice != 0) (if (choice == 1) -1 else 1) else 0
        val top = when {
            counting -> countdown
            done -> null
            game == DuelGame.TAP_RACE -> taps.toString()
            game == DuelGame.QUICK_DRAW && resultUntil == 0L && now >= signalAt() -> "!"
            game == DuelGame.IN_SYNC && choice != 0 && resultUntil == 0L -> "?"
            else -> null
        }
        val g = PetScene.render(me, now, animate = true, look = look, moodOverride = mood, topText = top?.takeIf { it != "?" }, hud = false)
        // Waiting for the friend's pick: three dots blinking.
        if (top == "?") for (i in 0 until 3) g.set(10 + i * 2, 3, if ((now / 300) % 3 == i.toLong()) 220 else 60)
        if (game == DuelGame.STOP_DOT && !counting && !done) drawTrack(g, now)
        return g
    }

    private fun drawTrack(g: DotGrid, now: Long) {
        val half = zoneHalf()
        for (x in TRACK_LEFT..TRACK_RIGHT) {
            for (y in TRACK_Y - 1..TRACK_Y + 1) g.set(x, y, 0)
            g.set(x, TRACK_Y, if (abs(x - TRACK_CENTER) <= half) 90 else 30)
        }
        g.set(TRACK_CENTER - half - 1, TRACK_Y - 1, 60)
        g.set(TRACK_CENTER + half + 1, TRACK_Y - 1, 60)
        val pos = if (stoppedAt >= 0) stoppedAt else dotPosition(now)
        val missed = stoppedAt >= 0 && lastResult == false
        g.set(pos, TRACK_Y, 255, red = missed)
    }

    private fun peekaboo(now: Long): DotGrid {
        val g = DotGrid(25, 25)
        for (x in 0 until 25 step 2) g.set(x, GROUND_Y, 30)
        for (b in 0 until 3) g.stamp(BUSH, BUSH_X[b], BUSH_TOP, level = 110)
        if (!done) {
            val showing = lastResult != null || (now >= peekFrom() && now < peekUntil())
            if (showing) {
                val bush = peekBush[r()]
                val cx = BUSH_X[bush] + BUSH.width / 2
                val top = BUSH_TOP - HEAD.height
                g.stamp(HEAD, cx - HEAD.width / 2, top)
                when (lastResult) {
                    true -> g.stamp(HEART, cx - HEART.width / 2, top - HEART.height - 1, red = true)
                    false -> g.text("!", cx - 1, top - 6, level = 160)
                    null -> {}
                }
            }
        }
        for (y in 0 until 25) for (x in 0 until 25) if (!GlyphShape.contains(x, y)) g.set(x, y, 0)
        return g
    }

    companion object {
        const val ROUNDS = 5
        const val TAP_RACE_MS = 6_000L
        const val FOUL_MS = 1_000
        const val DRAW_TIMEOUT_MS = 1_500
        const val COUNTDOWN_MS = 3_000L

        private const val TRACK_Y = 3
        private const val TRACK_LEFT = 4
        private const val TRACK_RIGHT = 20
        private const val TRACK_CENTER = 12
        private val DOT_ZONE_HALF = intArrayOf(2, 2, 1, 1, 0)
        private val DOT_STEP_MS = longArrayOf(90L, 75L, 65L, 55L, 45L)

        private val PEEK_SHOW_MS = longArrayOf(1_000L, 900L, 800L, 700L, 600L)
        private const val BUSH_TOP = 16
        private const val GROUND_Y = 20
        private val BUSH_X = intArrayOf(1, 9, 17)
        private val BUSH = Sprite("..###..", ".#####.", "#######", "#######")
        private val HEAD = Sprite(".###.", "#####", "#.#.#", "#####", "#####")
        private val HEART = Sprite(".#.#.", "#####", ".###.", "..#..")
    }
}
