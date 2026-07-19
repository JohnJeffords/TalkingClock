package io.github.johnjeffords.talkingclock.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.TalkingClockApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when AlarmManager says an alarm is due. Looks the alarm up, starts
 * the ringing effects (AlarmRinger), and posts a FULL-SCREEN notification —
 * the modern mechanism that pops the ringing screen over the lockscreen
 * (USE_FULL_SCREEN_INTENT; the ringing Activity itself sets showWhenLocked/
 * turnScreenOn). Repeating alarms reschedule their next occurrence here;
 * one-shots disable themselves at dismiss time.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val app = context.applicationContext as TalkingClockApp

        // A receiver must return fast; goAsync() keeps the process alive
        // for the short lookup + start (well under the ~10 s budget).
        val pending = goAsync()
        app.appScope.launch {
            try {
                val alarm = app.alarmRepository.alarms.first()
                    .find { it.id == alarmId && it.enabled }
                    ?: return@launch // deleted/disabled since scheduling: stay silent

                app.alarmRinger.startRinging(alarm)
                postFullScreenNotification(context, alarm.label)

                // A repeating alarm books its next occurrence immediately —
                // ringing and scheduling are independent.
                if (!alarm.isOneShot) app.alarmScheduler.schedule(alarm)
            } finally {
                pending.finish()
            }
        }
    }

    private fun postFullScreenNotification(context: Context, label: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_alarm),
                // HIGH importance is what makes the full-screen intent fire.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setSound(null, null) }, // AlarmRinger owns the audio
        )

        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, AlarmRingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alarm_ringing_title))
            .setContentText(label.ifBlank { context.getString(R.string.app_name) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_FIRE = "io.github.johnjeffords.talkingclock.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_ID = "alarms"
        const val NOTIFICATION_ID = 10

        /** The ringing screen clears this when the alarm is answered. */
        fun cancelNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        }
    }
}

/** Re-books enabled alarms after reboot or a system wall-clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val app = context.applicationContext as TalkingClockApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.alarmScheduler.rescheduleAll(app.alarmRepository.alarms.first())
            } finally {
                pending.finish()
            }
        }
    }
}
