package io.github.johnjeffords.talkingclock.domain.time

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a moment in time into the strings shown on the Clock screen.
 *
 * This is deliberately a PURE function of its inputs — it takes the exact
 * date-time, the 12/24-hour choice, the show-seconds choice, and a locale,
 * and returns strings. It touches no system clock and no Android classes, so
 * it can be unit-tested exhaustively on the plain JVM (midnight rollover,
 * noon/midnight in 12-hour mode, etc.) — see TimeFormatterTest.
 *
 * The caller (the ViewModel) is responsible for supplying "now" from a
 * [java.time.Clock], which is what makes the whole thing testable with a
 * fake clock.
 */
object TimeFormatter {

    // "Tuesday · July 15" — full weekday and month with a middle dot, exactly
    // as the design's clock frames show the date line.
    private const val DATE_PATTERN = "EEEE · MMMM d"

    /**
     * @param dateTime the moment to display.
     * @param use24Hour true for "14:23", false for "2:23 PM".
     * @param showSeconds whether to include the two-digit seconds.
     * @param locale the locale for the date line's day/month names.
     */
    fun format(
        dateTime: LocalDateTime,
        use24Hour: Boolean,
        showSeconds: Boolean,
        locale: Locale,
    ): ClockReadout {
        val hour24 = dateTime.hour
        val minute = dateTime.minute

        val timeText: String
        val meridiem: String?
        if (use24Hour) {
            // 24-hour is always two digits: 00:00 .. 23:59.
            timeText = "%02d:%02d".format(hour24, minute)
            meridiem = null
        } else {
            // 12-hour: map 0->12, 13->1, etc. The hour is NOT zero-padded
            // (we want "2:23", not "02:23"), but the minute always is.
            val hour12 = ((hour24 + 11) % 12) + 1
            timeText = "%d:%02d".format(hour12, minute)
            meridiem = if (hour24 < 12) "AM" else "PM"
        }

        val secondsText = if (showSeconds) "%02d".format(dateTime.second) else null
        val dateText = DateTimeFormatter.ofPattern(DATE_PATTERN, locale).format(dateTime)

        return ClockReadout(
            time = timeText,
            seconds = secondsText,
            meridiem = meridiem,
            date = dateText,
        )
    }
}
