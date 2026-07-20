package io.github.johnjeffords.talkingclock.domain.time

import io.github.johnjeffords.talkingclock.domain.time.SecondTicker.Companion.millisUntilNextSecond
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Tests the second-alignment math that keeps the ticking clock on the beat.
 * The property that matters: from any instant, the wait lands us exactly on
 * the next whole second, and it's never zero (which would spin the loop).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecondTickerTest {

    private class MutableZoneClock(
        private var current: Instant,
        var currentZone: ZoneId,
    ) : Clock() {
        fun advanceOneSecond() {
            current = current.plusSeconds(1)
        }

        override fun instant(): Instant = current
        override fun getZone(): ZoneId = currentZone
        override fun withZone(zone: ZoneId): Clock = MutableZoneClock(current, zone)
    }

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

    @Test
    fun `each display tick reads the clock's current zone`() = runTest {
        val instant = LocalDateTime.of(2000, 1, 1, 10, 0)
            .toInstant(ZoneOffset.UTC)
        val clock = MutableZoneClock(instant, ZoneOffset.UTC)
        val ticks = mutableListOf<LocalDateTime>()
        backgroundScope.launch { SecondTicker(clock).ticks().take(2).toList(ticks) }

        runCurrent()
        clock.currentZone = ZoneOffset.ofHours(3)
        clock.advanceOneSecond()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(LocalDateTime.of(2000, 1, 1, 10, 0), ticks[0])
        assertEquals(LocalDateTime.of(2000, 1, 1, 13, 0, 1), ticks[1])
    }

    @Test
    fun `production wall clock does not capture the process start zone`() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Phoenix"))
            assertEquals(ZoneId.of("America/Phoenix"), SystemWallClock.zone)

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(ZoneId.of("America/New_York"), SystemWallClock.zone)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
}
