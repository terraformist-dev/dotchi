package dev.terraformist.glyphpet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/** The home-screen widget: the pet on a dot grid, with Feed / Play / Clean buttons underneath. */
class PetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Every half hour: take a quick look at the light and charger first, so naps start and end.
        val pending = goAsync()
        Senses.sample(context) {
            Weather.refreshIfStale(context) {
                draw(context, manager, ids)
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Nudges.ACTION) {
            // Half-hourly: refresh the widget, and maybe blink a nudge on the Glyph Matrix.
            val pending = goAsync()
            refreshAll(context)
            Nudges.check(context) { pending.finish() }
            return
        }
        if (intent.action == ACTION_PET) {
            val action = intent.getStringExtra(EXTRA_ACTION)
                ?.let { name -> Action.entries.firstOrNull { it.name == name } }
            if (action != null) {
                PetStore.act(context, action)
                // The pet reacts for a few seconds, then goes back to normal.
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    Senses.sample(context) {
                        refreshAll(context)
                        pending.finish()
                    }
                }, PetState.REACT_MS + 300)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_PET = "dev.terraformist.glyphpet.PET_ACTION"
        private const val EXTRA_ACTION = "action"

        private val FRAME_IDS = intArrayOf(R.id.pet_0, R.id.pet_1, R.id.pet_2, R.id.pet_3, R.id.pet_4, R.id.pet_5)

        // Animation moments for the six frames: down, up, down, up, down, up-with-a-blink.
        private val FRAME_TIMES = longArrayOf(200L, 700L, 1200L, 1700L, 2200L, 6800L)

        /** Redraw every copy of the widget on the home screen. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
            if (ids.isNotEmpty()) draw(context, manager, ids)
        }

        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val state = PetStore.get(context, now)
            // Pre-draw a short loop; the home screen flips through it every 500ms.
            val weather = Weather.current(context, now)
            val frames = FRAME_TIMES.map { t ->
                DotPainter.paintPet(PetScene.render(state, now, animate = true, anim = t, weather = weather), pitch = 12)
            }
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_pet)
                FRAME_IDS.forEachIndexed { i, viewId ->
                    views.setImageViewBitmap(viewId, frames[i])
                    views.setOnClickPendingIntent(viewId, openApp(context))
                }

                // Middle button is Play, or Medicine while the pet is ill.
                val (left, middle, right) = state.actions()
                bindButton(context, views, R.id.btn_feed, left, state)
                bindButton(context, views, R.id.btn_play, middle, state)
                bindButton(context, views, R.id.btn_clean, right, state)

                manager.updateAppWidget(id, views)
            }
        }

        private fun bindButton(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            action: Action,
            state: PetState,
        ) {
            views.setImageViewBitmap(viewId, buttonFor(action, state))
            if (action == Action.PLAY) {
                // Play means a mini-game, which lives in the app.
                views.setOnClickPendingIntent(viewId, openGame(context))
                return
            }
            val intent = Intent(context, PetWidgetProvider::class.java)
                .setAction(ACTION_PET)
                .putExtra(EXTRA_ACTION, action.name)
            val pending = PendingIntent.getBroadcast(
                context,
                action.ordinal + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(viewId, pending)
        }

        fun label(action: Action): String = when (action) {
            Action.FEED -> "FEED"
            Action.PLAY -> "PLAY"
            Action.CLEAN -> "CLEAN"
            Action.MEDICINE -> "MEDS"
        }

        /** The button picture for an action, as it should look right now. */
        fun buttonFor(action: Action, state: PetState): Bitmap =
            if (action == Action.MEDICINE) {
                buttonBitmap(label(action), 5, highlight = true, alarm = true)
            } else {
                buttonBitmap(label(action), state.level(action), highlight = state.mostNeeded() == action)
            }

        /**
         * A dot-matrix label with a 5-dot meter under it.
         * [alarm] lights the whole meter red (used for Medicine).
         */
        fun buttonBitmap(label: String, level: Int, highlight: Boolean, alarm: Boolean = false): Bitmap {
            val g = DotGrid(21, 9)
            val textX = (g.width - DotFont.width(label)) / 2
            g.text(label, textX, 0, red = highlight)
            val meterX = (g.width - 9) / 2
            for (i in 0 until 5) {
                val lit = i < level
                val low = level <= 1 || alarm
                g.set(meterX + i * 2, 7, if (lit) 255 else 0, red = lit && low)
            }
            return DotPainter.paint(g, pitch = 8, ghosts = false).let { withMeterGhosts(it, g, meterX) }
        }

        // Unlit meter dots are drawn faintly so you can see the meter is out of 5.
        private fun withMeterGhosts(bmp: Bitmap, g: DotGrid, meterX: Int): Bitmap {
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFF333333.toInt()
            val pitch = bmp.width / g.width
            for (i in 0 until 5) {
                val x = meterX + i * 2
                if (g.level(x, 7) == 0) {
                    canvas.drawCircle(x * pitch + pitch / 2f, 7 * pitch + pitch / 2f, pitch * 0.40f, paint)
                }
            }
            return bmp
        }

        private fun openGame(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, GameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
