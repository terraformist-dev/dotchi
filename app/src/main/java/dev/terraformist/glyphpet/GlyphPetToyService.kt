package dev.terraformist.glyphpet

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
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
 *  - Then just wait: after a moment the picked action happens.
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

    private var selected: Action? = null
    private var selectedAt = 0L

    /** Animate until this time even if the screen is off. */
    private var animateUntil = 0L

    /** True when the last thing that woke us was the once-a-minute always-on tick. */
    private var alwaysOn = false

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
            if (screenOn || now < animateUntil || selected != null) {
                draw(now, animate = true, dim = alwaysOn)
                main.postDelayed(this, FRAME_MS)
            } else {
                // Rest: one still frame, then let the phone sleep until something wakes us.
                draw(now, animate = false, dim = alwaysOn)
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

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                alwaysOn = false
                restartLoop()
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
                animateFor(ACTIVE_MS, alwaysOnTick = false)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                main.removeCallbacks(tick)
                releaseWakeLock()
            }
        })
        if (!receiverRegistered) {
            registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(tick)
        releaseWakeLock()
        if (receiverRegistered) {
            unregisterReceiver(screenOnReceiver)
            receiverRegistered = false
        }
        connected = false
        selected = null
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
        val order = listOf(Action.FEED, Action.PLAY, Action.CLEAN, null)
        selected = order[(order.indexOf(selected) + 1) % order.size]
        selectedAt = System.currentTimeMillis()
        animateFor(ACTIVE_MS, alwaysOnTick = false)
    }

    private fun confirmSelectionIfDue(now: Long) {
        val action = selected ?: return
        if (now - selectedAt >= CONFIRM_AFTER_MS) {
            selected = null
            PetStore.act(applicationContext, action, now)
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
        val state = PetStore.get(applicationContext, now)
        val progress = if (selected != null) (now - selectedAt).toFloat() / CONFIRM_AFTER_MS else 0f
        val grid = PetScene.render(state, now, animate, selected, progress)
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

    private companion object {
        const val TAG = "GlyphPet"
        const val FRAME_MS = 100L
        const val CONFIRM_AFTER_MS = 2_400L
        const val ACTIVE_MS = 60_000L
        const val AOD_BURST_MS = 4_000L
    }
}
