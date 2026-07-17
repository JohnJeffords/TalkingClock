package io.github.johnjeffords.talkingclock.domain.stopwatch

import io.github.johnjeffords.talkingclock.domain.announce.standardMarks
import java.time.Duration

/**
 * One thing the stopwatch should say as it counts up — the ascending mirror
 * of the timer's cues.
 */
sealed interface StopwatchCue {
    /** The opening count-up: bare "one", "two", … "five". */
    data class Count(val number: Int) : StopwatchCue

    /** A reached milestone: "Ten seconds", "One minute", "Five minutes". */
    data class Elapsed(val at: Duration) : StopwatchCue
}

/**
 * Which milestones the stopwatch crossed between two ELAPSED samples — the
 * count-up counterpart of the timer's [io.github.johnjeffords.talkingclock
 * .domain.timer.cuesBetween]. Same crossed-the-line guarantee: each mark
 * fires exactly once, and an uneven/late tick fires every mark it skipped,
 * in order.
 *
 * Marks up to 5 seconds are the opening count ("one, two, three, four,
 * five", bare numbers, like the timer's final countdown reversed); every
 * later mark is spoken as the elapsed time reached.
 *
 * Pure function; tested in StopwatchScheduleTest.
 */
fun stopwatchCuesBetween(prevElapsed: Duration, nowElapsed: Duration): List<StopwatchCue> {
    if (nowElapsed <= prevElapsed) return emptyList() // paused or no progress
    return standardMarks(nowElapsed)
        .filter { it > prevElapsed && it <= nowElapsed }
        .map { mark ->
            if (mark <= Duration.ofSeconds(5)) {
                StopwatchCue.Count(mark.seconds.toInt())
            } else {
                StopwatchCue.Elapsed(mark)
            }
        }
}
