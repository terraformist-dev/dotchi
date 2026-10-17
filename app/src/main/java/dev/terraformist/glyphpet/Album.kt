package dev.terraformist.glyphpet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keepsakes that last across generations: every form you've raised, every pet that has set
 * off on its adventure, and walking records. (Friends are kept by [Friends].)
 */
object Album {

    private const val PREFS = "album"

    class PastPet(val generation: Int, val name: String, val form: Form?, val leftAt: Long)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Cached so the Glyph Toy can call noteForm many times a second cheaply.
    @Volatile private var knownForms: Set<String>? = null

    fun formsSeen(context: Context): Set<Int> =
        (prefs(context).getStringSet("forms", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()

    /** Codes as in [PetState.formCode]: 1 baby, 2 PIP, 3 GRUB, 4 NOVA, 5 BUD, 6 GLOOP. */
    fun noteForm(context: Context, code: Int) {
        val key = code.toString()
        val cached = knownForms ?: (prefs(context).getStringSet("forms", emptySet()) ?: emptySet()).also { knownForms = it }
        if (key in cached) return
        val updated = cached + key
        knownForms = updated
        prefs(context).edit().putStringSet("forms", updated).apply()
    }

    fun addGeneration(context: Context, generation: Int, name: String, form: Form?, leftAt: Long) {
        val list = JSONArray(prefs(context).getString("generations", "[]"))
        list.put(
            JSONObject()
                .put("g", generation)
                .put("n", name)
                .put("f", form?.name ?: "")
                .put("t", leftAt)
        )
        prefs(context).edit().putString("generations", list.toString()).apply()
    }

    fun pastPets(context: Context): List<PastPet> {
        val list = JSONArray(prefs(context).getString("generations", "[]"))
        return (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            PastPet(
                generation = o.optInt("g"),
                name = o.optString("n"),
                form = Form.entries.firstOrNull { it.name == o.optString("f") },
                leftAt = o.optLong("t"),
            )
        }
    }

    fun noteSteps(context: Context, added: Int, today: Int) {
        val p = prefs(context)
        p.edit()
            .putLong("totalSteps", p.getLong("totalSteps", 0L) + added)
            .putInt("bestDay", maxOf(p.getInt("bestDay", 0), today))
            .apply()
    }

    fun totalSteps(context: Context): Long = prefs(context).getLong("totalSteps", 0L)
    fun bestDay(context: Context): Int = prefs(context).getInt("bestDay", 0)
}
