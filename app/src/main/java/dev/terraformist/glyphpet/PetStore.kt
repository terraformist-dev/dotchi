package dev.terraformist.glyphpet

import android.content.Context
import android.content.SharedPreferences

/**
 * The one place the pet is saved. The widget, the Glyph Toy and the app all
 * read and write through here, so they always show the same pet.
 */
object PetStore {

    private const val PREFS = "pet"
    private const val SAVE_EVERY_MS = 5 * PetState.MINUTE_MS
    private const val NAP_CHECK_EVERY_MS = 5_000L

    private val lock = Any()
    private var lastNapCheck = 0L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The weather, in the terms the pet cares about. */
    private fun climate(context: Context, now: Long): Climate? = Weather.current(context, now)?.let {
        Climate(
            rainy = it.sky == Sky.RAIN || it.sky == Sky.STORM,
            snowy = it.sky == Sky.SNOW,
            hot = it.tempC >= PetState.HOT_C,
        )
    }

    /**
     * Load and bring up to date. Also records things for the Album: forms seen, and a pet
     * that has set off on its adventure.
     */
    private fun current(context: Context, now: Long): Pair<PetState, Boolean> {
        val saved = load(context, now)
        var s = saved.advanceTo(now, climate = climate(context, now))
        var changed = s.lastTick != saved.lastTick && now - saved.lastTick > SAVE_EVERY_MS
        if (s.albumPending) {
            Album.addGeneration(context, s.farewellGen, s.farewellName, s.farewellForm, s.bornAt)
            s = s.copy(albumPending = false)
            changed = true
        }
        if (s.generation != saved.generation) changed = true
        s.form(now)?.let { Album.noteForm(context, PetState.codeOf(it)) }
        if (s.stage(now) == Stage.BABY) Album.noteForm(context, 1)
        return s to changed
    }

    /**
     * The pet as it is right now. Also starts or ends naps based on what the phone senses.
     * [lookingNow] is true for the app and the Glyph Matrix: they play one-off animations
     * (growing up, goodbyes), so those start only when someone can actually see them.
     */
    fun get(
        context: Context,
        now: Long = System.currentTimeMillis(),
        checkNapNow: Boolean = false,
        lookingNow: Boolean = false,
    ): PetState = synchronized(lock) {
        var (s, dirty) = current(context, now)
        if (checkNapNow || now - lastNapCheck > NAP_CHECK_EVERY_MS) {
            lastNapCheck = now
            val p = prefs(context)
            val charging = Senses.isCharging(context)
            val justPluggedIn = charging && !p.getBoolean("wasCharging", false)
            if (charging != p.getBoolean("wasCharging", false)) p.edit().putBoolean("wasCharging", charging).apply()
            var wanted = Senses.napReason(context, s.nap, now)
            if (s.recentlyWoken(now)) {
                if (justPluggedIn && wanted == Nap.CHARGING) {
                    // Plugging in is a clear "time for a nap", even just after waking it.
                    s = s.copy(wokenAt = 0L)
                    dirty = true
                } else {
                    // Just woken up (e.g. shaken awake on the charger): stays awake for a while.
                    wanted = null
                }
            }
            if (wanted != s.nap) {
                // Lights on or unplugged: wake up properly (with the stretch), not just stop napping.
                s = if (wanted == null) s.wake(now) else s.withNap(wanted, now)
                dirty = true
            }
        }
        if (lookingNow) {
            val next = s.nextCutscene(now)
            if (next != s) {
                s = next
                dirty = true
            }
        }
        if (dirty) save(context, s)
        s
    }

    /** Change the pet under the lock, save it, and refresh the widget. */
    private fun update(context: Context, now: Long, refresh: Boolean = true, change: (PetState) -> PetState): PetState {
        val updated = synchronized(lock) {
            val next = change(current(context, now).first)
            save(context, next)
            next
        }
        if (refresh) PetWidgetProvider.refreshAll(context)
        return updated
    }

    /** Wake it up (shaking a sleeping pet). Doesn't count as playing. */
    fun wake(context: Context, now: Long = System.currentTimeMillis()): PetState =
        update(context, now) { it.wake(now) }

    /** A shake: wakes it if it's asleep, otherwise a quick play. */
    fun shake(context: Context, now: Long = System.currentTimeMillis()): PetState {
        val s = get(context, now)
        return if (s.isAsleep(now)) wake(context, now) else act(context, Action.PLAY, now)
    }

    /** Steps counted by the phone. Doesn't refresh the widget (steps can arrive every second). */
    fun walked(context: Context, steps: Int, live: Boolean, now: Long = System.currentTimeMillis()) {
        val s = update(context, now, refresh = false) { it.walked(steps, now, live) }
        Album.noteSteps(context, steps, s.stepsOn(now))
    }

    /** Forget today's step count (used once, after the step-counting fix). */
    fun clearTodaysSteps(context: Context, now: Long = System.currentTimeMillis()) {
        update(context, now) { it.copy(stepsToday = 0) }
    }

    /** Met a friend's pet for the first time today. */
    fun metFriend(context: Context, now: Long = System.currentTimeMillis()): PetState =
        update(context, now) { it.metFriend(now) }

    /** Feed / play / clean / medicine, save, and refresh every place the pet is shown. */
    fun act(context: Context, action: Action, now: Long = System.currentTimeMillis()): PetState {
        val rainy = climate(context, now)?.rainy == true
        return update(context, now) { s ->
            if (action == Action.PLAY && rainy) s.played(true, now, boost = 30f * PetState.RAINY_PLAY_FACTOR)
            else s.apply(action, now)
        }
    }

    /** Record the result of a mini-game (app or Glyph Matrix). */
    fun played(context: Context, won: Boolean, now: Long = System.currentTimeMillis()): PetState {
        val factor = if (climate(context, now)?.rainy == true) PetState.RAINY_PLAY_FACTOR else 1f
        return update(context, now) {
            val next = it.played(won, now, boost = (if (won) 40f else 15f) * factor)
            // A win (not a refusal) earns the happy glow.
            if (won && next.lastActionAt == now) next.copy(glowAt = now) else next
        }
    }

    /** It turns down a game because it's played out (shakes its head). */
    fun refusePlay(context: Context, now: Long = System.currentTimeMillis()): PetState =
        update(context, now) { it.refusePlay(now) }

    fun rename(context: Context, name: String): PetState =
        update(context, System.currentTimeMillis()) { it.named(name) }

    fun startDaycare(context: Context, now: Long = System.currentTimeMillis()): PetState =
        update(context, now) { it.startDaycare(now) }

    fun endDaycare(context: Context, now: Long = System.currentTimeMillis()): PetState =
        update(context, now) { it.endDaycare(now) }

    /** Start over with a fresh egg (the old pet isn't added to the album). */
    fun reset(context: Context, now: Long = System.currentTimeMillis()): PetState {
        val egg = synchronized(lock) {
            val old = current(context, now).first
            val next = PetState.newEgg(now).copy(generation = old.generation + 1)
            save(context, next)
            next
        }
        PetWidgetProvider.refreshAll(context)
        return egg
    }

    private fun load(context: Context, now: Long): PetState {
        val p = prefs(context)
        if (!p.contains("bornAt")) {
            val egg = PetState.newEgg(now)
            save(context, egg)
            return egg
        }
        return PetState(
            bornAt = p.getLong("bornAt", now),
            lastTick = p.getLong("lastTick", now),
            food = p.getFloat("food", 80f),
            joy = p.getFloat("joy", 80f),
            poops = p.getInt("poops", 0),
            nextPoopAt = p.getLong("nextPoopAt", now + PetState.POOP_EVERY_MS),
            lastAction = p.getString("lastAction", null)?.let { name ->
                Action.entries.firstOrNull { it.name == name }
            },
            lastActionAt = p.getLong("lastActionAt", 0L),
            sick = p.getBoolean("sick", false),
            sickRiskMs = p.getLong("sickRiskMs", 0L),
            neglectMs = p.getLong("neglectMs", 0L),
            childForm = formNamed(p.getString("childForm", null)),
            adultForm = formNamed(p.getString("adultForm", null)),
            nap = p.getString("nap", null)?.let { name -> Nap.entries.firstOrNull { it.name == name } },
            napSince = p.getLong("napSince", 0L),
            wokenAt = p.getLong("wokenAt", 0L),
            stepsToday = p.getInt("stepsToday", 0),
            stepsDay = p.getLong("stepsDay", -1L),
            walkingUntil = p.getLong("walkingUntil", 0L),
            name = p.getString("name", "") ?: "",
            generation = p.getInt("generation", 1),
            revealedCode = p.getInt("revealedCode", -1),
            cutscene = p.getInt("cutscene", Cutscene.NONE),
            cutsceneAt = p.getLong("cutsceneAt", 0L),
            farewellPending = p.getBoolean("farewellPending", false),
            albumPending = p.getBoolean("albumPending", false),
            farewellForm = formNamed(p.getString("farewellForm", null)),
            farewellName = p.getString("farewellName", "") ?: "",
            farewellGen = p.getInt("farewellGen", 0),
            daycareUntil = p.getLong("daycareUntil", 0L),
            daycareDay = p.getLong("daycareDay", -1L),
            overfedAt = p.getLong("overfedAt", 0L),
            extraMess = p.getInt("extraMess", 0),
            playCount = p.getInt("playCount", 0),
            playBurstStart = p.getLong("playBurstStart", 0L),
            playedOutUntil = p.getLong("playedOutUntil", 0L),
            refusedAt = p.getLong("refusedAt", 0L),
            glowAt = p.getLong("glowAt", 0L),
        )
    }

    private fun formNamed(name: String?): Form? = Form.entries.firstOrNull { it.name == name }

    private fun save(context: Context, s: PetState) {
        prefs(context).edit()
            .putLong("bornAt", s.bornAt)
            .putLong("lastTick", s.lastTick)
            .putFloat("food", s.food)
            .putFloat("joy", s.joy)
            .putInt("poops", s.poops)
            .putLong("nextPoopAt", s.nextPoopAt)
            .putString("lastAction", s.lastAction?.name)
            .putLong("lastActionAt", s.lastActionAt)
            .putBoolean("sick", s.sick)
            .putLong("sickRiskMs", s.sickRiskMs)
            .putLong("neglectMs", s.neglectMs)
            .putString("childForm", s.childForm?.name)
            .putString("adultForm", s.adultForm?.name)
            .putString("nap", s.nap?.name)
            .putLong("napSince", s.napSince)
            .putLong("wokenAt", s.wokenAt)
            .putInt("stepsToday", s.stepsToday)
            .putLong("stepsDay", s.stepsDay)
            .putLong("walkingUntil", s.walkingUntil)
            .putString("name", s.name)
            .putInt("generation", s.generation)
            .putInt("revealedCode", s.revealedCode)
            .putInt("cutscene", s.cutscene)
            .putLong("cutsceneAt", s.cutsceneAt)
            .putBoolean("farewellPending", s.farewellPending)
            .putBoolean("albumPending", s.albumPending)
            .putString("farewellForm", s.farewellForm?.name)
            .putString("farewellName", s.farewellName)
            .putInt("farewellGen", s.farewellGen)
            .putLong("daycareUntil", s.daycareUntil)
            .putLong("daycareDay", s.daycareDay)
            .putLong("overfedAt", s.overfedAt)
            .putInt("extraMess", s.extraMess)
            .putInt("playCount", s.playCount)
            .putLong("playBurstStart", s.playBurstStart)
            .putLong("playedOutUntil", s.playedOutUntil)
            .putLong("refusedAt", s.refusedAt)
            .putLong("glowAt", s.glowAt)
            .apply()
    }
}
