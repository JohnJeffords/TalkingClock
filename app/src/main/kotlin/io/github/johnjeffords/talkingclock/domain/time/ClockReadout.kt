package io.github.johnjeffords.talkingclock.domain.time

/**
 * The pieces of a formatted clock display, already turned into strings and
 * ready to drop onto the screen. Splitting them out (rather than one big
 * string) lets the UI style each part differently — the seconds are shown
 * smaller and muted, the AM/PM smaller still.
 *
 * @property time the main "hours:minutes" text, e.g. "14:23" or "2:23".
 * @property seconds the two-digit seconds, e.g. "07", or null when the user
 *   has turned seconds off.
 * @property meridiem "AM"/"PM" in 12-hour mode, or null in 24-hour mode.
 * @property date the date line, e.g. "Tue, Jul 15".
 */
data class ClockReadout(
    val time: String,
    val seconds: String?,
    val meridiem: String?,
    val date: String,
)
