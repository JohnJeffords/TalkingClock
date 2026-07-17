package io.github.johnjeffords.talkingclock.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration

/**
 * Tests the countdown arithmetic against a hand-cranked monotonic clock.
 * The properties that matter: remaining is always derived (never drifts),
 * pause banks time exactly, and overtime goes negative rather than lying.
 */
class TimerEngineTest {

    /** A fake elapsedRealtime() the test advances by hand. */
    private var nowMs = 1_000_000L
    private val engine = TimerEngine { nowMs }

    private fun advance(d: Duration) {
        nowMs += d.toMillis()
    }

    @Test
    fun `idle before start`() {
        assertEquals(TimerEngine.Phase.Idle, engine.snapshot().phase)
    }

    @Test
    fun `running timer counts down`() {
        engine.start(Duration.ofMinutes(5))
        advance(Duration.ofSeconds(90))

        val snap = engine.snapshot()
        assertEquals(TimerEngine.Phase.Running, snap.phase)
        assertEquals(Duration.ofSeconds(5 * 60 - 90), snap.remaining)
        assertEquals(Duration.ofMinutes(5), snap.duration)
    }

    @Test
    fun `pause freezes remaining and resume continues exactly`() {
        engine.start(Duration.ofMinutes(5))
        advance(Duration.ofSeconds(60))
        engine.pause()

        // A long pause changes nothing.
        advance(Duration.ofHours(2))
        assertEquals(Duration.ofMinutes(4), engine.snapshot().remaining)
        assertEquals(TimerEngine.Phase.Paused, engine.snapshot().phase)

        engine.resume()
        advance(Duration.ofSeconds(30))
        assertEquals(Duration.ofSeconds(3 * 60 + 30), engine.snapshot().remaining)
        assertEquals(TimerEngine.Phase.Running, engine.snapshot().phase)
    }

    @Test
    fun `finish goes negative into overtime rather than clamping`() {
        engine.start(Duration.ofSeconds(10))
        advance(Duration.ofSeconds(47))

        val snap = engine.snapshot()
        assertEquals(TimerEngine.Phase.Finished, snap.phase)
        assertEquals(Duration.ofSeconds(-37), snap.remaining)
        assertEquals(Duration.ofSeconds(37), snap.overtime)
    }

    @Test
    fun `reset returns to idle`() {
        engine.start(Duration.ofMinutes(5))
        advance(Duration.ofSeconds(10))
        engine.reset()
        assertEquals(TimerEngine.Phase.Idle, engine.snapshot().phase)
    }

    @Test
    fun `pause when not running is a no-op`() {
        engine.pause() // idle: nothing happens
        engine.start(Duration.ofMinutes(1))
        engine.pause()
        engine.pause() // second pause changes nothing
        assertEquals(Duration.ofMinutes(1), engine.snapshot().remaining)
    }

    @Test
    fun `restart replaces the previous run completely`() {
        engine.start(Duration.ofMinutes(5))
        advance(Duration.ofMinutes(2))
        engine.start(Duration.ofSeconds(30))

        assertEquals(Duration.ofSeconds(30), engine.snapshot().remaining)
    }

    @Test
    fun `zero or negative durations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { engine.start(Duration.ZERO) }
        assertThrows(IllegalArgumentException::class.java) {
            engine.start(Duration.ofSeconds(-5))
        }
    }
}
