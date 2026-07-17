package io.github.johnjeffords.talkingclock.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** The stopwatch's crossed-the-line milestone generator (ascending mirror of the timer). */
class StopwatchScheduleTest {

    private fun sec(s: Long) = Duration.ofSeconds(s)
    private fun min(m: Long) = Duration.ofMinutes(m)

    @Test
    fun `no progress or going backwards says nothing`() {
        assertEquals(emptyList<Duration>(), stopwatchCuesBetween(sec(10), sec(10)))
        assertEquals(emptyList<Duration>(), stopwatchCuesBetween(sec(30), sec(20)))
    }

    @Test
    fun `the opening seconds are individual marks`() {
        assertEquals(
            listOf(sec(1), sec(2), sec(3), sec(4), sec(5)),
            stopwatchCuesBetween(Duration.ZERO, sec(5)),
        )
    }

    @Test
    fun `later marks are the milestone times`() {
        assertEquals(listOf(sec(10), sec(30)), stopwatchCuesBetween(sec(5), sec(35)))
        assertEquals(listOf(min(1)), stopwatchCuesBetween(sec(31), sec(65)))
    }

    @Test
    fun `a single late tick fires every mark it skipped, in order`() {
        // Jump from 0 to 1 minute in one sample: every mark up to a minute.
        assertEquals(
            listOf(sec(1), sec(2), sec(3), sec(4), sec(5), sec(10), sec(30), min(1)),
            stopwatchCuesBetween(Duration.ZERO, min(1)),
        )
    }

    @Test
    fun `each mark fires exactly once across contiguous windows`() {
        val first = stopwatchCuesBetween(Duration.ZERO, sec(30))
        val second = stopwatchCuesBetween(sec(30), min(1))
        // No overlap: the 30 s mark belongs only to the first window.
        assertEquals(sec(30), first.last())
        assertEquals(listOf(min(1)), second)
    }
}
