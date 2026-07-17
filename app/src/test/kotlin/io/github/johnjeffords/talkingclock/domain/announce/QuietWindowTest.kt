package io.github.johnjeffords.talkingclock.domain.announce

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** The overnight wrap is the whole reason this class exists — test it hard. */
class QuietWindowTest {

    private val overnight = QuietWindow(22 * 60, 7 * 60) // 22:00 → 07:00

    @Test
    fun `overnight window covers late evening and early morning`() {
        assertTrue(overnight.contains(LocalTime.of(23, 30)))
        assertTrue(overnight.contains(LocalTime.of(3, 0)))
        assertTrue(overnight.contains(LocalTime.of(22, 0)))   // start inclusive
        assertTrue(overnight.contains(LocalTime.of(6, 59)))
    }

    @Test
    fun `overnight window excludes the daytime`() {
        assertFalse(overnight.contains(LocalTime.of(7, 0)))   // end exclusive
        assertFalse(overnight.contains(LocalTime.of(12, 0)))
        assertFalse(overnight.contains(LocalTime.of(21, 59)))
    }

    @Test
    fun `same-day window behaves like a plain range`() {
        val afternoon = QuietWindow(13 * 60, 15 * 60)
        assertTrue(afternoon.contains(LocalTime.of(13, 0)))
        assertTrue(afternoon.contains(LocalTime.of(14, 59)))
        assertFalse(afternoon.contains(LocalTime.of(15, 0)))
        assertFalse(afternoon.contains(LocalTime.of(12, 59)))
    }

    @Test
    fun `zero-length window is never quiet`() {
        val zero = QuietWindow(9 * 60, 9 * 60)
        assertFalse(zero.contains(LocalTime.of(9, 0)))
        assertFalse(zero.contains(LocalTime.of(21, 0)))
    }
}
