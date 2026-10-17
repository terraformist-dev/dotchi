package dev.terraformist.glyphpet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView

/** The app screen: a big animated pet, the three actions, and a Guide button. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var petView: ImageView
    private lateinit var statusView: TextView
    private lateinit var buttons: List<ImageView>

    private val loop = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 120L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        petView = findViewById(R.id.pet)
        statusView = findViewById(R.id.status)
        buttons = listOf<ImageView>(findViewById(R.id.btn_feed), findViewById(R.id.btn_play), findViewById(R.id.btn_clean))

        findViewById<ImageView>(R.id.title).setImageBitmap(dotTitle("GLYPH PET"))

        // Each button does whatever action is in that slot right now
        // (the middle one is Play, or Medicine while the pet is ill).
        buttons.forEachIndexed { slot, view ->
            view.setOnClickListener { onSlot(slot) }
        }

        findViewById<TextView>(R.id.guide).setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java))
        }

        findViewById<TextView>(R.id.reset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset_confirm) { _, _ -> PetStore.reset(this); render() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun onSlot(slot: Int) {
        val action = PetStore.get(this).actions()[slot]
        if (action == Action.PLAY) {
            startActivity(Intent(this, GameActivity::class.java))
        } else {
            PetStore.act(this, action)
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(loop)
        PetWidgetProvider.refreshAll(this)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        super.onPause()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        petView.setImageBitmap(DotPainter.paintPet(PetScene.render(state, now, animate = true), pitch = 24))

        state.actions().forEachIndexed { slot, action ->
            val view = buttons[slot]
            view.setImageBitmap(PetWidgetProvider.buttonFor(action, state))
            view.contentDescription = PetWidgetProvider.label(action)
        }

        statusView.text = statusLine(state, now)
    }

    private fun statusLine(state: PetState, now: Long): String {
        val stage = state.stage(now)
        if (stage == Stage.EGG) {
            val left = ((PetState.EGG_MS - (now - state.bornAt)) / 1000L).coerceAtLeast(0L)
            return "EGG  ·  HATCHING IN ${left / 60}:${"%02d".format(left % 60)}"
        }
        val mood = when (state.mood(now)) {
            Mood.SICK -> "ILL"
            Mood.SLEEPING -> "ASLEEP"
            Mood.HAPPY -> "HAPPY"
            Mood.SAD -> "UNHAPPY"
            Mood.EATING -> "EATING"
            Mood.PLAYING -> "PLAYING"
            Mood.CLEANING -> "FRESH"
            Mood.MEDICATED -> "YUCK!"
            else -> "CONTENT"
        }
        return "${state.name(now)}  ·  DAY ${state.day(now)}  ·  $mood"
    }

    companion object {
        /** A heading written in the dot font. */
        fun dotTitle(text: String) = DotGrid(DotFont.width(text), DotFont.HEIGHT).let { g ->
            g.text(text, 0, 0)
            DotPainter.paint(g, pitch = 10, ghosts = false)
        }
    }
}
