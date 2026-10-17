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
        draw(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PET) {
            val action = intent.getStringExtra(EXTRA_ACTION)
                ?.let { name -> Action.entries.firstOrNull { it.name == name } }
            if (action != null) {
                PetStore.act(context, action)
                // The pet reacts for a few seconds, then goes back to normal.
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    refreshAll(context)
                    pending.finish()
                }, PetState.REACT_MS + 300)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_PET = "dev.terraformist.glyphpet.PET_ACTION"
        private const val EXTRA_ACTION = "action"

        /** Redraw every copy of the widget on the home screen. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
            if (ids.isNotEmpty()) draw(context, manager, ids)
        }

        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val state = PetStore.get(context, now)
            val petBitmap = DotPainter.paintPet(PetScene.render(state, now, animate = false), pitch = 14)
            val need = state.mostNeeded()

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_pet)
                views.setImageViewBitmap(R.id.pet, petBitmap)
                views.setOnClickPendingIntent(R.id.pet, openApp(context))

                bindButton(context, views, R.id.btn_feed, Action.FEED, "FEED", state, need)
                bindButton(context, views, R.id.btn_play, Action.PLAY, "PLAY", state, need)
                bindButton(context, views, R.id.btn_clean, Action.CLEAN, "CLEAN", state, need)

                manager.updateAppWidget(id, views)
            }
        }

        private fun bindButton(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            action: Action,
            label: String,
            state: PetState,
            need: Action?,
        ) {
            views.setImageViewBitmap(viewId, buttonBitmap(label, state.level(action), highlight = need == action))
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

        /** A dot-matrix label with a 5-dot meter under it. */
        fun buttonBitmap(label: String, level: Int, highlight: Boolean): Bitmap {
            val g = DotGrid(21, 9)
            val textX = (g.width - DotFont.width(label)) / 2
            g.text(label, textX, 0, red = highlight)
            val meterX = (g.width - 9) / 2
            for (i in 0 until 5) {
                val lit = i < level
                val low = level <= 1
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

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
