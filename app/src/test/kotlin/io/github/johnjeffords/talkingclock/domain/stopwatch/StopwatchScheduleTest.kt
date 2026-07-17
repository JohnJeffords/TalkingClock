package io.github.johnjeffords.talkingclock.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** The stopwatch's crossed-the-line cue generator (ascending mirror of the timer). */
class StopwatchScheduleTest {

    private fun sec(s: Long) = Duration.ofSeconds(s)
    private fun min(m: Long) = Duration.ofMinutes(m)

    @Test
    fun `no progress or going backwards says nothing`() {
        assertEquals(emptyList<StopwatchCue>(), stopwatchCuesBetween(sec(10), sec(10)))
        assertEquals(emptyList<StopwatchCue>(), stopwatchCuesBetween(sec(30), sec(20)))
    }

    @Test
    fun `the opening seconds are bare counts`() {
        assertEquals(
            listOf(
                StopwatchCue.Count(1),
                StopwatchCue.Count(2),
                StopwatchCue.Count(3),
                StopwatchCue.Count(4),
                StopwatchCue.Count(5),
            ),
            stopwatchCuesBetween(Duration.ZERO, sec(5)),
        )
    }

    @Test
    fun `marks past five seconds are spoken as elapsed time`() {
        assertEquals(
            listOf(StopwatchCue.Elapsed(sec(10)), StopwatchCue.Elapsed(sec(30))),
            stopwatchCuesBetween(sec(5), sec(35)),
        )
        assertEquals(
            listOf(StopwatchCue.Elapsed(min(1))),
            stopwatchCuesBetween(sec(31), sec(65)),
        )
    }

    @Test
    fun `a single late tick fires every mark it skipped, in order`() {
        // Jump from 0 to 1 minute in one sample: every mark up to a minute.
        assertEquals(
            listOf(
                StopwatchCue.Count(1),
                StopwatchCue.Count(2),
                StopwatchCue.Count(3),
                StopwatchCue.Count(4),
                StopwatchCue.Count(5),
                StopwatchCue.Elapsed(sec(10)),
                StopwatchCue.Elapsed(sec(30)),
                StopwatchCue.Elapsed(min(1)),
            ),
            stopwatchCuesBetween(Duration.ZERO, min(1)),
        )
    }

    @Test
    fun `each mark fires exactly once across contiguous windows`() {
        val first = stopwatchCuesBetween(Duration.ZERO, sec(30))
        val second = stopwatchCuesBetween(sec(30), min(1))
        // No overlap: the 30 s mark belongs only to the first window.
        assertEquals(StopwatchCue.Elapsed(sec(30)), first.last())
        assertEquals(listOf(StopwatchCue.Elapsed(min(1))), second)
    }
}
