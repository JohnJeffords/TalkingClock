package io.github.johnjeffords.talkingclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.domain.alarm.nextTriggerTime
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Bridges [Alarm]s to Android's AlarmManager. `setAlarmClock` is used
 * deliberately: it is the API for USER-VISIBLE alarm clocks — exempt from
 * Doze deferral, surfaces the alarm icon in the status bar, and is exactly
 * what the exact-alarm permissions we declare are FOR (docs/DECISIONS.md
 * D-020).
 *
 * One PendingIntent per alarm id; rescheduling an alarm replaces its
 * previous PendingIntent (FLAG_UPDATE_CURRENT + stable request code from
 * the id), so there's never a stale duplicate.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** (Re)schedule every enabled alarm; cancel everything disabled. Called
     *  after any alarm edit and on boot. */
    fun rescheduleAll(alarms: List<Alarm>, now: LocalDateTime = LocalDateTime.now()) {
        alarms.forEach { alarm ->
            if (alarm.enabled) schedule(alarm, now) else cancel(alarm.id)
        }
    }

    fun schedule(alarm: Alarm, now: LocalDateTime = LocalDateTime.now()) {
        val triggerAt = nextTriggerTime(alarm, now)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // The info intent opens the app when the user taps the status-bar
        // alarm icon; the operation fires AlarmReceiver at the trigger time.
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            firePendingIntent(alarm.id),
        )
    }

    /**
     * Book [alarm] at an EXACT moment regardless of its own wall-clock time
     * — the snooze path ("ring again at now+9min"). Uses the same
     * PendingIntent slot as the regular schedule, so a snooze and a regular
     * occurrence can never both be pending for one alarm.
     */
    fun scheduleAt(alarm: Alarm, at: LocalDateTime) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            firePendingIntent(alarm.id),
        )
    }

    fun cancel(alarmId: String) {
        alarmManager.cancel(firePendingIntent(alarmId))
    }

    private fun firePendingIntent(alarmId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(), // stable per alarm → replaces, never duplicates
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
