package dev.terraformist.glyphpet

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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

    // Shake the phone: wakes it if it's asleep, otherwise a quick play.
    // The light sensor and step counter are read here too.
    private val sensors by lazy {
        PetSensors(this, onShake = {
            PetStore.shake(this)
            render()
        })
    }

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
        findViewById<TextView>(R.id.meet).setOnClickListener {
            startActivity(Intent(this, MeetActivity::class.java))
        }

        findViewById<TextView>(R.id.reset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset_confirm) { _, _ -> PetStore.reset(this); render() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        askForPermissionsOnce()
        removeOldLockScreenCard()
    }

    /**
     * Build 7 had an optional lock-screen card. It's been removed; if it was switched on,
     * take the card and its half-hourly refresh away.
     */
    private fun removeOldLockScreenCard() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            cancel(1)
            deleteNotificationChannel("lock_screen_pet")
        }
        val old = PendingIntent.getBroadcast(
            this,
            100,
            Intent(this, PetWidgetProvider::class.java).setAction("dev.terraformist.glyphpet.REFRESH"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (old != null) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(old)
            old.cancel()
        }
        getSharedPreferences("app", Context.MODE_PRIVATE).edit().remove("lockScreenPet").apply()
    }

    /** Walks need the step counter; weather needs approximate location. Asked once, with a reason. */
    private fun askForPermissionsOnce() {
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("askedWalksWeather", false)) return
        if (Senses.canCountSteps(this) && Weather.canUse(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.permissions_title)
            .setMessage(R.string.permissions_message)
            .setPositiveButton(R.string.permissions_ok) { _, _ ->
                prefs.edit().putBoolean("askedWalksWeather", true).apply()
                requestPermissions(
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSIONS_REQUEST,
                )
            }
            .setNegativeButton(R.string.permissions_no) { _, _ ->
                prefs.edit().putBoolean("askedWalksWeather", true).apply()
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST) return
        // Start counting steps and fetch the weather now that we're allowed to.
        sensors.stop()
        sensors.start()
        Weather.refreshIfStale(this) { render() }
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
        sensors.start()
        Weather.refreshIfStale(this) { render() }
        PetWidgetProvider.refreshAll(this)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        sensors.stop()
        super.onPause()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        val weather = Weather.current(this, now)
        petView.setImageBitmap(
            DotPainter.paintPet(PetScene.render(state, now, animate = true, weather = weather), pitch = 24)
        )

        state.actions().forEachIndexed { slot, action ->
            val view = buttons[slot]
            view.setImageBitmap(PetWidgetProvider.buttonFor(action, state))
            view.contentDescription = PetWidgetProvider.label(action)
        }

        statusView.text = statusLine(state, now) + extrasLine(state, now, weather)
    }

    /** A second line with today's steps and the weather, when we have them. */
    private fun extrasLine(state: PetState, now: Long, weather: WeatherNow?): String {
        val parts = mutableListOf<String>()
        if (Senses.canCountSteps(this)) parts += "%,d STEPS".format(state.stepsOn(now))
        if (weather != null) parts += "${weather.label} ${weather.tempC}°C"
        return if (parts.isEmpty()) "" else "\n" + parts.joinToString("  ·  ")
    }

    private fun statusLine(state: PetState, now: Long): String {
        val stage = state.stage(now)
        if (stage == Stage.EGG) {
            val left = ((PetState.EGG_MS - (now - state.bornAt)) / 1000L).coerceAtLeast(0L)
            return "EGG  ·  HATCHING IN ${left / 60}:${"%02d".format(left % 60)}"
        }
        val mood = Status.moodWord(state, now)
        return "${state.name(now)}  ·  DAY ${state.day(now)}  ·  $mood"
    }

    companion object {
        private const val PERMISSIONS_REQUEST = 1

        /** A heading written in the dot font. */
        fun dotTitle(text: String) = DotGrid(DotFont.width(text), DotFont.HEIGHT).let { g ->
            g.text(text, 0, 0)
            DotPainter.paint(g, pitch = 10, ghosts = false)
        }
    }
}
