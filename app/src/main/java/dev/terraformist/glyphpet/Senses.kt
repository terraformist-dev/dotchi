package dev.terraformist.glyphpet

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * What the phone can tell the pet about the world: is it charging, is the room dark,
 * which way up is the phone, is it being shaken.
 *
 * The light sensor is on the front of the phone, so it reads "dark" whenever the phone is
 * face-down on a table. Light readings taken while face-down are ignored.
 */
object Senses {

    /** Below this many lux the pet naps; above [BRIGHT_LUX] it wakes. In between, nothing changes. */
    const val DARK_LUX = 5f
    const val BRIGHT_LUX = 25f

    /** How long a light reading stays trustworthy. */
    private const val LIGHT_FRESH_MS = 2 * 60_000L

    /** Accelerometer: below this on the z axis (m/s²) the screen is facing the table. */
    const val FACE_DOWN_Z = -7.5f

    @Volatile private var lux = -1f
    @Volatile private var luxAt = 0L
    @Volatile var faceDown = false
        private set

    fun noteLux(value: Float, now: Long = System.currentTimeMillis()) {
        if (faceDown) return
        lux = value
        luxAt = now
    }

    fun noteFaceDown(value: Boolean) {
        faceDown = value
    }

    fun isCharging(context: Context): Boolean {
        val battery: Intent? = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /** Should the pet be napping right now, and why? Keeps [current] if the light is in-between or unknown. */
    fun napReason(context: Context, current: Nap?, now: Long): Nap? {
        if (isCharging(context)) return Nap.CHARGING
        val fresh = lux >= 0f && now - luxAt < LIGHT_FRESH_MS
        if (fresh && lux < DARK_LUX) return Nap.DARK
        if (fresh && lux > BRIGHT_LUX) return null
        // Just unplugged: stop the charging nap.
        return if (current == Nap.CHARGING) null else current
    }

    /**
     * Take one quick reading of light and orientation (used by the widget, which has no
     * screen of its own open). Calls [done] on the main thread within [timeoutMs].
     */
    fun sample(context: Context, timeoutMs: Long = 1_500L, done: () -> Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val light = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || (light == null && accel == null)) {
            done()
            return
        }
        val main = Handler(Looper.getMainLooper())
        var gotLight = light == null
        var gotAccel = accel == null
        var pendingLux = -1f
        var finished = false

        lateinit var listener: SensorEventListener
        val finish = {
            if (!finished) {
                finished = true
                manager.unregisterListener(listener)
                if (pendingLux >= 0f) noteLux(pendingLux)
                done()
            }
        }
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> { pendingLux = event.values[0]; gotLight = true }
                    Sensor.TYPE_ACCELEROMETER -> { noteFaceDown(event.values[2] < FACE_DOWN_Z); gotAccel = true }
                }
                if (gotLight && gotAccel) main.post { finish() }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accel?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        light?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        main.postDelayed({ finish() }, timeoutMs)
    }
}

/**
 * Listens to the accelerometer and light sensor while a screen (the app or the Glyph Toy)
 * is showing the pet. Reports shakes and the phone being turned face-down.
 */
class PetSensors(
    context: Context,
    private val onShake: () -> Unit,
    private val onFlippedFaceDown: () -> Unit = {},
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var running = false
    private var knowsOrientation = false
    private var wasFaceDown = false

    // Shake detection: several strong jolts close together.
    private val jolts = ArrayDeque<Long>()
    private var lastShakeAt = 0L

    fun start() {
        if (running || manager == null) return
        running = true
        knowsOrientation = false
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        manager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        manager?.unregisterListener(this)
        jolts.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> Senses.noteLux(event.values[0], now)
            Sensor.TYPE_ACCELEROMETER -> {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])

                val faceDown = z < Senses.FACE_DOWN_Z
                // Use a looser threshold to count as "face-up again", so wobbles don't re-trigger.
                val faceUpAgain = z > -3f
                Senses.noteFaceDown(faceDown)
                if (knowsOrientation && faceDown && !wasFaceDown) onFlippedFaceDown()
                if (faceDown) wasFaceDown = true else if (faceUpAgain) wasFaceDown = false
                knowsOrientation = true

                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (g > SHAKE_G) {
                    if (jolts.isEmpty() || now - jolts.last() > JOLT_GAP_MS) jolts.addLast(now)
                    while (jolts.isNotEmpty() && now - jolts.first() > SHAKE_WINDOW_MS) jolts.removeFirst()
                    if (jolts.size >= JOLTS_FOR_SHAKE && now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                        lastShakeAt = now
                        jolts.clear()
                        onShake()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val SHAKE_G = 2.2f
        const val JOLT_GAP_MS = 120L
        const val SHAKE_WINDOW_MS = 1_000L
        const val JOLTS_FOR_SHAKE = 3
        const val SHAKE_COOLDOWN_MS = 4_000L
    }
}
