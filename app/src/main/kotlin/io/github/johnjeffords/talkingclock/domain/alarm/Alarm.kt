package io.github.johnjeffords.talkingclock.domain.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One alarm (design frames 23/24). Pure data + pure scheduling math; the
 * Android side (AlarmManager, receivers, the ringing screen) consumes this.
 *
 * @property id stable unique id (a UUID string).
 * @property hour/minute wall-clock firing time.
 * @property days weekdays it repeats on; EMPTY = one-shot (fires once at
 *   the next occurrence of the time, then disables itself).
 * @property label optional user text ("Meds", "Bus").
 * @property announceTime speak the time aloud when ringing (the app's
 *   whole point — on by default).
 * @property vibrate buzz while ringing.
 * @property handoffInterval the signature feature (design frame 24's amber
 *   card): when the alarm is DISMISSED, arm the speaking clock at this
 *   interval — the "getting ready" hook. Null = no handoff.
 * @property handoffMinutes how long that speaking clock runs (auto-off).
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val announceTime: Boolean = true,
    val vibrate: Boolean = true,
    val handoffIntervalSeconds: Int? = null,
    val handoffMinutes: Int = 30,
    val enabled: Boolean = true,
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Alarm time out of range: $hour:$minute" }
    }

    val time: LocalTime get() = LocalTime.of(hour, minute)

    /** One-shot alarms fire once and then disable themselves. */
    val isOneShot: Boolean get() = days.isEmpty()
}

/**
 * When does [alarm] fire next, strictly after [now]?
 *
 * One-shot: today at the alarm time if that's still ahead, else tomorrow.
 * Repeating: the next enabled weekday at the alarm time — including later
 * TODAY if today is enabled and the time hasn't passed, wrapping up to a
 * full week ahead (an alarm set for "every Tuesday" checked on a Tuesday
 * one minute after it rang fires next Tuesday).
 *
 * Pure function; the week-wrap and today-edge cases are unit-tested.
 */
fun nextTriggerTime(alarm: Alarm, now: LocalDateTime): LocalDateTime {
    val todayAtTime = now.toLocalDate().atTime(alarm.time)

    if (alarm.isOneShot) {
        return if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
    }

    // Walk 0..7 days ahead; day 0 counts only if the time is still ahead.
    for (offset in 0..7) {
        val candidate = todayAtTime.plusDays(offset.toLong())
        if (!candidate.isAfter(now)) continue
        if (candidate.dayOfWeek in alarm.days) return candidate
    }
    // Unreachable: with a non-empty day set, one of the next 7 days matches.
    error("No trigger found for alarm ${alarm.id} — day set was ${alarm.days}")
}
