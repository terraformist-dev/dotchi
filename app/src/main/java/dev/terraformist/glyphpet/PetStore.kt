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

    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val NAP_CHECK_EVERY_MS = 5_000L
    private var lastNapCheck = 0L

    /** The pet as it is right now. Also starts or ends naps based on what the phone senses. */
    fun get(context: Context, now: Long = System.currentTimeMillis()): PetState = synchronized(lock) {
        val saved = load(context, now)
        var current = saved.advanceTo(now)
        var dirty = now - saved.lastTick > SAVE_EVERY_MS
        if (now - lastNapCheck > NAP_CHECK_EVERY_MS) {
            lastNapCheck = now
            val wanted = Senses.napReason(context, current.nap, now)
            if (wanted != current.nap) {
                current = current.withNap(wanted, now)
                dirty = true
            }
        }
        if (dirty) save(context, current)
        current
    }

    /** End a nap caused by darkness (e.g. the phone was flipped over to say hello). */
    fun wakeFromDarkNap(context: Context, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val current = load(context, now).advanceTo(now)
            if (current.nap == Nap.DARK) save(context, current.withNap(null, now))
        }
    }

    /** Feed / play / clean, save, and refresh every place the pet is shown. */
    fun act(context: Context, action: Action, now: Long = System.currentTimeMillis()): PetState {
        val updated = synchronized(lock) {
            val next = load(context, now).advanceTo(now).apply(action, now)
            save(context, next)
            next
        }
        PetWidgetProvider.refreshAll(context)
        return updated
    }

    /** Record the result of a mini-game. */
    fun played(context: Context, won: Boolean, now: Long = System.currentTimeMillis()): PetState {
        val updated = synchronized(lock) {
            val next = load(context, now).advanceTo(now).played(won, now)
            save(context, next)
            next
        }
        PetWidgetProvider.refreshAll(context)
        return updated
    }

    /** Start over with a fresh egg. */
    fun reset(context: Context, now: Long = System.currentTimeMillis()): PetState {
        val egg = PetState.newEgg(now)
        synchronized(lock) { save(context, egg) }
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
            .apply()
    }
}
