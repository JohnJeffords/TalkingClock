package io.github.johnjeffords.talkingclock.domain.timer

import io.github.johnjeffords.talkingclock.domain.announce.standardMarks
import java.time.Duration

/**
 * WHAT the talking timer says and WHEN — a set of remaining-time checkpoints
 * plus a final countdown, with individual toggles for the extras
 * (docs/DESIGN.md → Announcement schedules; DECISIONS.md D-016).
 *
 * A schedule is pure data: the editor screen edits it, tests assert on it,
 * and [cuesBetween] turns it into concrete spoken cues. Only checkpoints
 * SHORTER than the timer's duration apply (a 2-minute timer doesn't hear
 * "Thirty minutes remaining").
 */
data class AnnouncementSchedule(
    val name: String,
    /** Remaining-time marks that get spoken, e.g. 5 min, 1 min, 30 s. */
    val checkpoints: Set<Duration>,
    /** Also announce every whole minute (the "Frequent" style). */
    val everyMinute: Boolean = false,
    /** Speak "<time> remaining. Halfway there." at half the duration. */
    val halfway: Boolean = false,
    /** Count the last N seconds aloud: 5-4-3-2-1. Zero disables. */
    val countdownFrom: Int = 5,
    /** Announce "Timer started: <duration>" on start. */
    val announceStart: Boolean = true,
    /** Announce "Time's up" at zero. */
    val announceTimesUp: Boolean = true,
) {
    companion object {
        private fun minutes(vararg m: Long) = m.map { Duration.ofMinutes(it) }
        private fun seconds(vararg s: Long) = s.map { Duration.ofSeconds(it) }

        /**
         * The default — the exact mirror of the stopwatch's ascending
         * milestones ([standardMarks]), read as REMAINING as the timer
         * counts down: … 10 min, 5 min, 4, 3, 2, 1 min, 30 s, 10 s, then the
         * 5-4-3-2-1 countdown, then "Time's up". Only marks below the timer's
         * duration fire, so the announcements naturally cluster near the end.
         * (The sub-5-second marks are the countdown, so they're excluded from
         * the checkpoint set here and handled by [countdownFrom].) A generous
         * 24-hour bound precomputes every mark any realistic timer needs.
         */
        val GAME = AnnouncementSchedule(
            name = "Milestones",
            checkpoints = standardMarks(Duration.ofHours(24))
                .filter { it.seconds > 5 }
                .toSet(),
            countdownFrom = 5,
            halfway = false,
        )

        /** Quiet: halfway + one minute + a short countdown. */
        val MINIMAL = AnnouncementSchedule(
            name = "Minimal",
            checkpoints = minutes(1).toSet(),
            halfway = true,
            countdownFrom = 3,
        )

        /** Chatty: every minute, then 30 s / 10 s, then a long countdown. */
        val FREQUENT = AnnouncementSchedule(
            name = "Frequent",
            checkpoints = seconds(30, 10).toSet(),
            everyMinute = true,
            countdownFrom = 10,
        )

        val BUILT_INS = listOf(GAME, MINIMAL, FREQUENT)
    }
}

/** One thing the timer should say right now. */
sealed interface TimerCue {
    /** "<time> remaining." — [remaining] is the checkpoint's round value. */
    data class Checkpoint(val remaining: Duration) : TimerCue

    /** "<time> remaining. Halfway there." (D-016: real time first). */
    data class Halfway(val remaining: Duration) : TimerCue

    /** A bare countdown number: "five", "four", … */
    data class Countdown(val number: Int) : TimerCue

    /** "Time's up." */
    data object TimesUp : TimerCue
}

/**
 * The next mark that WILL be spoken, given the current remaining time —
 * drives the running screen's "Next: one minute remaining" indicator.
 * Returns null when nothing is left to say before "Time's up".
 */
fun nextMark(
    schedule: AnnouncementSchedule,
    duration: Duration,
    remaining: Duration,
): Duration? {
    val marks = buildList {
        schedule.checkpoints
            .filter { it < duration && it.seconds > schedule.countdownFrom }
            .forEach { add(it) }
        if (schedule.everyMinute) {
            var m = Duration.ofMinutes(1)
            while (m < duration) {
                if (m.seconds > schedule.countdownFrom) add(m)
                m = m.plusMinutes(1)
            }
        }
        if (schedule.halfway && duration >= Duration.ofMinutes(1)) add(duration.dividedBy(2))
        if (schedule.countdownFrom > 0) add(Duration.ofSeconds(schedule.countdownFrom.toLong()))
    }
    return marks.filter { it < remaining }.maxOrNull()
}

/**
 * Which cues fired between two observations of the remaining time?
 *
 * The timer loop samples the remaining time every few hundred ms; a cue
 * fires when its mark was CROSSED between the previous sample and this one
 * (prev > mark ≥ now). That "crossed the line" rule is what guarantees each
 * cue fires exactly once no matter how uneven the sampling is — a delayed
 * tick fires every cue it skipped over, in order, and a pause/resume can't
 * double-fire anything because remaining time never moves backward.
 *
 * Pure function; exhaustively tested in AnnouncementScheduleTest.
 *
 * @param schedule what to announce.
 * @param duration the timer's full length (filters irrelevant checkpoints,
 *   anchors the halfway mark).
 * @param prevRemaining remaining time at the previous sample.
 * @param nowRemaining remaining time at this sample (may be negative once
 *   the timer has finished; zero-crossing fires [TimerCue.TimesUp]).
 */
fun cuesBetween(
    schedule: AnnouncementSchedule,
    duration: Duration,
    prevRemaining: Duration,
    nowRemaining: Duration,
): List<TimerCue> {
    if (prevRemaining <= nowRemaining) return emptyList() // paused or no progress

    val cues = mutableListOf<TimerCue>()

    fun crossed(mark: Duration): Boolean = prevRemaining > mark && nowRemaining <= mark

    // Checkpoints (longest first, so a big skip announces in natural order).
    val marks = buildSet {
        addAll(schedule.checkpoints)
        if (schedule.everyMinute) {
            var m = Duration.ofMinutes(1)
            while (m < duration) {
                add(m); m = m.plusMinutes(1)
            }
        }
    }
    marks
        .filter { it < duration } // a checkpoint equal to the duration is just "start"
        .sortedDescending()
        .forEach { mark ->
            if (crossed(mark) && mark.seconds > schedule.countdownFrom) {
                cues += TimerCue.Checkpoint(mark)
            }
        }

    // Halfway (only meaningful for timers of at least a minute).
    if (schedule.halfway && duration >= Duration.ofMinutes(1)) {
        val half = duration.dividedBy(2)
        if (crossed(half)) cues += TimerCue.Halfway(half)
    }

    // Final countdown: each whole second from countdownFrom down to 1.
    for (n in schedule.countdownFrom downTo 1) {
        if (crossed(Duration.ofSeconds(n.toLong()))) cues += TimerCue.Countdown(n)
    }

    // Zero crossing.
    if (schedule.announceTimesUp && crossed(Duration.ZERO)) cues += TimerCue.TimesUp

    return cues
}
