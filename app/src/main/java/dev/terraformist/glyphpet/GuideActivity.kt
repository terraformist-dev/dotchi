package dev.terraformist.glyphpet

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shows [Guide]: one heading and paragraph per section. */
class GuideActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(24)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ImageView(this).apply {
            setImageBitmap(MainActivity.dotTitle("GUIDE"))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            contentDescription = "Guide"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28))
        })

        for (section in Guide.sections) {
            column.addView(TextView(this).apply {
                text = section.heading
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.1f
                setPadding(0, dp(28), 0, dp(8))
            })
            column.addView(TextView(this).apply {
                text = section.body
                setTextColor(0xFFBDBDBD.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.MONOSPACE
                setLineSpacing(dp(3).toFloat(), 1f)
            })
            if (section.heading == Guide.NUDGES_HEADING) column.addView(nudgeSwitch())
        }

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

    /** The on/off switch for Glyph nudges, shown under that section. */
    private fun nudgeSwitch(): TextView = TextView(this).apply {
        fun show() {
            val on = Nudges.isOn(this@GuideActivity)
            text = if (on) "GLYPH NUDGES: ON  ·  TAP TO TURN OFF" else "GLYPH NUDGES: OFF  ·  TAP TO TURN ON"
            setTextColor(if (on) 0xFFFFFFFF.toInt() else 0xFFD71921.toInt())
        }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setBackgroundResource(R.drawable.button_bg)
        val pad = dp(14)
        setPadding(pad, pad, pad, pad)
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) }
        show()
        setOnClickListener {
            Nudges.setOn(this@GuideActivity, !Nudges.isOn(this@GuideActivity))
            show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
