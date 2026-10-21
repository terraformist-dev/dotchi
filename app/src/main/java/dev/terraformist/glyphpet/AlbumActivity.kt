package dev.terraformist.glyphpet

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Album: every form you've raised (unknown ones as faint outlines), every pet that has
 * set off on its adventure, friends met, and walking records.
 */
class AlbumActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(24)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ImageView(this).apply {
            setImageBitmap(MainActivity.dotTitle("ALBUM"))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            contentDescription = "Album"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28))
        })

        val now = System.currentTimeMillis()
        val pet = PetStore.get(this, now)
        val seen = Album.formsSeen(this)

        // Forms: baby, the two children, the three adults, and the rare one.
        column.addView(heading("FORMS  ·  ${seen.count { it in 1..7 }} OF 7 FOUND"))
        column.addView(formRow(listOf(1), seen))
        column.addView(formRow(listOf(2, 3), seen))
        column.addView(formRow(listOf(4, 5, 6), seen))
        column.addView(formRow(listOf(7), seen))
        column.addView(body("Different care raises different forms. Faint outlines are ones you haven't raised yet. The last one is rare."))

        // Generations.
        column.addView(heading("GENERATIONS"))
        val past = Album.pastPets(this)
        val nowName = pet.name.ifEmpty { pet.name(now) }
        val leaves = DATE.format(Instant.ofEpochMilli(pet.departsAt()).atZone(ZoneId.systemDefault()))
        // Its personality shows here once it's grown up.
        val nowTrait = if (pet.stage(now) == Stage.ADULT) "  ·  ${pet.trait.label}" else ""
        val lines = mutableListOf("NO. ${pet.generation}  $nowName$nowTrait  ·  NOW  ·  SETS OFF ${leaves.uppercase()}")
        for (p in past.reversed()) {
            val who = p.name.ifEmpty { p.form?.label ?: "PET" }
            val form = (if (p.name.isNotEmpty()) " (${p.form?.label ?: "?"})" else "") +
                (p.trait?.let { "  ·  ${it.label}" } ?: "")
            val left = DATE.format(Instant.ofEpochMilli(p.leftAt).atZone(ZoneId.systemDefault()))
            lines += "NO. ${p.generation}  $who$form  ·  LEFT ${left.uppercase()}"
        }
        column.addView(body(lines.joinToString("\n")))

        // Friends and walks.
        column.addView(heading("FRIENDS"))
        val friends = Friends.count(this)
        column.addView(body(if (friends == 0) "None yet. Tap MEET with a friend nearby." else "$friends met so far."))

        column.addView(heading("WALKS"))
        column.addView(
            body(
                if (!Senses.canCountSteps(this)) "Walks are off (step counter not allowed)."
                else "%,d steps in all  ·  best day %,d".format(Album.totalSteps(this), Album.bestDay(this))
            )
        )

        column.addView(TextView(this).apply {
            text = "BACK"
            setTextColor(0xFF9A9A9A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(32), dp(16), dp(8))
            setOnClickListener { finish() }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            fitsSystemWindows = true
            addView(column)
        })
    }

    /** A row of form portraits with their names (or "???" if not found yet). */
    private fun formRow(codes: List<Int>, seen: Set<Int>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(12), 0, 0)
        for (code in codes) {
            val known = code in seen
            addView(LinearLayout(this@AlbumActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
                addView(ImageView(this@AlbumActivity).apply {
                    setImageBitmap(DotPainter.paint(PetScene.portrait(code, known), pitch = 12))
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(dp(80), dp(80))
                    contentDescription = if (known) PetScene.nameForCode(code) else "Not found yet"
                })
                addView(TextView(this@AlbumActivity).apply {
                    text = if (known) PetScene.nameForCode(code) else "???"
                    setTextColor(if (known) 0xFFFFFFFF.toInt() else 0xFF555555.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                })
            })
        }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.1f
        setPadding(0, dp(28), 0, dp(4))
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFBDBDBD.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.MONOSPACE
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(8), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}
