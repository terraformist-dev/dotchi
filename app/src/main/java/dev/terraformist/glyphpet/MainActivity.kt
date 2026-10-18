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
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The app screen: a big animated pet, the three actions, and Guide / Album / Meet. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var titleView: ImageView
    private lateinit var petView: ImageView
    private lateinit var statusView: TextView
    private lateinit var daycareView: TextView
    private lateinit var buttons: List<ImageView>

    private var shownTitle: String? = null
    private var pokedAt = 0L
    private var namePromptOpen = false

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

        titleView = findViewById(R.id.title)
        petView = findViewById(R.id.pet)
        statusView = findViewById(R.id.status)
        daycareView = findViewById(R.id.daycare)
        buttons = listOf<ImageView>(findViewById(R.id.btn_feed), findViewById(R.id.btn_play), findViewById(R.id.btn_clean))

        // Tap the name to rename the pet.
        titleView.setOnClickListener { askForName() }

        // Each button does whatever action is in that slot right now
        // (the middle one is Play, or Medicine while the pet is ill).
        buttons.forEachIndexed { slot, view ->
            view.setOnClickListener { onSlot(slot) }
        }

        findViewById<TextView>(R.id.guide).setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java))
        }
        findViewById<TextView>(R.id.album).setOnClickListener {
            startActivity(Intent(this, AlbumActivity::class.java))
        }
        findViewById<TextView>(R.id.meet).setOnClickListener {
            startActivity(Intent(this, MeetActivity::class.java))
        }
        daycareView.setOnClickListener { onDaycare() }

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
        Steps.resetOnceAfterFix(this)
    }

    // ---- Naming -------------------------------------------------------------------------

    /** Ask once per pet, after it has hatched (and not during its reveal). */
    private fun maybeAskForName(state: PetState, now: Long) {
        if (namePromptOpen || state.name.isNotEmpty()) return
        if (state.stage(now) == Stage.EGG || state.isGrowing(now) || state.isFarewell(now)) return
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("askedWalksWeather", false)) return // one question at a time
        if (prefs.getInt("namePromptedGen", 0) >= state.generation) return
        prefs.edit().putInt("namePromptedGen", state.generation).apply()
        askForName()
    }

    private fun askForName() {
        if (namePromptOpen) return
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        if (state.stage(now) == Stage.EGG) return
        namePromptOpen = true
        val input = EditText(this).apply {
            setText(state.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            // The dot font has capital letters, numbers, spaces and "!".
            filters = arrayOf(
                InputFilter.LengthFilter(MAX_NAME),
                InputFilter { source, start, end, _, _, _ ->
                    val typed = source.subSequence(start, end).toString()
                    val cleaned = typed.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == ' ' || it == '!' }
                    if (cleaned == typed) null else cleaned
                },
            )
            hint = "UP TO $MAX_NAME LETTERS"
        }
        val box = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(if (state.name.isEmpty()) "Name your pet" else "Rename your pet")
            .setMessage("Its name shows at the top of this screen. Tap the name any time to change it.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                PetStore.rename(this, input.text.toString().trim().uppercase().take(MAX_NAME))
                render()
            }
            .setNegativeButton("Not now", null)
            .setOnDismissListener { namePromptOpen = false }
            .show()
    }

    // ---- Daycare ------------------------------------------------------------------------

    private fun onDaycare() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        when {
            state.isAtDaycare(now) -> PetStore.endDaycare(this, now)
            state.canUseDaycare(now) -> AlertDialog.Builder(this)
                .setTitle("Daycare")
                .setMessage(
                    "Daycare looks after your pet for up to 8 hours, so its needs barely change while " +
                        "you're busy. You can pick it up early. Daycare can be used once a day."
                )
                .setPositiveButton("Send to daycare") { _, _ -> PetStore.startDaycare(this); render() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        render()
    }

    private fun daycareLine(state: PetState, now: Long): String = when {
        state.stage(now) == Stage.EGG -> ""
        state.isAtDaycare(now) -> {
            val until = TIME.format(Instant.ofEpochMilli(state.daycareUntil).atZone(ZoneId.systemDefault()))
            "AT DAYCARE UNTIL $until  ·  TAP TO PICK UP"
        }
        state.canUseDaycare(now) -> "SEND TO DAYCARE"
        else -> "DAYCARE USED TODAY"
    }

    // ---- Permissions and tidying up -----------------------------------------------------

    /** Walks need the step counter; weather needs approximate location. Asked once, with a reason. */
    private fun askForPermissionsOnce() {
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("askedWalksWeather", false)) return
        if (Senses.canCountSteps(this) && Weather.canUse(this)) {
            prefs.edit().putBoolean("askedWalksWeather", true).apply()
            return
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST) return
        // Start counting steps and fetch the weather now that we're allowed to.
        sensors.stop()
        sensors.start()
        Weather.refreshIfStale(this) { render() }
    }

    // ---- Buttons and drawing ------------------------------------------------------------

    private fun onSlot(slot: Int) {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        if (state.stage(now) == Stage.EGG) {
            // Nothing to feed yet: the egg wobbles instead.
            pokedAt = now
            render()
            return
        }
        val action = state.actions()[slot]
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
        Nudges.keepScheduled(this)
        PetWidgetProvider.keepTicking(this)
        PetWidgetProvider.refreshAll(this)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        sensors.stop()
        super.onPause()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now, lookingNow = true)
        val weather = Weather.current(this, now)
        val poked = now - pokedAt < POKE_MS
        petView.setImageBitmap(
            DotPainter.paintPet(PetScene.render(state, now, animate = true, weather = weather, poke = poked), pitch = 24)
        )

        // The name (or "DOTCHI" until it has one), written in dots. Only redrawn when it changes.
        val title = state.name.ifEmpty { "DOTCHI" }
        if (title != shownTitle) {
            titleView.setImageBitmap(dotTitle(title))
            shownTitle = title
        }

        state.actions().forEachIndexed { slot, action ->
            val view = buttons[slot]
            view.setImageBitmap(PetWidgetProvider.buttonFor(action, state))
            view.contentDescription = PetWidgetProvider.label(action)
        }

        statusView.text = if (poked) "STILL AN EGG. NOT LONG NOW!" else statusLine(state, now) + extrasLine(state, now, weather)
        daycareView.text = daycareLine(state, now)

        maybeAskForName(state, now)
    }

    /** Extra lines: today's steps and the weather, and any special day. */
    private fun extrasLine(state: PetState, now: Long, weather: WeatherNow?): String {
        val parts = mutableListOf<String>()
        if (Senses.canCountSteps(this)) parts += "%,d STEPS".format(state.stepsOn(now))
        if (weather != null) parts += "${weather.label} ${weather.tempC}°C"
        var text = if (parts.isEmpty()) "" else "\n" + parts.joinToString("  ·  ")
        Season.on(now)?.let { text += "\n" + it.greeting }
        return text
    }

    private fun statusLine(state: PetState, now: Long): String {
        if (state.isFarewell(now)) {
            val who = state.farewellName.ifEmpty { state.farewellForm?.label ?: "YOUR PET" }
            return "$who IS OFF ON AN ADVENTURE!"
        }
        if (state.isGrowing(now)) {
            return if (state.stage(now) == Stage.BABY) "IT'S HATCHING!" else "GROWING UP... IT'S A ${state.name(now)}!"
        }
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
        private const val MAX_NAME = 8
        private const val POKE_MS = 1_500L
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** A heading written in the dot font. */
        fun dotTitle(text: String) = DotGrid(DotFont.width(text), DotFont.HEIGHT).let { g ->
            g.text(text, 0, 0)
            DotPainter.paint(g, pitch = 10, ghosts = false)
        }
    }
}
