package dev.terraformist.glyphpet

import java.time.Instant
import java.time.ZoneId

enum class Stage { EGG, BABY, CHILD, ADULT }

enum class Action { FEED, PLAY, CLEAN }

enum class Mood { EGG, SLEEPING, HAPPY, OK, SAD, EATING, PLAYING, CLEANING }

/**
 * Everything the pet "is", as plain numbers. No Android code in here.
 *
 * The pet never runs in the background. Instead, whenever anything looks at it,
 * [advanceTo] replays the time that has passed since [lastTick] and works out
 * how hungry, bored and messy it has become.
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

    /** Whole days since hatching, starting at 1. */
    fun day(now: Long): Int = ((now - bornAt) / DAY_MS).toInt() + 1

    fun isReacting(now: Long): Boolean = lastAction != null && now - lastActionAt in 0L until REACT_MS

    fun mood(now: Long): Mood {
        if (stage(now) == Stage.EGG) return Mood.EGG
        if (isReacting(now)) {
            return when (lastAction) {
                Action.FEED -> Mood.EATING
                Action.PLAY -> Mood.PLAYING
                else -> Mood.CLEANING
            }
        }
        if (isNight(now) && now - lastActionAt > AWAKE_AFTER_ACTION_MS) return Mood.SLEEPING
        if (food < LOW || joy < LOW || poops >= 2) return Mood.SAD
        if (food >= HIGH && joy >= HIGH && poops == 0) return Mood.HAPPY
        return Mood.OK
    }

    /** The action the pet most needs right now, or null if it's fine. */
    fun mostNeeded(): Action? = when {
        poops >= 2 -> Action.CLEAN
        food < LOW && food <= joy -> Action.FEED
        joy < LOW -> Action.PLAY
        food < LOW -> Action.FEED
        poops == 1 -> Action.CLEAN
        else -> null
    }

    /** 0..5 dots for each meter on the widget. */
    fun level(action: Action): Int = when (action) {
        Action.FEED -> ((food + 10f) / 20f).toInt().coerceIn(0, 5)
        Action.PLAY -> ((joy + 10f) / 20f).toInt().coerceIn(0, 5)
        Action.CLEAN -> when (poops) {
            0 -> 5
            1 -> 3
            2 -> 1
            else -> 0
        }
    }

    /** Replay the time between [lastTick] and [now] in small steps. */
    fun advanceTo(now: Long, zone: ZoneId = ZoneId.systemDefault()): PetState {
        if (now <= lastTick) return this
        var t = lastTick
        var food = food
        var joy = joy
        var poops = poops
        var nextPoop = nextPoopAt

        // Don't replay more than two weeks: after that everything is at zero anyway.
        if (now - t > MAX_REPLAY_MS) t = now - MAX_REPLAY_MS

        while (t < now) {
            val step = minOf(STEP_MS, now - t)
            val mid = t + step / 2
            val hours = step / HOUR_MS.toFloat()
            val stage = stage(mid)
            if (stage != Stage.EGG) {
                val asleep = isNight(mid, zone)
                val pace = if (asleep) SLEEP_PACE else 1f
                food -= FOOD_PER_HOUR * hours * pace
                joy -= (JOY_PER_HOUR + POOP_GLOOM_PER_HOUR * poops) * hours * pace
                if (t + step >= nextPoop) {
                    if (asleep) {
                        // Hold it until morning.
                        nextPoop = t + step + 30 * MINUTE_MS
                    } else {
                        if (poops < MAX_POOPS) poops++
                        nextPoop = t + step + POOP_EVERY_MS
                    }
                }
            } else {
                nextPoop = maxOf(nextPoop, t + step + POOP_EVERY_MS)
            }
            t += step
        }

        return copy(
            lastTick = now,
            food = food.coerceIn(0f, 100f),
            joy = joy.coerceIn(0f, 100f),
            poops = poops,
            nextPoopAt = nextPoop,
        )
    }

    /** Apply a feed / play / clean. Call [advanceTo] first. */
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
            Action.PLAY -> copy(
                joy = (joy + 30f).coerceAtMost(100f),
                food = (food - 5f).coerceAtLeast(0f),
                lastAction = action,
                lastActionAt = now,
            )
            Action.CLEAN -> copy(
                poops = 0,
                joy = if (poops > 0) (joy + 5f).coerceAtMost(100f) else joy,
                lastAction = action,
                lastActionAt = now,
            )
        }
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
        const val SLEEP_PACE = 0.3f
        const val POOP_EVERY_MS = 4 * HOUR_MS
        const val MAX_POOPS = 3

        const val LOW = 25f
        const val HIGH = 60f

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

        fun isNight(time: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
            val hour = Instant.ofEpochMilli(time).atZone(zone).hour
            return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
        }
    }
}
