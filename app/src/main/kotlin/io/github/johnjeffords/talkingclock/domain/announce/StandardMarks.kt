package io.github.johnjeffords.talkingclock.domain.announce

import java.time.Duration

/**
 * The app's shared "milestone" schedule, ascending:
 *
 *   1, 2, 3, 4, 5 seconds · 10 s · 30 s · 1, 2, 3, 4 minutes ·
 *   then every 5 minutes up to 1 hour · then every 10 minutes.
 *
 * The **stopwatch** announces these as ELAPSED marks as it counts up; the
 * **timer** announces the same marks as REMAINING as it counts down (its
 * mirror image). One list keeps the two tools symmetric — the pattern
 * accelerates near the interesting end (the start of a stopwatch, the finish
 * of a timer) and spreads out in the calm middle.
 *
 * @param upTo the largest mark to include (the stopwatch's current elapsed,
 *   or a bound covering the timer's duration). Pure function; tested in
 *   StandardMarksTest.
 */
fun standardMarks(upTo: Duration): List<Duration> {
    if (upTo.isNegative || upTo.isZero) return emptyList()

    val marks = mutableListOf<Duration>()
    // The count-up / final-countdown seconds, then the two lone second marks.
    listOf(1L, 2L, 3L, 4L, 5L, 10L, 30L).forEach { marks += Duration.ofSeconds(it) }
    // Every minute for the first few.
    (1L..4L).forEach { marks += Duration.ofMinutes(it) }
    // Every 5 minutes up to and including one hour.
    var minute = 5L
    while (minute <= 60L) {
        marks += Duration.ofMinutes(minute)
        minute += 5
    }
    // Every 10 minutes beyond an hour, as far as needed.
    val limitMinutes = upTo.toMinutes()
    minute = 70L
    while (minute <= limitMinutes) {
        marks += Duration.ofMinutes(minute)
        minute += 10
    }
    return marks.filter { it <= upTo }.distinct().sorted()
}
