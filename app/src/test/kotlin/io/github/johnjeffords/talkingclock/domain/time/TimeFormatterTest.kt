package io.github.johnjeffords.talkingclock.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

/**
 * Exhaustive tests for the clock formatter. Because [TimeFormatter] is a pure
 * function, we can nail down every tricky case — the noon/midnight edges of
 * 12-hour time, zero-padding, seconds on/off — with plain JVM assertions and
 * no emulator. These are the cases most likely to be wrong in a clock.
 */
class TimeFormatterTest {

    private val locale = Locale.US

    // A fixed date so the date-line assertions are deterministic.
    // 1 January 2000 was a Saturday.
    private fun at(hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(2000, 1, 1, hour, minute, second)

    // --- 24-hour mode: always two-digit hours ---

    @Test
    fun `24-hour afternoon is two digits with no meridiem`() {
        val r = TimeFormatter.format(at(14, 23), use24Hour = true, showSeconds = false, locale)
        assertEquals("14:23", r.time)
        assertNull(r.meridiem)
    }

    @Test
    fun `24-hour midnight is 00 00`() {
        val r = TimeFormatter.format(at(0, 0), use24Hour = true, showSeconds = false, locale)
        assertEquals("00:00", r.time)
    }

    @Test
    fun `24-hour single-digit hour is zero-padded`() {
        val r = TimeFormatter.format(at(9, 5), use24Hour = true, showSeconds = false, locale)
        assertEquals("09:05", r.time)
    }

    // --- 12-hour mode: the noon/midnight rollover is the classic bug ---

    @Test
    fun `12-hour midnight reads 12 AM`() {
        val r = TimeFormatter.format(at(0, 0), use24Hour = false, showSeconds = false, locale)
        assertEquals("12:00", r.time)
        assertEquals("AM", r.meridiem)
    }

    @Test
    fun `12-hour noon reads 12 PM`() {
        val r = TimeFormatter.format(at(12, 0), use24Hour = false, showSeconds = false, locale)
        assertEquals("12:00", r.time)
        assertEquals("PM", r.meridiem)
    }

    @Test
    fun `12-hour afternoon subtracts 12 and marks PM`() {
        val r = TimeFormatter.format(at(14, 23), use24Hour = false, showSeconds = false, locale)
        assertEquals("2:23", r.time)   // hour NOT zero-padded in 12-hour mode
        assertEquals("PM", r.meridiem)
    }

    @Test
    fun `12-hour just before midnight reads 11 59 PM`() {
        val r = TimeFormatter.format(at(23, 59), use24Hour = false, showSeconds = false, locale)
        assertEquals("11:59", r.time)
        assertEquals("PM", r.meridiem)
    }

    @Test
    fun `12-hour early morning reads single-digit hour AM`() {
        val r = TimeFormatter.format(at(1, 5), use24Hour = false, showSeconds = false, locale)
        assertEquals("1:05", r.time)
        assertEquals("AM", r.meridiem)
    }

    // --- Seconds ---

    @Test
    fun `seconds are two digits when shown`() {
        val r = TimeFormatter.format(at(14, 23, 7), use24Hour = true, showSeconds = true, locale)
        assertEquals("07", r.seconds)
    }

    @Test
    fun `seconds are absent when hidden`() {
        val r = TimeFormatter.format(at(14, 23, 7), use24Hour = true, showSeconds = false, locale)
        assertNull(r.seconds)
    }

    // --- Date line ---

    @Test
    fun `date line shows full weekday and month with middle dot`() {
        val r = TimeFormatter.format(at(9, 0), use24Hour = true, showSeconds = false, locale)
        assertEquals("Saturday · January 1", r.date)
    }
}
