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
    fun `lowering the lead mid-run doesn't double a milestone`() = runTest {
        // The lead is latched at start; lowering it must not move the running
        // loop's frontier back up, which would re-cross the 10 s milestone.
        val controller = buildController()
        controller.speechLead = Duration.ofSeconds(3)
        controller.start()
        advance(Duration.ofSeconds(8)) // "Ten seconds" already fired early (elapsed 7)
        controller.speechLead = Duration.ZERO // user lowers the lead mid-run
        advance(Duration.ofSeconds(4)) // elapsed passes the real 10 s

        assertEquals(1, speaker.spoken.count { it == "Ten seconds" })
    }

    @Test
    fun `raising the lead across a pause doesn't drop the next milestone`() = runTest {
        val controller = buildController() // lead defaults to 0
        controller.start()
        advance(Duration.ofMillis(9_500)) // 1..5 s spoken; 10 s not yet
        controller.pause()
        controller.speechLead = Duration.ofSeconds(3) // user raises it while paused
        controller.resume()
        advance(Duration.ofSeconds(2)) // elapsed 9.5 -> 11.5, passes 10 s

        assertEquals(1, speaker.spoken.count { it == "Ten seconds" })
    }

    @Test
    fun `reset silences any in-flight speech`() = runTest {
        val controller = buildController()
        controller.start()
        advance(Duration.ofSeconds(2))
        controller.reset()
        assertTrue(speaker.stopCount >= 1)
        assertEquals(listOf(Speaker.PRIORITY_STOPWATCH), speaker.stoppedPriorities)
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
