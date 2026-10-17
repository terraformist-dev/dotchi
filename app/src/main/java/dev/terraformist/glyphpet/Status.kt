package dev.terraformist.glyphpet

/** Short words describing the pet, used on the pet screen. */
object Status {

    fun moodWord(state: PetState, now: Long): String = when (state.mood(now)) {
        Mood.EGG -> "EGG"
        Mood.GROWING -> "GROWING UP"
        Mood.SICK -> "ILL"
        Mood.SLEEPING -> if (state.isAtDaycare(now)) "NAPPING AT DAYCARE" else when (state.nap) {
            Nap.CHARGING -> "RECHARGING"
            Nap.DARK -> if (PetState.isNight(now)) "ASLEEP" else "NAPPING"
            null -> "ASLEEP"
        }
        Mood.HAPPY -> "HAPPY"
        Mood.SAD -> "UNHAPPY"
        Mood.EATING -> "EATING"
        Mood.PLAYING -> "PLAYING"
        Mood.CLEANING -> "FRESH"
        Mood.MEDICATED -> "YUCK!"
        Mood.WAKING -> "WAKING UP"
        Mood.WALKING -> "WALKING"
        Mood.OK -> "CONTENT"
    }

    fun title(state: PetState, now: Long): String =
        if (state.stage(now) == Stage.EGG) "EGG  ·  HATCHING SOON"
        else "${state.name(now)}  ·  ${moodWord(state, now)}"

    /** "FEED ●●●○○" style meters, one per line. */
    fun meters(state: PetState): String {
        fun dots(level: Int) = "●".repeat(level) + "○".repeat(5 - level)
        val lines = mutableListOf(
            "FEED  ${dots(state.level(Action.FEED))}",
            "PLAY  ${dots(state.level(Action.PLAY))}",
            "CLEAN ${dots(state.level(Action.CLEAN))}",
        )
        if (state.sick) lines += "ILL: NEEDS MEDS"
        return lines.joinToString("\n")
    }
}
