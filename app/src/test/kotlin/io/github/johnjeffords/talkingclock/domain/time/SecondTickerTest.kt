package io.github.johnjeffords.talkingclock.domain.time

import io.github.johnjeffords.talkingclock.domain.time.SecondTicker.Companion.millisUntilNextSecond
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the second-alignment math that keeps the ticking clock on the beat.
 * The property that matters: from any instant, the wait lands us exactly on
 * the next whole second, and it's never zero (which would spin the loop).
 */
class SecondTickerTest {

    @Test
    fun `exactly on a second boundary waits a full second`() {
        // At ...000 ms there's a whole second until the next boundary.
        assertEquals(1000L, millisUntilNextSecond(1_000L))
        assertEquals(1000L, millisUntilNextSecond(0L))
    }

    @Test
    fun `partway through a second waits only the remainder`() {
        // At ...600 ms, 400 ms remain until the next whole second.
        assertEquals(400L, millisUntilNextSecond(1_600L))
        // At ...001 ms, 999 ms remain.
        assertEquals(999L, millisUntilNextSecond(1_001L))
        // At ...999 ms, just 1 ms remains.
        assertEquals(1L, millisUntilNextSecond(1_999L))
    }

    @Test
    fun `wait is always between 1 and 1000 inclusive`() {
        // Spot-check across a whole second: the result stays in (0, 1000],
        // so the flow never busy-loops with a zero delay.
        for (ms in 0L until 1000L) {
            val wait = millisUntilNextSecond(ms)
            assert(wait in 1L..1000L) { "wait $wait out of range for ms=$ms" }
        }
    }
}
