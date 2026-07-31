package io.github.johnjeffords.talkingclock.domain.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalTime
import java.util.TimeZone as JavaTimeZone

/**
 * The clock must follow the device's time zone. A talking clock that keeps
 * speaking the old local time after travel is stating something confidently
 * false, so this is the regression that matters most about it.
 */
class SystemZoneClockTest {

    private val original: JavaTimeZone = JavaTimeZone.getDefault()

    @After
    fun restoreZone() {
        JavaTimeZone.setDefault(original)
    }

    @Test
    fun `the zone follows a device time-zone change`() {
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/Phoenix"))
        assertEquals("America/Phoenix", SystemZoneClock.zone.id)

        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/New_York"))
        // The same singleton now reports the NEW zone — no restart needed.
        assertEquals("America/New_York", SystemZoneClock.zone.id)
    }

    @Test
    fun `the spoken local time re-aligns after the change`() {
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/Phoenix"))
        val phoenix = LocalTime.now(SystemZoneClock)

        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/New_York"))
        val newYork = LocalTime.now(SystemZoneClock)

        // Phoenix and New York are never the same local hour (Arizona doesn't
        // observe DST, so the gap is 2 or 3 hours, but never zero).
        assertNotEquals(phoenix.hour, newYork.hour)
    }

    @Test
    fun `a captured-zone clock is what goes stale`() {
        // Documents the bug this class exists to prevent: the JDK's own
        // systemDefaultZone() snapshots the zone at construction.
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/Phoenix"))
        val captured = Clock.systemDefaultZone()

        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone("America/New_York"))
        assertEquals("America/Phoenix", captured.zone.id) // stale
        assertEquals("America/New_York", SystemZoneClock.zone.id) // current
    }
}
