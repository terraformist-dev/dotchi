package dev.terraformist.glyphpet

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews

/**
 * The optional lock-screen pet: a quiet, always-there notification showing the pet, its
 * meters, and Feed / Play (or Meds) / Clean buttons that work without unlocking.
 *
 * Off by default; switched on from the pet screen. Kept up to date by
 * [PetWidgetProvider.refreshAll], which runs after every change to the pet.
 */
object LockScreenPet {

    private const val CHANNEL = "lock_screen_pet"
    private const val NOTIFICATION_ID = 1
    private const val PREFS = "app"
    private const val KEY = "lockScreenPet"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun canNotify(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        if (on) {
            keepFresh(context)
            update(context)
        } else {
            manager(context).cancel(NOTIFICATION_ID)
            alarms(context).cancel(refreshIntent(context))
        }
    }

    /**
     * Refresh the card about every half hour even if nothing else does (the home-screen widget
     * normally does this, but you might not have one). Safe to call repeatedly.
     */
    fun keepFresh(context: Context) {
        if (!isOn(context)) return
        alarms(context).setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            refreshIntent(context),
        )
    }

    private fun alarms(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        100,
        Intent(context, PetWidgetProvider::class.java).setAction(PetWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Redraw the lock-screen card (or remove it if switched off). */
    fun update(context: Context) {
        val app = context.applicationContext
        if (!isOn(app) || !canNotify(app)) return
        val notifications = manager(app)
        ensureChannel(notifications)

        val now = System.currentTimeMillis()
        val state = PetStore.get(app, now)
        val picture = petOnBlack(
            PetScene.render(state, now, animate = false, weather = Weather.current(app, now)),
        )
        val title = Status.title(state, now)
        val meters = Status.meters(state)

        val small = RemoteViews(app.packageName, R.layout.notification_pet).apply {
            setImageViewBitmap(R.id.pet, picture)
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.meters, meters.replace("\n", "   "))
        }
        val big = RemoteViews(app.packageName, R.layout.notification_pet_big).apply {
            setImageViewBitmap(R.id.pet, picture)
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.meters, meters)
        }

        val builder = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(
                PendingIntent.getActivity(
                    app, 0,
                    Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )

        // Buttons: same three as everywhere else. Play here is a quick play (games need the app).
        if (state.stage(now) != Stage.EGG) {
            for (action in state.actions()) {
                builder.addAction(
                    Notification.Action.Builder(null, PetWidgetProvider.label(action), PetWidgetProvider.actionIntent(app, action))
                        .build()
                )
            }
        }

        notifications.notify(NOTIFICATION_ID, builder.build())
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "Pet on lock screen", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows your Glyph Pet on the lock screen, with Feed, Play and Clean buttons."
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /** The pet on a black disc, so it looks like the Glyph Matrix on any notification colour. */
    private fun petOnBlack(grid: DotGrid): Bitmap {
        val dots = DotPainter.paintPet(grid, pitch = 10)
        val out = Bitmap.createBitmap(dots.width, dots.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
        canvas.drawCircle(out.width / 2f, out.height / 2f, out.width / 2f, paint)
        canvas.drawBitmap(dots, 0f, 0f, null)
        return out
    }
}

/** Short words describing the pet, shared by the pet screen and the lock-screen card. */
object Status {

    fun moodWord(state: PetState, now: Long): String = when (state.mood(now)) {
        Mood.EGG -> "EGG"
        Mood.SICK -> "ILL"
        Mood.SLEEPING -> when (state.nap) {
            Nap.CHARGING -> "RECHARGING"
            Nap.DARK -> if (PetState.isNight(now)) "ASLEEP" else "NAPPING"
            null -> "ASLEEP"
        }
        Mood.HAPPY -> "HAPPY"
        Mood.SAD -> "UNHAPPY"
        Mood.EATING -> "EATING"
        Mood.PLAYING -> "PLAYING"
        Mood.CLEANING -> "FRESH"
        Mood.MEDICATED -> "YUCK!"
        Mood.WAKING -> "WAKING UP"
        Mood.WALKING -> "WALKING"
        Mood.OK -> "CONTENT"
    }

    fun title(state: PetState, now: Long): String =
        if (state.stage(now) == Stage.EGG) "EGG  ·  HATCHING SOON"
        else "${state.name(now)}  ·  ${moodWord(state, now)}"

    /** "FEED ●●●○○" style meters, one per line. */
    fun meters(state: PetState): String {
        fun dots(level: Int) = "●".repeat(level) + "○".repeat(5 - level)
        val lines = mutableListOf(
            "FEED  ${dots(state.level(Action.FEED))}",
            "PLAY  ${dots(state.level(Action.PLAY))}",
            "CLEAN ${dots(state.level(Action.CLEAN))}",
        )
        if (state.sick) lines += "ILL: NEEDS MEDS"
        return lines.joinToString("\n")
    }
}
