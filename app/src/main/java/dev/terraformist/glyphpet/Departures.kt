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

/**
 * A heads-up when your Dotchi sets off on its adventure (or leaves early), so you can open the
 * app and see its goodbye. One notification per departure, and none if the app is already open.
 *
 * The pet only updates when something looks at it, so an alarm is set for the moment it's due to
 * leave. While it's frail (and might leave early) the alarm checks every hour instead.
 */
object Departures {

    const val ACTION = "dev.terraformist.glyphpet.DEPARTURE_CHECK"
    private const val CHANNEL = "departures"
    private const val NOTIFICATION_ID = 7
    private const val FRAIL_CHECK_MS = 60 * 60 * 1000L

    /** True while the main screen is showing: no need to notify, they'll see the goodbye. */
    @Volatile var appInFront = false

    fun canNotify(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Called once, at the moment a pet is found to have left. */
    fun notifyLeft(context: Context, s: PetState) {
        if (appInFront || !canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Setting off", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When your Dotchi sets off on its adventure"
            }
        )
        val who = s.farewellName.ifEmpty { s.farewellForm?.label ?: "Your Dotchi" }
        val text = if (s.farewellEarly) {
            "$who has packed its little bag and left early. Open Dotchi to see it off."
        } else {
            "$who is setting off on its adventure. Open Dotchi to say goodbye."
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val note = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(if (s.farewellEarly) "$who has left" else "$who is setting off")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, note)
        } catch (e: SecurityException) {
            // Notifications were switched off: nothing to do.
        }
    }

    /** Clear the notification (the app is open and showing the goodbye). */
    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Set (or move) the alarm that checks on it at the right time. */
    fun schedule(context: Context, state: PetState, now: Long = System.currentTimeMillis()) {
        val due = state.departsAt()
        var at = if (state.frailStage() >= 1) minOf(due, now + FRAIL_CHECK_MS) else due
        if (at <= now) at = now + 60_000L
        val pi = PendingIntent.getBroadcast(
            context, 300,
            Intent(context, PetWidgetProvider::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
