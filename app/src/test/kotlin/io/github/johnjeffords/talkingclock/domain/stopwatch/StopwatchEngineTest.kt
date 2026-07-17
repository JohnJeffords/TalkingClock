package io.github.johnjeffords.talkingclock.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** Count-up arithmetic against a hand-cranked monotonic clock. */
class StopwatchEngineTest {

    private var nowMs = 5_000_000L
    private val engine = StopwatchEngine { nowMs }

    private fun advance(d: Duration) {
        nowMs += d.toMillis()
    }

    @Test
    fun `idle at zero before start`() {
        val snap = engine.snapshot()
        assertEquals(StopwatchEngine.Phase.Idle, snap.phase)
        assertEquals(Duration.ZERO, snap.elapsed)
    }

    @Test
    fun `counts up while running`() {
        engine.start()
        advance(Duration.ofSeconds(90))
        assertEquals(Duration.ofSeconds(90), engine.snapshot().elapsed)
        assertEquals(StopwatchEngine.Phase.Running, engine.snapshot().phase)
    }

    @Test
    fun `pause freezes and resume continues exactly`() {
        engine.start()
        advance(Duration.ofSeconds(10))
        engine.pause()
        advance(Duration.ofHours(1)) // frozen
        assertEquals(Duration.ofSeconds(10), engine.snapshot().elapsed)
        assertEquals(StopwatchEngine.Phase.Paused, engine.snapshot().phase)

        engine.resume()
        advance(Duration.ofSeconds(5))
        assertEquals(Duration.ofSeconds(15), engine.snapshot().elapsed)
    }

    @Test
    fun `laps record cumulative and per-lap times`() {
        engine.start()
        advance(Duration.ofSeconds(70)) // lap 1 at 1:10
        engine.lap()
        advance(Duration.ofSeconds(50)) // lap 2 at 2:00 (lap time 0:50)
        engine.lap()

        val laps = engine.snapshot().laps
        assertEquals(2, laps.size)
        assertEquals(1, laps[0].number)
        assertEquals(Duration.ofSeconds(70), laps[0].cumulative)
        assertEquals(Duration.ofSeconds(70), laps[0].lapTime)
        assertEquals(2, laps[1].number)
        assertEquals(Duration.ofSeconds(120), laps[1].cumulative)
        assertEquals(Duration.ofSeconds(50), laps[1].lapTime)
    }

    @Test
    fun `lap while paused is ignored`() {
        engine.start()
        advance(Duration.ofSeconds(10))
        engine.pause()
        engine.lap()
        assertEquals(0, engine.snapshot().laps.size)
    }

    @Test
    fun `one second of monotonic time is exactly one second elapsed`() {
        engine.start()
        advance(Duration.ofMillis(1000))
        // Accuracy, stated bluntly: the readout tracks real time 1:1.
        assertEquals(1000L, engine.snapshot().elapsed.toMillis())
    }

    @Test
    fun `elapsed never drifts across many pause and resume cycles`() {
        // The "is the stopwatch accurate?" guard the owner asked to keep in
        // the build. 100 running stretches of 137 ms each, with a long paused
        // gap between every one that must NOT be counted. If the engine ever
        // accumulated per-tick or double-counted, this exact total would be
        // off — it can't be, because elapsed is always DERIVED from anchors.
        engine.start()
        repeat(100) {
            advance(Duration.ofMillis(137))
            engine.pause()
            advance(Duration.ofSeconds(9)) // paused: must not count
            engine.resume()
        }
        assertEquals(137L * 100, engine.snapshot().elapsed.toMillis())
    }

    @Test
    fun `reset clears everything`() {
        engine.start()
        advance(Duration.ofSeconds(30))
        engine.lap()
        engine.reset()

        val snap = engine.snapshot()
        assertEquals(StopwatchEngine.Phase.Idle, snap.phase)
        assertEquals(Duration.ZERO, snap.elapsed)
        assertEquals(0, snap.laps.size)
    }
}
