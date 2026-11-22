package dev.terraformist.glyphpet

import android.app.Activity
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small shared pieces for the screens that are built in code (Guide, Album). */
object UiBits {

    /**
     * A scrolling page with a proper BACK button fixed at the bottom of the screen, where your
     * thumb is, instead of at the very end of the page.
     */
    fun pageWithBack(activity: Activity, content: View): View {
        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            fitsSystemWindows = true
            addView(
                ScrollView(activity).apply { addView(content) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(
                TextView(activity).apply {
                    text = "BACK"
                    setTextColor(0xFFFFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    letterSpacing = 0.2f
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.button_bg)
                    setOnClickListener { activity.finish() }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                    setMargins(dp(16), dp(8), dp(16), dp(16))
                },
            )
        }
    }
}
