package io.github.johnjeffords.talkingclock.domain.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * The alarm-scheduling math. 1 January 2000 was a SATURDAY — every
 * expected date below is derived from that anchor.
 */
class AlarmTest {

    // Saturday, 10:00 in the morning.
    private val saturdayMorning = LocalDateTime.of(2000, 1, 1, 10, 0)

    private fun alarm(
        hour: Int,
        minute: Int = 0,
        days: Set<DayOfWeek> = emptySet(),
    ) = Alarm(id = "a", hour = hour, minute = minute, days = days)

    // --- One-shot ---

    @Test
    fun `one-shot later today fires today`() {
        val next = nextTriggerTime(alarm(14, 30), saturdayMorning)
        assertEquals(LocalDateTime.of(2000, 1, 1, 14, 30), next)
    }

    @Test
    fun `one-shot earlier today fires tomorrow`() {
        val next = nextTriggerTime(alarm(8, 0), saturdayMorning)
        assertEquals(LocalDateTime.of(2000, 1, 2, 8, 0), next)
    }

    @Test
    fun `one-shot exactly now fires tomorrow — strictly after`() {
        val next = nextTriggerTime(alarm(10, 0), saturdayMorning)
        assertEquals(LocalDateTime.of(2000, 1, 2, 10, 0), next)
    }

    // --- Repeating ---

    @Test
    fun `repeating fires later today when today is enabled`() {
        val next = nextTriggerTime(
            alarm(14, 0, days = setOf(DayOfWeek.SATURDAY)),
            saturdayMorning,
        )
        assertEquals(LocalDateTime.of(2000, 1, 1, 14, 0), next)
    }

    @Test
    fun `repeating skips to the next enabled weekday`() {
        // Monday-only alarm checked on Saturday: Jan 3 2000 is the Monday.
        val next = nextTriggerTime(
            alarm(7, 0, days = setOf(DayOfWeek.MONDAY)),
            saturdayMorning,
        )
        assertEquals(LocalDateTime.of(2000, 1, 3, 7, 0), next)
    }

    @Test
    fun `repeating wraps a full week when today's time has passed`() {
        // Saturday-only alarm at 8:00, checked Saturday 10:00 → NEXT Saturday.
        val next = nextTriggerTime(
            alarm(8, 0, days = setOf(DayOfWeek.SATURDAY)),
            saturdayMorning,
        )
        assertEquals(LocalDateTime.of(2000, 1, 8, 8, 0), next)
    }

    @Test
    fun `weekday set picks the soonest of several days`() {
        val next = nextTriggerTime(
            alarm(8, 0, days = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            saturdayMorning,
        )
        // Sunday Jan 2 beats Monday Jan 3.
        assertEquals(LocalDateTime.of(2000, 1, 2, 8, 0), next)
    }

    @Test
    fun `invalid times are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { alarm(24) }
        assertThrows(IllegalArgumentException::class.java) { alarm(10, 60) }
    }
}
