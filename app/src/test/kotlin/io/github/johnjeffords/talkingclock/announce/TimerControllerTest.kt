package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.speech.FakeSpeaker
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

/**
 * Drives whole timer runs under virtual time and asserts the complete
 * spoken transcript — the strongest possible statement of what the talking
 * timer says and when. The monotonic source and the coroutine scheduler
 * advance in lockstep (like real time), 200 ms per step to match the
 * controller's sampling cadence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {

    private var nowMs = 0L
    private val speaker = FakeSpeaker()
    private var servicePokes = 0

    private fun TestScope.buildController() = TimerController(
        monotonicMs = { nowMs },
        speaker = speaker,
        scope = backgroundScope,
        ensureServiceRunning = { servicePokes++ },
    )

    /** Advance monotonic time and the scheduler together. */
    private fun TestScope.advance(duration: Duration) {
        var remaining = duration.toMillis()
        while (remaining > 0) {
            val step = minOf(200L, remaining)
            nowMs += step
            advanceTimeBy(step)
            runCurrent()
            remaining -= step
        }
    }

    @Test
    fun `a two-minute game-style run speaks the full transcript in order`() = runTest {
        val controller = buildController()
        controller.start(Duration.ofMinutes(2), AnnouncementSchedule.GAME)
        runCurrent()

        advance(Duration.ofMinutes(2)) // run it to the end

        assertEquals(
            listOf(
                "Timer started: two minutes",
                "One minute remaining",
                "Thirty seconds remaining",
                "Twenty seconds remaining",
                "Ten seconds remaining",
                "five",
                "four",
                "three",
                "two",
                "one",
                "Time's up",
            ),
            speaker.spoken,
        )
        // Every timer line outranks clock announcements (the collision law).
        assertTrue(speaker.spokenPriorities.all { it == Speaker.PRIORITY_TIMER })
        assertEquals(1, servicePokes)
    }

    @Test
    fun `pause silences cues and resume picks up where it left off`() = runTest {
        val controller = buildController()
        controller.start(Duration.ofMinutes(2), AnnouncementSchedule.GAME)
        runCurrent()

        advance(Duration.ofSeconds(30)) // 1:30 remaining, nothing crossed yet
        controller.pause()
        advance(Duration.ofMinutes(10)) // a long paused stretch: silence
        assertEquals(listOf("Timer started: two minutes"), speaker.spoken)

        controller.resume()
        advance(Duration.ofSeconds(31)) // crosses the 1-minute mark
        assertEquals(
            listOf("Timer started: two minutes", "One minute remaining"),
            speaker.spoken,
        )
    }

    @Test
    fun `reset silences the timer and clears state`() = runTest {
        val controller = buildController()
        controller.start(Duration.ofMinutes(2))
        runCurrent()
        advance(Duration.ofSeconds(10))

        controller.reset()
        advance(Duration.ofMinutes(3)) // nothing more is ever said
        assertEquals(listOf("Timer started: two minutes"), speaker.spoken)
        assertEquals(TimerEngine.Phase.Idle, controller.state.value.snapshot.phase)
        assertTrue(speaker.stopCount >= 1)
    }

    @Test
    fun `overtime keeps counting after times up`() = runTest {
        val controller = buildController()
        controller.start(Duration.ofSeconds(30), AnnouncementSchedule.GAME)
        runCurrent()

        advance(Duration.ofSeconds(45)) // 15 s past the end
        val snap = controller.state.value.snapshot
        assertEquals(TimerEngine.Phase.Finished, snap.phase)
        assertEquals(15L, snap.overtime.seconds)
        assertEquals("Time's up", speaker.spoken.last())
    }

    @Test
    fun `last duration is remembered for the keypad prefill`() = runTest {
        val controller = buildController()
        assertEquals(Duration.ofMinutes(15), controller.state.value.lastDuration) // default

        controller.start(Duration.ofMinutes(7))
        runCurrent()
        assertEquals(Duration.ofMinutes(7), controller.state.value.lastDuration)
    }
}
