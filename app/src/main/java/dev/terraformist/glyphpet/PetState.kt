package dev.terraformist.glyphpet

import java.time.Instant
import java.time.ZoneId

enum class Stage { EGG, BABY, CHILD, ADULT }

/** MEDICINE is only offered while the pet is ill (it takes Play's place). */
enum class Action { FEED, PLAY, CLEAN, MEDICINE }

enum class Mood { EGG, SICK, SLEEPING, WAKING, WALKING, HAPPY, OK, SAD, EATING, PLAYING, CLEANING, MEDICATED }

/** Why the pet is napping outside its normal 11pm-7am sleep. */
enum class Nap { DARK, CHARGING }

/**
 * What the pet grows into. Which one depends on how well it was looked after
 * in the stage before ("neglect" = awake time spent with a need left unmet).
 */
enum class Form(val stage: Stage, val label: String) {
    PIP(Stage.CHILD, "PIP"),       // child, well looked after
    GRUB(Stage.CHILD, "GRUB"),     // child, often neglected
    NOVA(Stage.ADULT, "NOVA"),     // adult, excellent care
    BUD(Stage.ADULT, "BUD"),       // adult, decent care
    GLOOP(Stage.ADULT, "GLOOP"),   // adult, poor care
}

/**
 * Everything the pet "is", as plain numbers. No Android code in here.
 *
 * The pet never runs in the background. Instead, whenever anything looks at it,
 * [advanceTo] replays the time that has passed since [lastTick] and works out
 * how hungry, bored, messy or ill it has become, and whether it has grown up.
 */
data class PetState(
    val bornAt: Long,
    val lastTick: Long,
    val food: Float,      // 0..100, 100 = full
    val joy: Float,       // 0..100, 100 = delighted
    val poops: Int,       // 0..MAX_POOPS
    val nextPoopAt: Long,
    val lastAction: Action?,
    val lastActionAt: Long,
    val sick: Boolean = false,
    val sickRiskMs: Long = 0L,   // builds up while it's dirty or starving; at SICK_AFTER_MS it falls ill
    val neglectMs: Long = 0L,    // awake time with an unmet need, during the current stage
    val childForm: Form? = null,
    val adultForm: Form? = null,
    val nap: Nap? = null,        // napping right now, and why (set from the phone's sensors)
    val napSince: Long = 0L,
    val wokenAt: Long = 0L,      // last time it was woken up (by a shake, or the lights coming on)
    val stepsToday: Int = 0,     // steps walked with the phone today
    val stepsDay: Long = -1L,    // which day [stepsToday] belongs to (days since 1970, local time)
    val walkingUntil: Long = 0L, // shows the walking animation until then
) {

    fun stage(now: Long): Stage {
        val age = now - bornAt
        return when {
            age < EGG_MS -> Stage.EGG
            age < BABY_MS -> Stage.BABY
            age < CHILD_MS -> Stage.CHILD
            else -> Stage.ADULT
        }
    }

    /** The current form (null for egg and baby). */
    fun form(now: Long): Form? = when (stage(now)) {
        Stage.CHILD -> childForm ?: Form.PIP
        Stage.ADULT -> adultForm ?: Form.NOVA
        else -> null
    }

    /** Short name for status lines: BABY, PIP, NOVA... */
    fun name(now: Long): String = form(now)?.label ?: stage(now).name

    /** Whole days since hatching, starting at 1. */
    fun day(now: Long): Int = ((now - bornAt) / DAY_MS).toInt() + 1

    fun isReacting(now: Long): Boolean = lastAction != null && now - lastActionAt in 0L until REACT_MS

    fun mood(now: Long): Mood {
        if (stage(now) == Stage.EGG) return Mood.EGG
        if (isReacting(now)) {
            return when (lastAction) {
                Action.FEED -> Mood.EATING
                Action.PLAY -> Mood.PLAYING
                Action.MEDICINE -> Mood.MEDICATED
                else -> Mood.CLEANING
            }
        }
        if (isWaking(now)) return Mood.WAKING
        if (sick) return Mood.SICK
        if (isWalking(now)) return Mood.WALKING
        if (isAsleep(now)) return Mood.SLEEPING
        if (food < LOW || joy < LOW || poops >= 2) return Mood.SAD
        if (food >= HIGH && joy >= HIGH && poops == 0) return Mood.HAPPY
        return Mood.OK
    }

    /** Asleep for the night or napping, and not just woken up or out for a walk. */
    fun isAsleep(now: Long): Boolean =
        (isNight(now) || nap != null) && now - lastActionAt > AWAKE_AFTER_ACTION_MS &&
            !recentlyWoken(now) && !isWalking(now)

    /** Woken up in the last [WOKEN_AWAKE_MS]: stays awake, and won't start a new nap. */
    fun recentlyWoken(now: Long): Boolean = wokenAt > 0L && now - wokenAt in 0L until WOKEN_AWAKE_MS

    /** Playing the wake-up animation. */
    fun isWaking(now: Long): Boolean = wokenAt > 0L && now - wokenAt in 0L until WAKING_ANIM_MS

    fun isWalking(now: Long): Boolean = now < walkingUntil

    /** Wake it up (shake, or lights on). Doesn't count as playing. Call [advanceTo] first. */
    fun wake(now: Long): PetState = copy(nap = null, napSince = now, wokenAt = now)

    /** Today's steps, or 0 if none have been counted yet today. */
    fun stepsOn(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        if (stepsDay == epochDay(now, zone)) stepsToday else 0

    /**
     * The phone counted [steps] more steps. Every 1,000 cheers it up a little and makes it a
     * little hungry; reaching [STEP_GOAL] in a day is a treat. [live] means they were taken
     * just now (so show it walking), not added up while nothing was watching.
     */
    fun walked(steps: Int, now: Long, live: Boolean, zone: ZoneId = ZoneId.systemDefault()): PetState {
        if (steps <= 0) return this
        val today = epochDay(now, zone)
        val before = if (stepsDay == today) stepsToday else 0
        val after = before + steps
        if (stage(now) == Stage.EGG) return copy(stepsToday = after, stepsDay = today)
        val thousands = after / 1000 - before / 1000
        var joy = joy + JOY_PER_1000_STEPS * thousands
        val food = food - FOOD_PER_1000_STEPS * thousands
        val goal = before < STEP_GOAL && after >= STEP_GOAL
        if (goal) joy += STEP_GOAL_JOY
        return copy(
            stepsToday = after,
            stepsDay = today,
            joy = joy.coerceIn(0f, 100f),
            food = food.coerceIn(0f, 100f),
            walkingUntil = if (live) now + WALKING_SHOW_MS else walkingUntil,
            // A little celebration (hearts) for reaching the daily goal.
            lastAction = if (goal) Action.PLAY else lastAction,
            lastActionAt = if (goal) now else lastActionAt,
        )
    }

    /** Start or stop a nap. Call [advanceTo] first so the time before now is counted correctly. */
    fun withNap(reason: Nap?, now: Long): PetState =
        if (reason == nap) this else copy(nap = reason, napSince = now)

    /** The action the pet most needs right now, or null if it's fine. */
    fun mostNeeded(): Action? = when {
        sick -> Action.MEDICINE
        poops >= 2 -> Action.CLEAN
        food < LOW && food <= joy -> Action.FEED
        joy < LOW -> Action.PLAY
        food < LOW -> Action.FEED
        poops == 1 -> Action.CLEAN
        else -> null
    }

    /** The three actions on offer right now: Play turns into Medicine while it's ill. */
    fun actions(): List<Action> =
        listOf(Action.FEED, if (sick) Action.MEDICINE else Action.PLAY, Action.CLEAN)

    /** 0..5 dots for each meter on the buttons. */
    fun level(action: Action): Int = when (action) {
        Action.FEED -> ((food + 10f) / 20f).toInt().coerceIn(0, 5)
        Action.PLAY -> ((joy + 10f) / 20f).toInt().coerceIn(0, 5)
        Action.CLEAN -> when (poops) {
            0 -> 5
            1 -> 3
            2 -> 1
            else -> 0
        }
        Action.MEDICINE -> 0
    }

    /** Replay the time between [lastTick] and [now] in small steps. */
    fun advanceTo(now: Long, zone: ZoneId = ZoneId.systemDefault()): PetState {
        if (now <= lastTick) return this
        var t = lastTick
        var food = food
        var joy = joy
        var poops = poops
        var nextPoop = nextPoopAt
        var sick = sick
        var sickRisk = sickRiskMs
        var neglect = neglectMs
        var childForm = childForm
        var adultForm = adultForm

        // Don't replay more than two weeks: after that everything is at zero anyway.
        if (now - t > MAX_REPLAY_MS) t = now - MAX_REPLAY_MS

        while (t < now) {
            val step = minOf(STEP_MS, now - t)
            val end = t + step
            val mid = t + step / 2
            val hours = step / HOUR_MS.toFloat()

            if (stage(mid) != Stage.EGG) {
                // [nap] can only have changed at [napSince], which is never later than [lastTick].
                val napping = nap != null && mid >= napSince
                val night = isNight(mid, zone)
                // Shaken awake: stays up for a while, even at night.
                val woken = wokenAt > 0L && mid >= wokenAt && mid < wokenAt + WOKEN_AWAKE_MS
                val asleep = (night || napping) && !woken
                val pace = if (!asleep) 1f else if (night) SLEEP_PACE else NAP_PACE
                // Napping on the charger slowly cheers it up.
                if (asleep && napping && nap == Nap.CHARGING) joy += CHARGING_JOY_PER_HOUR * hours
                food -= FOOD_PER_HOUR * hours * pace
                val gloom = POOP_GLOOM_PER_HOUR * poops + if (sick) SICK_GLOOM_PER_HOUR else 0f
                joy -= (JOY_PER_HOUR + gloom) * hours * pace

                if (end >= nextPoop) {
                    if (asleep) {
                        // Hold it until morning.
                        nextPoop = end + 30 * MINUTE_MS
                    } else {
                        if (poops < MAX_POOPS) poops++
                        nextPoop = end + POOP_EVERY_MS
                    }
                }

                if (!asleep) {
                    // Falling ill: mess left lying around, or starving.
                    val risky = poops >= 2 || food < STARVING
                    sickRisk = if (risky) sickRisk + step else maxOf(0L, sickRisk - step)
                    if (!sick && sickRisk >= SICK_AFTER_MS) {
                        sick = true
                        sickRisk = 0L
                    }
                    // Neglect: any need left unmet while it's awake.
                    if (sick || poops >= 2 || food < LOW || joy < LOW) neglect += step
                }
            } else {
                nextPoop = maxOf(nextPoop, end + POOP_EVERY_MS)
            }

            // Growing up: pick a form based on how it was cared for, then start counting afresh.
            val before = stage(t)
            val after = stage(end)
            if (before == Stage.BABY && after != Stage.BABY && childForm == null) {
                childForm = if (neglect <= CHILD_GOOD_MAX_NEGLECT_MS) Form.PIP else Form.GRUB
                neglect = 0L
            }
            if (before == Stage.CHILD && after == Stage.ADULT && adultForm == null) {
                adultForm = when {
                    neglect <= ADULT_GREAT_MAX_NEGLECT_MS -> Form.NOVA
                    neglect <= ADULT_OK_MAX_NEGLECT_MS -> Form.BUD
                    else -> Form.GLOOP
                }
                neglect = 0L
            }
            t = end
        }

        // If it was left alone so long that we skipped its growing up, it grew up neglected.
        val stageNow = stage(now)
        if (stageNow >= Stage.CHILD && childForm == null) childForm = Form.GRUB
        if (stageNow == Stage.ADULT && adultForm == null) adultForm = Form.GLOOP

        return copy(
            lastTick = now,
            food = food.coerceIn(0f, 100f),
            joy = joy.coerceIn(0f, 100f),
            poops = poops,
            nextPoopAt = nextPoop,
            sick = sick,
            sickRiskMs = sickRisk,
            neglectMs = neglect,
            childForm = childForm,
            adultForm = adultForm,
        )
    }

    /** Apply a feed / play / clean / medicine. Call [advanceTo] first. */
    fun apply(action: Action, now: Long): PetState {
        if (stage(now) == Stage.EGG) return this
        return when (action) {
            Action.FEED -> copy(
                food = (food + 35f).coerceAtMost(100f),
                joy = if (food >= 95f) (joy - 5f).coerceAtLeast(0f) else joy,
                nextPoopAt = minOf(nextPoopAt, now + 90 * MINUTE_MS),
                lastAction = action,
                lastActionAt = now,
            )
            Action.PLAY -> played(won = true, now = now, boost = 30f)
            Action.CLEAN -> copy(
                poops = 0,
                joy = if (poops > 0) (joy + 5f).coerceAtMost(100f) else joy,
                lastAction = action,
                lastActionAt = now,
            )
            Action.MEDICINE -> if (!sick) this else copy(
                sick = false,
                sickRiskMs = 0L,
                joy = (joy - 5f).coerceAtLeast(0f), // it doesn't like the taste
                lastAction = action,
                lastActionAt = now,
            )
        }
    }

    /** Met a friend's pet (first time today with that friend): a big cheer-up and some hearts. */
    fun metFriend(now: Long): PetState {
        if (stage(now) == Stage.EGG) return this
        return copy(
            joy = (joy + FRIEND_JOY).coerceAtMost(100f),
            lastAction = Action.PLAY,
            lastActionAt = now,
        )
    }

    /** After a mini-game (or a quick play on the Glyph Matrix). Ill pets can't play. */
    fun played(won: Boolean, now: Long, boost: Float = if (won) 40f else 15f): PetState {
        if (stage(now) == Stage.EGG || sick) return this
        return copy(
            joy = (joy + boost).coerceAtMost(100f),
            food = (food - 5f).coerceAtLeast(0f),
            lastAction = Action.PLAY,
            lastActionAt = now,
        )
    }

    companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DAY_MS = 24 * HOUR_MS

        const val EGG_MS = 3 * MINUTE_MS
        const val BABY_MS = 1 * DAY_MS
        const val CHILD_MS = 3 * DAY_MS

        const val REACT_MS = 3_000L
        const val AWAKE_AFTER_ACTION_MS = 60_000L

        const val FOOD_PER_HOUR = 5.5f
        const val JOY_PER_HOUR = 4.5f
        const val POOP_GLOOM_PER_HOUR = 2f
        const val SICK_GLOOM_PER_HOUR = 4f
        const val SLEEP_PACE = 0.3f
        const val NAP_PACE = 0.5f
        const val WOKEN_AWAKE_MS = 15 * MINUTE_MS
        const val WAKING_ANIM_MS = 2_600L

        const val JOY_PER_1000_STEPS = 6f
        const val FOOD_PER_1000_STEPS = 3f
        const val STEP_GOAL = 5_000
        const val STEP_GOAL_JOY = 15f
        const val WALKING_SHOW_MS = 15_000L
        const val FRIEND_JOY = 20f
        const val CHARGING_JOY_PER_HOUR = 6f
        const val POOP_EVERY_MS = 4 * HOUR_MS
        const val MAX_POOPS = 3

        const val LOW = 25f
        const val HIGH = 60f
        const val STARVING = 10f
        const val SICK_AFTER_MS = 2 * HOUR_MS

        // Evolution thresholds: awake hours with needs unmet during the previous stage.
        const val CHILD_GOOD_MAX_NEGLECT_MS = 3 * HOUR_MS
        const val ADULT_GREAT_MAX_NEGLECT_MS = 4 * HOUR_MS
        const val ADULT_OK_MAX_NEGLECT_MS = 12 * HOUR_MS

        const val NIGHT_START_HOUR = 23
        const val NIGHT_END_HOUR = 7

        private const val STEP_MS = 10 * MINUTE_MS
        private const val MAX_REPLAY_MS = 14 * DAY_MS

        fun newEgg(now: Long) = PetState(
            bornAt = now,
            lastTick = now,
            food = 80f,
            joy = 80f,
            poops = 0,
            nextPoopAt = now + EGG_MS + POOP_EVERY_MS,
            lastAction = null,
            lastActionAt = 0L,
        )

        fun epochDay(time: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay()

        fun isNight(time: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
            val hour = Instant.ofEpochMilli(time).atZone(zone).hour
            return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
        }
    }
}
