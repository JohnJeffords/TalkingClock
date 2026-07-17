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

/** Virtual-time tests for the stopwatch's ascending milestone announcements. */
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
    fun `speaks the ascending milestones by default`() = runTest {
        val controller = buildController()
        controller.start()

        advance(Duration.ofSeconds(65))
        assertEquals(
            listOf(
                // The opening seconds, spoken as full elapsed phrases.
                "One second", "Two seconds", "Three seconds", "Four seconds", "Five seconds",
                "Ten seconds",
                "Thirty seconds",
                "One minute",
            ),
            speaker.spoken,
        )
        // Stopwatch lines lose every collision (lowest priority).
        assertTrue(speaker.spokenPriorities.all { it == Speaker.PRIORITY_STOPWATCH })
    }

    @Test
    fun `a one-second speech lead fires each milestone a second early`() = runTest {
        val controller = buildController()
        controller.speechLead = Duration.ofSeconds(1)
        controller.start()

        advance(Duration.ofMillis(8_900)) // just before the early 10 s cue
        assertEquals(
            listOf("One second", "Two seconds", "Three seconds", "Four seconds", "Five seconds"),
            speaker.spoken,
        )

        advance(Duration.ofMillis(200)) // elapsed crosses 9 s -> 10 s cue, 1 s early
        assertEquals(
            listOf(
                "One second", "Two seconds", "Three seconds", "Four seconds", "Five seconds",
                "Ten seconds",
            ),
            speaker.spoken,
        )
    }

    @Test
    fun `speaking can be turned off`() = runTest {
        val controller = buildController()
        controller.setSpeakElapsed(false)
        controller.start()

        advance(Duration.ofMinutes(2))
        assertEquals(emptyList<String>(), speaker.spoken)
    }

    @Test
    fun `speaks laps when enabled`() = runTest {
        val controller = buildController()
        controller.setSpeakElapsed(false) // isolate lap speech from the milestones
        controller.setSpeakLaps(true)
        controller.start()
        advance(Duration.ofSeconds(62))
        controller.lap()

        assertEquals(listOf("Lap one: one minute, two seconds"), speaker.spoken)
    }

    @Test
    fun `laps are silent by default`() = runTest {
        val controller = buildController()
        controller.setSpeakElapsed(false)
        controller.start()
        advance(Duration.ofSeconds(30))
        controller.lap()
        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(1, controller.state.value.snapshot.laps.size)
    }

    @Test
    fun `pause stops announcements and resume never repeats a milestone`() = runTest {
        val controller = buildController()
        controller.start()
        advance(Duration.ofSeconds(6)) // crosses 1..5
        val opening =
            listOf("One second", "Two seconds", "Three seconds", "Four seconds", "Five seconds")
        assertEquals(opening, speaker.spoken)

        controller.pause()
        advance(Duration.ofMinutes(5)) // silence while paused
        assertEquals(opening, speaker.spoken)

        controller.resume()
        advance(Duration.ofSeconds(5)) // elapsed 6 -> 11 s, crosses 10 s
        assertEquals(opening + "Ten seconds", speaker.spoken)
        assertEquals(StopwatchEngine.Phase.Running, controller.state.value.snapshot.phase)
    }
}
