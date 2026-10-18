package dev.terraformist.glyphpet

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Glyph nudges (off unless switched on in the Guide): about every half hour, if the pet needs
 * something and the phone is lying face-down, the need's icon pulses on the Glyph Matrix (like a
 * heartbeat) for a few seconds. No sound, no notification. Not at night, at daycare, or more than once an hour.
 */
object Nudges {

    const val ACTION = "dev.terraformist.glyphpet.NUDGE"
    private const val PREFS = "app"
    private const val KEY = "glyphNudges"
    private const val MIN_GAP_MS = 55 * 60_000L
    private const val TAG = "GlyphPet"
    private const val PULSE_FRAME_MS = 100L
    private const val PULSE_FRAMES = 54 // three heartbeats of 1.8 seconds

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        if (on) keepScheduled(context) else alarms(context).cancel(alarmIntent(context))
    }

    /** Make sure the half-hourly check is scheduled (safe to call often). */
    fun keepScheduled(context: Context) {
        if (!isOn(context)) return
        alarms(context).setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            alarmIntent(context),
        )
    }

    /** The half-hourly check. Calls [done] when finished (always). */
    fun check(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = PetStore.get(app, now)
        val need = state.mostNeeded()
        val skip = !isOn(app) || need == null || GlyphPetToyService.active ||
            state.stage(now) == Stage.EGG || state.isAsleep(now) || state.isAtDaycare(now) ||
            now - prefs.getLong("lastNudge", 0L) < MIN_GAP_MS
        if (skip || need == null) {
            done()
            return
        }
        Senses.sample(app) {
            if (!Senses.faceDown) {
                done()
            } else {
                prefs.edit().putLong("lastNudge", now).apply()
                blink(app, need, done)
            }
        }
    }

    /** Pulse the need's icon on the Glyph Matrix for three heartbeats, then hand it back. */
    private fun blink(context: Context, need: Action, done: () -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val manager = GlyphMatrixManager.getInstance(context)
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            try {
                manager.closeAppMatrix()
            } catch (e: Exception) {
                Log.w(TAG, "closeAppMatrix failed", e)
            }
            manager.unInit()
            done()
        }
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                manager.register(Glyph.DEVICE_23112)
                for (i in 0 until PULSE_FRAMES) {
                    main.postDelayed({
                        if (finished) return@postDelayed
                        val grid = PetScene.nudgeFrame(need, i * PULSE_FRAME_MS)
                        try {
                            manager.setAppMatrixFrame(IntArray(grid.levels.size) { grid.levels[it] * 4095 / 255 })
                        } catch (e: Exception) {
                            Log.w(TAG, "setAppMatrixFrame failed", e)
                        }
                    }, i * PULSE_FRAME_MS)
                }
                main.postDelayed({ finish() }, PULSE_FRAMES * PULSE_FRAME_MS)
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        })
        // Never hold things up for long, whatever happens.
        main.postDelayed({ finish() }, 7_000L)
    }

    private fun alarms(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        200,
        Intent(context, PetWidgetProvider::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
