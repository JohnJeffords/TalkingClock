package io.github.johnjeffords.talkingclock.domain.stopwatch

import io.github.johnjeffords.talkingclock.domain.announce.standardMarks
import java.time.Duration

/**
 * Which elapsed milestones the stopwatch crossed between two ELAPSED samples
 * — the count-up counterpart of the timer's [io.github.johnjeffords
 * .talkingclock.domain.timer.cuesBetween]. Same crossed-the-line guarantee:
 * each mark fires exactly once, and an uneven/late tick fires every mark it
 * skipped, in order.
 *
 * Every mark — including the opening seconds — is announced as the elapsed
 * time reached ("One second", "Five seconds", "Ten seconds", "One minute").
 * Speaking the opening count as full "N seconds" phrases (rather than bare
 * numbers) keeps the intro consistent with the rest AND long enough that the
 * speech-lead latency compensation lands it on the number — a bare "one"
 * synthesizes faster than the lead assumes and slips in early.
 *
 * Pure function; tested in StopwatchScheduleTest.
 */
fun stopwatchCuesBetween(prevElapsed: Duration, nowElapsed: Duration): List<Duration> {
    if (nowElapsed <= prevElapsed) return emptyList() // paused or no progress
    return standardMarks(nowElapsed).filter { it > prevElapsed && it <= nowElapsed }
}
