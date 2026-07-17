package io.github.johnjeffords.talkingclock.domain.announce

import java.time.LocalTime

/**
 * Quiet hours: a daily window during which the speaking clock stays silent
 * (design frame 21). Pure math on minutes-since-midnight so it's trivially
 * testable — including the overnight wrap, which is the whole trick.
 *
 * @property fromMinutes window start, minutes since local midnight.
 * @property untilMinutes window end, minutes since local midnight.
 */
data class QuietWindow(val fromMinutes: Int, val untilMinutes: Int) {

    /**
     * Is [time] inside the window? Two shapes exist:
     *  - same-day window (from < until), e.g. 13:00–15:00 — inside means
     *    from ≤ t < until;
     *  - OVERNIGHT window (from > until), e.g. 22:00–07:00 — inside means
     *    after 22:00 OR before 07:00.
     * A zero-length window (from == until) is treated as never-quiet: that's
     * what a user who dragged both handles together almost certainly means.
     */
    fun contains(time: LocalTime): Boolean {
        val t = time.hour * 60 + time.minute
        return when {
            fromMinutes == untilMinutes -> false
            fromMinutes < untilMinutes -> t in fromMinutes until untilMinutes
            else -> t >= fromMinutes || t < untilMinutes
        }
    }
}
