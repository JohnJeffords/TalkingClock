package io.github.johnjeffords.talkingclock.domain.announce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** The shared ascending milestone schedule used by both the stopwatch and the timer. */
class StandardMarksTest {

    private fun sec(vararg s: Long) = s.map { Duration.ofSeconds(it) }
    private fun min(vararg m: Long) = m.map { Duration.ofMinutes(it) }

    @Test
    fun `non-positive bounds yield no marks`() {
        assertEquals(emptyList<Duration>(), standardMarks(Duration.ZERO))
        assertEquals(emptyList<Duration>(), standardMarks(Duration.ofSeconds(-5)))
    }

    @Test
    fun `the first minute is 1-2-3-4-5, 10, 30 seconds`() {
        assertEquals(
            sec(1, 2, 3, 4, 5, 10, 30),
            standardMarks(Duration.ofSeconds(59)),
        )
    }

    @Test
    fun `minutes go 1-2-3-4 then every five up to an hour`() {
        val marks = standardMarks(Duration.ofHours(1))
        // Every whole minute 1..4, then 5,10,15,…,60.
        val expectedMinuteMarks = min(1, 2, 3, 4) + (5L..60L step 5).map { Duration.ofMinutes(it) }
        assertEquals(expectedMinuteMarks, marks.filter { it >= Duration.ofMinutes(1) })
    }

    @Test
    fun `beyond an hour it steps every ten minutes`() {
        val marks = standardMarks(Duration.ofMinutes(90))
        // 60, 70, 80, 90 must all be present; 65 and 75 must not.
        assertTrue(marks.contains(Duration.ofMinutes(70)))
        assertTrue(marks.contains(Duration.ofMinutes(80)))
        assertTrue(marks.contains(Duration.ofMinutes(90)))
        assertTrue(marks.none { it == Duration.ofMinutes(65) || it == Duration.ofMinutes(75) })
    }

    @Test
    fun `the list is strictly ascending, unique, and bounded by upTo`() {
        val upTo = Duration.ofMinutes(47)
        val marks = standardMarks(upTo)
        assertEquals(marks.distinct(), marks)
        assertEquals(marks.sorted(), marks)
        assertTrue(marks.all { it <= upTo })
    }
}
