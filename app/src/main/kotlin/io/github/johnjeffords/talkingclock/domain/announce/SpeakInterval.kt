package io.github.johnjeffords.talkingclock.domain.announce

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * How often the speaking clock announces the time. The preset list matches
 * the design's interval menu exactly (frame 03):
 * Off · 15 s · 20 s · 1 min · 5 min · 10 min · 20 min · 30 min · 1 h ·
 * <last-used custom> · Custom…
 *
 * An interval is just a number of seconds plus a label; "Off" is the absence
 * of an interval (null in the state model) rather than a magic zero value —
 * that way there is no "armed with interval 0" state to mishandle.
 */
data class SpeakInterval(val seconds: Int) {

    init {
        require(seconds in MIN_SECONDS..MAX_SECONDS) {
            "Interval must be $MIN_SECONDS..$MAX_SECONDS seconds, was $seconds"
        }
    }

    /** Human label, e.g. "15 s", "5 min", "1 h", "90 s" for odd customs. */
    val label: String
        get() = when {
            seconds % 3600 == 0 -> "${seconds / 3600} h"
            seconds % 60 == 0 -> "${seconds / 60} min"
            else -> "$seconds s"
        }

    /** Spoken/a11y-friendly label, e.g. "every 5 minutes". */
    val spokenLabel: String
        get() = when {
            seconds % 3600 == 0 -> {
                val h = seconds / 3600
                if (h == 1) "every hour" else "every $h hours"
            }
            seconds % 60 == 0 -> {
                val m = seconds / 60
                if (m == 1) "every minute" else "every $m minutes"
            }
            else -> "every $seconds seconds"
        }

    companion object {
        // Guardrails: sub-15s would be constant chatter; >24h is a calendar,
        // not a clock.
        const val MIN_SECONDS = 15
        const val MAX_SECONDS = 24 * 3600

        /** The fixed menu presets from the design, in menu order. */
        val PRESETS: List<SpeakInterval> = listOf(
            SpeakInterval(15),
            SpeakInterval(20),
            SpeakInterval(60),
            SpeakInterval(5 * 60),
            SpeakInterval(10 * 60),
            SpeakInterval(20 * 60),
            SpeakInterval(30 * 60),
            SpeakInterval(3600),
        )
    }
}

/**
 * The next wall-clock moment (strictly after [now]) that lands on a whole
 * multiple of [interval] since local midnight.
 *
 * This alignment is a product rule, not an implementation detail: "every
 * 5 min" speaks at :00, :05, :10 …, and "every 15 s" at :00/:15/:30/:45 —
 * never at odd offsets from whenever the user happened to tap. Predictable
 * beats, and the announced time is always a round number (docs/DESIGN.md).
 *
 * Pure function — fully covered by unit tests including the midnight
 * rollover, where the "next boundary" is 00:00 tomorrow.
 */
fun nextAnnouncementTime(now: LocalDateTime, interval: SpeakInterval): LocalDateTime {
    val secondsSinceMidnight = now.toLocalTime().toSecondOfDay()
    // Integer division trick: the next multiple of N strictly greater than s.
    val nextMultiple = (secondsSinceMidnight / interval.seconds + 1) * interval.seconds

    val midnight = now.toLocalDate().atStartOfDay()
    return if (nextMultiple >= Duration.ofDays(1).seconds) {
        midnight.plusDays(1) // rolled past midnight -> 00:00 tomorrow
    } else {
        midnight.plusSeconds(nextMultiple.toLong())
    }
}

/** Convenience: [nextAnnouncementTime] when you only care about the time of day. */
fun nextAnnouncementTimeOfDay(now: LocalDateTime, interval: SpeakInterval): LocalTime =
    nextAnnouncementTime(now, interval).toLocalTime()
