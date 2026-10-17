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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
