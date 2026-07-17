package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.speech.FakeAnnouncer
import io.github.johnjeffords.talkingclock.speech.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** Virtual-time tests for the stopwatch controller's optional speech. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchControllerTest {

    private var nowMs = 0L
    private val speaker = FakeAnnouncer()
    private var servicePokes = 0

    private fun TestScope.buildController() = StopwatchController(
        monotonicMs = { nowMs },
        announcer = speaker,
        scope = backgroundScope,
        ensureServiceRunning = { servicePokes++ },
    )

    private fun TestScope.advance(duration: Duration) {
        var remaining = duration.toMillis()
        while (remaining > 0) {
            val step = minOf(100L, remaining)
            nowMs += step
            advanceTimeBy(step)
            runCurrent()
            remaining -= step
        }
    }

    @Test
    fun `silent by default even across announce boundaries`() = runTest {
        val controller = buildController()
        controller.start()
        advance(Duration.ofMinutes(2))
        assertEquals(emptyList<String>(), speaker.spoken)
    }

    @Test
    fun `announces elapsed at each whole multiple when enabled`() = runTest {
        val controller = buildController()
        controller.setAnnounceEvery(Duration.ofSeconds(30))
        controller.start()

        advance(Duration.ofSeconds(65))
        assertEquals(listOf("Thirty seconds", "One minute"), speaker.spoken)
        assertTrue(speaker.spokenPriorities.all { it == Speaker.PRIORITY_STOPWATCH })
    }

    @Test
    fun `speaks laps when enabled`() = runTest {
        val controller = buildController()
        controller.setSpeakLaps(true)
        controller.start()
        advance(Duration.ofSeconds(62))
        controller.lap()

        assertEquals(listOf("Lap one: one minute, two seconds"), speaker.spoken)
    }

    @Test
    fun `laps are silent by default`() = runTest {
        val controller = buildController()
        controller.start()
        advance(Duration.ofSeconds(30))
        controller.lap()
        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(1, controller.state.value.snapshot.laps.size)
    }

    @Test
    fun `pause stops announcements and resume continues`() = runTest {
        val controller = buildController()
        controller.setAnnounceEvery(Duration.ofSeconds(30))
        controller.start()
        advance(Duration.ofSeconds(10))
        controller.pause()
        advance(Duration.ofMinutes(5)) // silence while paused
        assertEquals(emptyList<String>(), speaker.spoken)

        controller.resume()
        advance(Duration.ofSeconds(21)) // elapsed crosses 0:30
        assertEquals(listOf("Thirty seconds"), speaker.spoken)
        assertEquals(StopwatchEngine.Phase.Running, controller.state.value.snapshot.phase)
    }
}
