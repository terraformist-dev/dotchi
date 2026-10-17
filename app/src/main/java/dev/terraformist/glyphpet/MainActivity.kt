package dev.terraformist.glyphpet

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView

/** The app screen: a big animated pet, the three actions, and setup instructions. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var petView: ImageView
    private lateinit var statusView: TextView
    private lateinit var feed: ImageView
    private lateinit var play: ImageView
    private lateinit var clean: ImageView

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
        feed = findViewById(R.id.btn_feed)
        play = findViewById(R.id.btn_play)
        clean = findViewById(R.id.btn_clean)

        val title = DotGrid(DotFont.width("GLYPH PET"), DotFont.HEIGHT)
        title.text("GLYPH PET", 0, 0)
        findViewById<ImageView>(R.id.title).setImageBitmap(DotPainter.paint(title, pitch = 10, ghosts = false))

        feed.setOnClickListener { PetStore.act(this, Action.FEED); render() }
        play.setOnClickListener { PetStore.act(this, Action.PLAY); render() }
        clean.setOnClickListener { PetStore.act(this, Action.CLEAN); render() }

        findViewById<TextView>(R.id.reset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset_confirm) { _, _ -> PetStore.reset(this); render() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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

        val need = state.mostNeeded()
        feed.setImageBitmap(PetWidgetProvider.buttonBitmap("FEED", state.level(Action.FEED), need == Action.FEED))
        play.setImageBitmap(PetWidgetProvider.buttonBitmap("PLAY", state.level(Action.PLAY), need == Action.PLAY))
        clean.setImageBitmap(PetWidgetProvider.buttonBitmap("CLEAN", state.level(Action.CLEAN), need == Action.CLEAN))

        statusView.text = statusLine(state, now)
    }

    private fun statusLine(state: PetState, now: Long): String {
        val stage = state.stage(now)
        if (stage == Stage.EGG) {
            val left = ((PetState.EGG_MS - (now - state.bornAt)) / 1000L).coerceAtLeast(0L)
            return "EGG  ·  HATCHING IN ${left / 60}:${"%02d".format(left % 60)}"
        }
        val mood = when (state.mood(now)) {
            Mood.SLEEPING -> "ASLEEP"
            Mood.HAPPY -> "HAPPY"
            Mood.SAD -> "UNHAPPY"
            Mood.EATING -> "EATING"
            Mood.PLAYING -> "PLAYING"
            Mood.CLEANING -> "FRESH"
            else -> "CONTENT"
        }
        return "${stage.name}  ·  DAY ${state.day(now)}  ·  $mood"
    }
}
