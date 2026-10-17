package dev.terraformist.glyphpet

import android.app.AlarmManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.SystemClock
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * The Glyph Toy: shows the pet on the 25x25 Glyph Matrix on the back of the phone.
 *
 * Controls (Glyph Button on the back):
 *  - Long-press: pick the next action (Feed -> Play -> Clean -> nothing).
 *    While the pet is ill, Medicine takes Play's place. Play here starts a tilt game
 *    (Catch or Roll, taking turns); a long-press during a game stops it.
 *  - Then just wait: after a moment the picked action happens.
 *
 * Motion (while the pet is awake on the matrix):
 *  - Shake the phone: wakes it if it's asleep, otherwise a quick play.
 *  - Turn the phone face-down: the pet waves hello (also when the toy is picked), if awake.
 *
 * Waking from a dark-room nap without the app: we watch for the screen turning on, the
 * charger, the phone being moved, and the light sensor (continuously if the phone has a
 * "wake-up" light sensor, otherwise a quick look every couple of minutes while it naps).
 *
 * Animation:
 *  - Screen on, or within a minute of touching the Glyph Button: animates continuously.
 *    (The phone is usually face-down with the screen off when you use a Glyph Toy, so we
 *    keep the processor awake for that minute, otherwise the animation would freeze.)
 *  - Always-on Glyph (screen off, toy left running): the system wakes us once a minute,
 *    and the pet does a few seconds of bouncing and blinking each time, then rests.
 *    Animating non-stop there would keep the phone awake all night.
 *
 * The system only keeps this running while the toy is selected, so it stores
 * nothing itself; the pet lives in [PetStore], shared with the widget.
 */
class GlyphPetToyService : Service() {

    private var matrix: GlyphMatrixManager? = null
    private var connected = false

    /** A tilt game in progress (started by picking Play with the back button). */
    private var game: GlyphGame? = null
    private var nextGameIsCatch = true

    /** When the egg was last poked (long-press while it's still an egg). */
    private var pokedAt = 0L

    private var selected: Action? = null
    private var selectedAt = 0L

    /** Animate until this time even if the screen is off. */
    private var animateUntil = 0L

    /** True when the last thing that woke us was the once-a-minute always-on tick. */
    private var alwaysOn = false

    /** When the current hello wave started (0 = none). */
    private var helloAt = 0L

    private val sensors by lazy {
        PetSensors(applicationContext, onShake = { onShake() }, onFlippedFaceDown = { sayHello() })
    }

    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val alarms by lazy { getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    // A light sensor that can wake the phone when the light changes (not every phone has one).
    private val wakeUpLight: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT, true) }
    private val lightWatcher = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Senses.noteLux(event.values[0])
            if (event.values[0] > Senses.BRIGHT_LUX && !Senses.faceDown) checkForWaking()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // "The phone was picked up or carried": a low-power one-shot sensor.
    private val motionTrigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            motionArmed = false
            animateFor(ACTIVE_MS, alwaysOnTick = false)
        }
    }
    private var motionArmed = false

    // While napping in the dark with no wake-up light sensor: peek at the light every few minutes.
    private val lightCheckAlarm = AlarmManager.OnAlarmListener { lightCheck() }
    private var lightCheckScheduled = false

    private val main = Handler(Looper.getMainLooper())

    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val wakeLock by lazy {
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlyphPet:animation").apply {
            setReferenceCounted(false)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            confirmSelectionIfDue(now)
            val screenOn = power.isInteractive
            if (screenOn) alwaysOn = false
            val g = game
            if (g != null) {
                runGame(g, now)
                main.postDelayed(this, GAME_FRAME_MS)
            } else if (screenOn || now < animateUntil || selected != null) {
                draw(now, animate = true, dim = alwaysOn)
                main.postDelayed(this, FRAME_MS)
            } else {
                // Rest: one still frame, then let the phone sleep until something wakes us.
                draw(now, animate = false, dim = alwaysOn)
                sensors.stop()
                armWakeTriggers()
                releaseWakeLock()
            }
        }
    }

    private val buttonEvents = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg)
                return
            }
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> onLongPress()
                GlyphToy.EVENT_AOD -> onAlwaysOnTick()
                GlyphToy.EVENT_ACTION_DOWN, GlyphToy.EVENT_ACTION_UP -> animateFor(ACTIVE_MS, alwaysOnTick = false)
            }
        }
    }

    private val messenger = Messenger(buttonEvents)

    private val systemEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    alwaysOn = false
                    animateFor(ACTIVE_MS, alwaysOnTick = false)
                }
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    // Start or end a charging nap straight away.
                    PetStore.get(applicationContext, checkNapNow = true)
                    animateFor(CHARGER_SHOW_MS, alwaysOnTick = false)
                }
            }
        }
    }
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder {
        val manager = GlyphMatrixManager.getInstance(applicationContext)
        matrix = manager
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                manager.register(Glyph.DEVICE_23112)
                connected = true
                active = true
                Weather.refreshIfStale(applicationContext)
                sayHello()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                main.removeCallbacks(tick)
                releaseWakeLock()
            }
        })
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(systemEvents, filter, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        wakeUpLight?.let { sensorManager.registerListener(lightWatcher, it, SensorManager.SENSOR_DELAY_NORMAL) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(tick)
        sensors.stop()
        disarmWakeTriggers()
        sensorManager.unregisterListener(lightWatcher)
        releaseWakeLock()
        if (receiverRegistered) {
            unregisterReceiver(systemEvents)
            receiverRegistered = false
        }
        connected = false
        active = false
        selected = null
        game = null
        matrix?.let {
            try {
                it.turnOff()
            } catch (e: Exception) {
                Log.w(TAG, "turnOff failed", e)
            }
            it.unInit()
        }
        matrix = null
        return false
    }

    private fun onLongPress() {
        val now = System.currentTimeMillis()
        // A long-press during a game stops it.
        if (game != null) {
            game = null
            animateFor(ACTIVE_MS, alwaysOnTick = false)
            return
        }
        // Still an egg: nothing to choose yet, so it just wobbles.
        if (PetStore.get(applicationContext, now).stage(now) == Stage.EGG) {
            pokedAt = now
            animateFor(ACTIVE_MS, alwaysOnTick = false)
            return
        }
        // Feed -> Play (or Medicine when ill) -> Clean -> nothing.
        val order: List<Action?> = PetStore.get(applicationContext).actions() + listOf(null)
        selected = order[(order.indexOf(selected) + 1) % order.size]
        selectedAt = System.currentTimeMillis()
        animateFor(ACTIVE_MS, alwaysOnTick = false)
    }

    /** Shaking wakes a sleeping pet (with its wake-up animation), otherwise it's a quick play. */
    private fun onShake() {
        if (game != null) return // shaking is part of tilting during a game
        PetStore.shake(applicationContext)
        animateFor(ACTIVE_MS, alwaysOnTick = false)
    }

    /** If it was napping in the dark and the light is now on, wake it and show it. */
    private fun checkForWaking() {
        val before = PetStore.get(applicationContext)
        if (before.nap != Nap.DARK) return
        val after = PetStore.get(applicationContext, checkNapNow = true)
        if (after.nap != Nap.DARK) animateFor(ACTIVE_MS, alwaysOnTick = false)
    }

    /** A quick look at the light while napping (no wake-up light sensor on this phone). */
    private fun lightCheck() {
        lightCheckScheduled = false
        if (!connected) return
        if (System.currentTimeMillis() < animateUntil) return // already awake and sensing
        try {
            wakeLock.acquire(LIGHT_LOOK_MS + 1_000L)
        } catch (e: Exception) {
            Log.w(TAG, "wake lock failed", e)
        }
        sensors.start()
        main.postDelayed({
            if (System.currentTimeMillis() >= animateUntil) {
                checkForWaking()
                if (System.currentTimeMillis() >= animateUntil) {
                    sensors.stop()
                    armWakeTriggers()
                    releaseWakeLock()
                }
            }
        }, LIGHT_LOOK_MS)
    }

    /** While resting: let movement, and (if napping in the dark) the light, bring us back. */
    private fun armWakeTriggers() {
        if (!connected) return
        if (!motionArmed) {
            sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let {
                motionArmed = sensorManager.requestTriggerSensor(motionTrigger, it)
            }
        }
        val napping = PetStore.get(applicationContext).nap == Nap.DARK
        if (napping && wakeUpLight == null && !lightCheckScheduled) {
            alarms.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + LIGHT_CHECK_EVERY_MS,
                "GlyphPet:light",
                lightCheckAlarm,
                main,
            )
            lightCheckScheduled = true
        }
    }

    private fun disarmWakeTriggers() {
        if (motionArmed) {
            sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let {
                sensorManager.cancelTriggerSensor(motionTrigger, it)
            }
            motionArmed = false
        }
        if (lightCheckScheduled) {
            alarms.cancel(lightCheckAlarm)
            lightCheckScheduled = false
        }
    }

    /** The pet waves hello, if it's awake. */
    private fun sayHello() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(applicationContext, now)
        animateFor(ACTIVE_MS, alwaysOnTick = false)
        // Only an awake pet waves: not an egg, not asleep, not napping (in the dark or on the charger).
        if (state.stage(now) == Stage.EGG || state.isAsleep(now)) return
        helloAt = now
    }

    private fun confirmSelectionIfDue(now: Long) {
        val action = selected ?: return
        if (now - selectedAt >= CONFIRM_AFTER_MS) {
            selected = null
            val state = PetStore.get(applicationContext, now)
            if (action == Action.PLAY && !state.sick && state.stage(now) != Stage.EGG) {
                // Play on the Glyph Matrix: a tilt game, taking turns between Catch and Roll.
                game = if (nextGameIsCatch) CatchGame(now) else RollGame(now)
                nextGameIsCatch = !nextGameIsCatch
                animateFor(GAME_HOLD_MS, alwaysOnTick = false)
            } else {
                PetStore.act(applicationContext, action, now)
            }
        }
    }

    /** One frame of a tilt game, then its result, then back to normal. */
    private fun runGame(g: GlyphGame, now: Long) {
        val manager = matrix ?: return
        if (!connected) return
        val state = PetStore.get(applicationContext, now)
        val grid = DotGrid(25, 25)
        when {
            g.playing(now) -> {
                g.step(now, sensors.ax, sensors.ay, state)
                g.draw(grid, state, now)
            }
            !g.finished(now) -> g.drawResult(grid, state, now)
            else -> {
                game = null
                PetStore.played(applicationContext, g.won, now)
                animateFor(ACTIVE_MS, alwaysOnTick = false)
                return
            }
        }
        for (y in 0 until 25) for (x in 0 until 25) if (!GlyphShape.contains(x, y)) grid.set(x, y, 0)
        try {
            manager.setMatrixFrame(IntArray(grid.levels.size) { grid.levels[it] * 4095 / 255 })
        } catch (e: Exception) {
            Log.w(TAG, "setMatrixFrame failed", e)
        }
    }

    private fun onAlwaysOnTick() {
        // Only take over if nothing livelier is already happening.
        val now = System.currentTimeMillis()
        if (power.isInteractive || now < animateUntil) return
        animateFor(AOD_BURST_MS, alwaysOnTick = true)
    }

    /** Animate for [ms], keeping the processor awake just long enough to do it. */
    private fun animateFor(ms: Long, alwaysOnTick: Boolean) {
        val now = System.currentTimeMillis()
        alwaysOn = alwaysOnTick
        animateUntil = maxOf(animateUntil, now + ms)
        try {
            wakeLock.acquire(animateUntil - now + 1_000L)
        } catch (e: Exception) {
            Log.w(TAG, "wake lock failed", e)
        }
        sensors.start()
        restartLoop()
    }

    private fun restartLoop() {
        main.removeCallbacks(tick)
        main.post(tick)
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) wakeLock.release()
        } catch (e: Exception) {
            Log.w(TAG, "wake lock release failed", e)
        }
    }

    private fun draw(now: Long, animate: Boolean, dim: Boolean) {
        val manager = matrix ?: return
        if (!connected) return
        val state = PetStore.get(applicationContext, now, lookingNow = animate)
        val progress = if (selected != null) (now - selectedAt).toFloat() / CONFIRM_AFTER_MS else 0f
        val sinceHello = now - helloAt
        val hello = if (helloAt > 0L && sinceHello < HELLO_MS) sinceHello.toFloat() / HELLO_MS else -1f
        val grid = PetScene.render(
            state, now, animate, selected, progress,
            hello = hello,
            weather = Weather.current(applicationContext, now),
            poke = now - pokedAt < POKE_MS,
        )
        val frame = IntArray(grid.levels.size) { i ->
            val level = if (dim) grid.levels[i] * 6 / 10 else grid.levels[i]
            // The matrix takes 0..4095 per LED; our grid is 0..255.
            level * 4095 / 255
        }
        try {
            manager.setMatrixFrame(frame)
        } catch (e: Exception) {
            Log.w(TAG, "setMatrixFrame failed", e)
        }
    }

    companion object {
        /** True while Dotchi is the selected Glyph Toy (nudges stay quiet then). */
        @Volatile var active = false
            private set

        private const val TAG = "GlyphPet"
        private const val FRAME_MS = 100L
        private const val GAME_FRAME_MS = 50L
        private const val GAME_HOLD_MS = GlyphGame.DURATION_MS + GlyphGame.SCORE_SHOW_MS + 2_000L
        private const val POKE_MS = 1_500L
        private const val CONFIRM_AFTER_MS = 2_400L
        private const val ACTIVE_MS = 60_000L
        private const val AOD_BURST_MS = 4_000L
        private const val HELLO_MS = 2_500L
        private const val CHARGER_SHOW_MS = 6_000L
        private const val LIGHT_CHECK_EVERY_MS = 2 * 60_000L
        private const val LIGHT_LOOK_MS = 1_500L
    }
}
