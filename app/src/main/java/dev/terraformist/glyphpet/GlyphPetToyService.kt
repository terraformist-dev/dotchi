package dev.terraformist.glyphpet

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
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
 * The system only keeps this running while the toy is selected, so it stores
 * nothing itself; the pet lives in [PetStore], shared with the widget.
 */
class GlyphPetToyService : Service() {

    private var matrix: GlyphMatrixManager? = null
    private var connected = false

    private var selected: Action? = null
    private var selectedAt = 0L

    // While the always-on display is showing the toy, we only draw once a minute.
    private var quietUntil = 0L

    private val main = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            confirmSelectionIfDue(now)
            if (now >= quietUntil) {
                draw(now, animate = true)
                main.postDelayed(this, FRAME_MS)
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
                GlyphToy.EVENT_ACTION_DOWN, GlyphToy.EVENT_ACTION_UP -> wake()
            }
        }
    }

    private val messenger = Messenger(buttonEvents)

    override fun onBind(intent: Intent?): IBinder {
        val manager = GlyphMatrixManager.getInstance(applicationContext)
        matrix = manager
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                manager.register(Glyph.DEVICE_23112)
                connected = true
                quietUntil = 0L
                main.removeCallbacks(tick)
                main.post(tick)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                main.removeCallbacks(tick)
            }
        })
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(tick)
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
        val now = System.currentTimeMillis()
        val order = listOf(Action.FEED, Action.PLAY, Action.CLEAN, null)
        selected = order[(order.indexOf(selected) + 1) % order.size]
        selectedAt = now
        wake()
    }

    private fun confirmSelectionIfDue(now: Long) {
        val action = selected ?: return
        if (now - selectedAt >= CONFIRM_AFTER_MS) {
            selected = null
            PetStore.act(applicationContext, action, now)
        }
    }

    private fun onAlwaysOnTick() {
        val now = System.currentTimeMillis()
        quietUntil = now + 65_000L
        main.removeCallbacks(tick)
        draw(now, animate = false)
    }

    /** Back to full animation (e.g. the button was touched). */
    private fun wake() {
        quietUntil = 0L
        main.removeCallbacks(tick)
        main.post(tick)
    }

    private fun draw(now: Long, animate: Boolean) {
        val manager = matrix ?: return
        if (!connected) return
        val state = PetStore.get(applicationContext, now)
        val progress = if (selected != null) (now - selectedAt).toFloat() / CONFIRM_AFTER_MS else 0f
        val grid = PetScene.render(state, now, animate, selected, progress)
        val dim = !animate
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
    }
}
