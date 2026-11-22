package dev.terraformist.glyphpet

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * Mini-games, opened from Play in the app or on the widget.
 *
 *  - LEFT OR RIGHT: guess which way the pet will look.
 *  - HIGHER OR LOWER: a number 1-9 is shown; guess if the next one is higher or lower.
 *  - STOP THE DOT: a light runs along a row; stop it inside the bright zone. The zone shrinks
 *    and the light speeds up each round.
 *  - PEEKABOO: Dotchi peeks out from behind one of three bushes for a moment; tap that bush
 *    before it ducks down again. It hides faster each round.
 *
 * Five rounds each; three or more right is a win. Winning makes the pet much happier
 * than losing, but playing at all helps.
 */
class GameActivity : Activity() {

    private enum class Game(val title: String) {
        LEFT_RIGHT("LEFT OR RIGHT"),
        HIGH_LOW("HIGHER OR LOWER"),
        STOP_DOT("STOP THE DOT"),
        PEEKABOO("PEEKABOO"),
    }

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var titleView: ImageView
    private lateinit var infoView: TextView
    private lateinit var petView: ImageView
    private lateinit var choices: List<TextView>
    private val petDots by lazy { LiveDots(petView, pitch = 24) }

    private var game: Game? = null
    private var round = 0
    private var score = 0
    private var waiting = false
    private var finished = false

    // Guessing games: what the pet shows during a round.
    private var look = 0
    private var reaction: Mood? = null
    private var number = 0

    // Stop the Dot.
    private var dotStart = 0L
    private var dotStoppedAt = -1 // where it was stopped, or -1 while it's still running

    // Peekaboo.
    private var peekBush = -1      // which bush it's behind this round
    private var peekFrom = 0L      // when it pops up
    private var peekUntil = 0L     // when it ducks down again
    private var peekResult: Boolean? = null // true = caught it, false = missed

    private val loop = object : Runnable {
        override fun run() {
            tick()
            renderPet()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = PetStore.get(this)
        val now = System.currentTimeMillis()
        val reason = when {
            state.stage(now) == Stage.EGG -> "Wait for the egg to hatch first."
            state.sick -> "Dotchi is ill. Give it medicine first."
            state.isBusy(now) -> "Dotchi is busy. Try again in a moment."
            else -> null
        }
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_game)
        titleView = findViewById(R.id.title)
        infoView = findViewById(R.id.info)
        petView = findViewById(R.id.pet)
        choices = listOf(findViewById(R.id.choice_a), findViewById(R.id.choice_b), findViewById(R.id.choice_c), findViewById(R.id.choice_d))
        findViewById<TextView>(R.id.back).setOnClickListener { finish() }

        choices.forEachIndexed { i, view -> view.setOnClickListener { onChoice(i, view) } }

        // In Stop the Dot and Peekaboo you can also tap the picture itself.
        petView.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                when (game) {
                    Game.STOP_DOT -> if (!finished) stopDot(v)
                    Game.PEEKABOO -> if (!finished) tapBush((e.x / v.width * 3).toInt().coerceIn(0, 2), v)
                    else -> {}
                }
            }
            true
        }

        showMenu()
    }

    override fun onResume() {
        super.onResume()
        if (::petView.isInitialized) handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    /** Show these labels on the buttons (in order); the rest are hidden. */
    private fun buttons(vararg labels: String) {
        choices.forEachIndexed { i, view ->
            if (i < labels.size) {
                view.text = labels[i]
                view.visibility = View.VISIBLE
            } else {
                view.visibility = View.GONE
            }
        }
    }

    private fun showMenu() {
        game = null
        finished = false
        waiting = false
        reaction = null
        look = 0
        titleView.setImageBitmap(MainActivity.dotTitle("PLAY"))
        infoView.text = "PICK A GAME"
        buttons(*Game.entries.map { it.title }.toTypedArray())
    }

    private fun start(g: Game) {
        game = g
        round = 1
        score = 0
        finished = false
        titleView.setImageBitmap(MainActivity.dotTitle(g.title))
        when (g) {
            Game.LEFT_RIGHT -> buttons("LEFT", "RIGHT")
            Game.HIGH_LOW -> buttons("HIGHER", "LOWER")
            Game.STOP_DOT -> buttons("STOP")
            Game.PEEKABOO -> buttons("LEFT", "MIDDLE", "RIGHT")
        }
        nextRound()
    }

    private fun nextRound() {
        waiting = false
        reaction = null
        look = 0
        val now = System.currentTimeMillis()
        when (game) {
            Game.HIGH_LOW -> number = Random.nextInt(2, 9) // 2..8, so both answers are possible
            Game.STOP_DOT -> {
                dotStart = now
                dotStoppedAt = -1
            }
            Game.PEEKABOO -> {
                peekBush = Random.nextInt(3)
                peekFrom = now + Random.nextLong(600L, 1_600L)
                peekUntil = peekFrom + PEEK_SHOW_MS[(round - 1).coerceIn(0, ROUNDS - 1)]
                peekResult = null
            }
            else -> {}
        }
        infoView.text = "ROUND $round OF $ROUNDS  ·  SCORE $score"
    }

    private fun onChoice(i: Int, view: View) {
        if (waiting) return
        val g = game
        when {
            finished -> if (i == 0) start(g ?: Game.LEFT_RIGHT) else finish()
            g == null -> start(Game.entries[i])
            g == Game.STOP_DOT -> stopDot(view)
            g == Game.PEEKABOO -> tapBush(i, view)
            else -> guess(g, first = i == 0)
        }
    }

    private fun guess(g: Game, first: Boolean) {
        waiting = true
        val correct = when (g) {
            Game.LEFT_RIGHT -> {
                look = if (Random.nextBoolean()) -1 else 1
                (first && look == -1) || (!first && look == 1)
            }
            Game.HIGH_LOW -> {
                var next = Random.nextInt(1, 10)
                while (next == number) next = Random.nextInt(1, 10)
                val higher = next > number
                number = next
                (first && higher) || (!first && !higher)
            }
            else -> false
        }
        roundResult(correct)
    }

    // ---- Stop the Dot ---------------------------------------------------------------------

    /** Where the running light is now (it bounces end to end). */
    private fun dotPosition(now: Long): Int {
        val stepMs = DOT_STEP_MS[(round - 1).coerceIn(0, ROUNDS - 1)]
        val span = TRACK_RIGHT - TRACK_LEFT
        val steps = ((now - dotStart) / stepMs).toInt() % (span * 2)
        return TRACK_LEFT + if (steps <= span) steps else span * 2 - steps
    }

    private fun zoneHalf(): Int = DOT_ZONE_HALF[(round - 1).coerceIn(0, ROUNDS - 1)]

    private fun stopDot(view: View) {
        if (waiting || dotStoppedAt >= 0) return
        waiting = true
        dotStoppedAt = dotPosition(System.currentTimeMillis())
        roundResult(kotlin.math.abs(dotStoppedAt - TRACK_CENTER) <= zoneHalf(), view)
    }

    // ---- Peekaboo -------------------------------------------------------------------------

    private fun peeking(now: Long): Boolean = now in peekFrom until peekUntil

    private fun tapBush(bush: Int, view: View) {
        if (waiting || peekResult != null) return
        val now = System.currentTimeMillis()
        if (now < peekFrom) return // nothing showing yet: ignore early taps
        waiting = true
        val caught = peeking(now) && bush == peekBush
        peekResult = caught
        peekUntil = now + 700L // stay up a moment to show what happened
        roundResult(caught, view)
    }

    /** Called every frame: Peekaboo needs to notice when Dotchi ducks down without being caught. */
    private fun tick() {
        if (game != Game.PEEKABOO || finished || waiting) return
        val now = System.currentTimeMillis()
        if (peekResult == null && now >= peekUntil) {
            waiting = true
            peekResult = false
            peekUntil = now + 500L
            roundResult(false)
        }
    }

    // ---- Rounds ---------------------------------------------------------------------------

    private fun roundResult(correct: Boolean, view: View? = null) {
        if (correct) score++
        reaction = if (correct) Mood.HAPPY else Mood.SAD
        infoView.text = (if (correct) "YES!" else "NOPE") + "  ·  SCORE $score"
        view?.performHapticFeedback(if (correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
        handler.postDelayed({
            if (round < ROUNDS) {
                round++
                nextRound()
            } else {
                endGame()
            }
        }, 1_300L)
    }

    private fun endGame() {
        finished = true
        waiting = false
        look = 0
        val won = score >= WIN_SCORE
        PetStore.played(this, won)
        reaction = null // show the pet's real (now playful) mood
        infoView.text = if (won) "YOU WON  ·  $score OF $ROUNDS" else "$score OF $ROUNDS  ·  NICE TRY"
        buttons("PLAY AGAIN", "DONE")
    }

    // ---- Drawing --------------------------------------------------------------------------

    private fun renderPet() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        val inRound = game != null && !finished
        if (inRound && game == Game.PEEKABOO) {
            petDots.show(peekabooScene(now))
            return
        }
        val grid = PetScene.render(
            state,
            now,
            animate = true,
            look = look,
            moodOverride = if (inRound) reaction ?: Mood.OK else null,
            topText = if (inRound && game == Game.HIGH_LOW) number.toString() else null,
            hud = !inRound,
        )
        if (inRound && game == Game.STOP_DOT) drawTrack(grid, now)
        petDots.show(grid)
    }

    /** The Stop the Dot track across the top: faint row, brighter zone in the middle, the running light. */
    private fun drawTrack(g: DotGrid, now: Long) {
        val half = zoneHalf()
        for (x in TRACK_LEFT..TRACK_RIGHT) {
            for (y in TRACK_Y - 1..TRACK_Y + 1) g.set(x, y, 0)
            g.set(x, TRACK_Y, if (kotlin.math.abs(x - TRACK_CENTER) <= half) 90 else 30)
        }
        // Little end stops show the edges of the zone.
        g.set(TRACK_CENTER - half - 1, TRACK_Y - 1, 60)
        g.set(TRACK_CENTER + half + 1, TRACK_Y - 1, 60)
        val pos = if (dotStoppedAt >= 0) dotStoppedAt else dotPosition(now)
        val hit = dotStoppedAt >= 0 && kotlin.math.abs(dotStoppedAt - TRACK_CENTER) <= half
        g.set(pos, TRACK_Y, 255, red = dotStoppedAt >= 0 && !hit)
    }

    /** Three bushes, and Dotchi peeking out from behind one of them. */
    private fun peekabooScene(now: Long): DotGrid {
        val g = DotGrid(25, 25)
        for (x in 0 until 25 step 2) g.set(x, GROUND_Y, 30)
        for (b in 0 until 3) g.stamp(BUSH, BUSH_X[b], BUSH_TOP, level = 110)
        val showing = peekResult != null || peeking(now)
        if (showing && peekBush >= 0) {
            val cx = BUSH_X[peekBush] + BUSH.width / 2
            // It pops up quickly: one row up on the first frame, then all the way.
            val rise = if (now - peekFrom < FRAME_MS) 2 else 0
            val top = BUSH_TOP - HEAD.height + rise
            g.stamp(HEAD, cx - HEAD.width / 2, top)
            // Re-draw the bush over its lower half so it's peeking from behind.
            g.stamp(BUSH, BUSH_X[peekBush], BUSH_TOP, level = 110)
            when (peekResult) {
                true -> g.stamp(HEART, cx - HEART.width / 2, top - HEART.height - 1, red = true)
                false -> g.text("!", cx - 1, top - 6, level = 160)
                null -> {}
            }
        }
        for (y in 0 until 25) for (x in 0 until 25) if (!GlyphShape.contains(x, y)) g.set(x, y, 0)
        return g
    }

    private companion object {
        const val ROUNDS = 5
        const val WIN_SCORE = 3
        const val FRAME_MS = 40L

        // Stop the Dot: the track, how big the zone is and how fast the light runs, round by round.
        const val TRACK_Y = 3
        const val TRACK_LEFT = 4
        const val TRACK_RIGHT = 20
        const val TRACK_CENTER = 12
        val DOT_ZONE_HALF = intArrayOf(2, 2, 1, 1, 0)
        val DOT_STEP_MS = longArrayOf(90L, 75L, 65L, 55L, 45L)

        // Peekaboo: how long it stays up, round by round, and where things are.
        val PEEK_SHOW_MS = longArrayOf(1_000L, 900L, 800L, 700L, 600L)
        const val BUSH_TOP = 16
        const val GROUND_Y = 20
        val BUSH_X = intArrayOf(1, 9, 17)
        val BUSH = Sprite(
            "..###..",
            ".#####.",
            "#######",
            "#######",
        )
        val HEAD = Sprite(
            ".###.",
            "#####",
            "#.#.#",
            "#####",
            "#####",
        )
        val HEART = Sprite(
            ".#.#.",
            "#####",
            ".###.",
            "..#..",
        )
    }
}
