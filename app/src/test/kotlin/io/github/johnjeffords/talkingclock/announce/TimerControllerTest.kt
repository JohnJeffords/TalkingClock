package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
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
    private val speaker = FakeAnnouncer()
    private var servicePokes = 0

    private fun TestScope.buildController() = TimerController(
        monotonicMs = { nowMs },
        announcer = speaker,
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
            // The default schedule now mirrors the stopwatch's milestones,
            // which has 30 s and 10 s but no 20 s mark.
            listOf(
                "Timer started: two minutes",
                "One minute remaining",
                "Thirty seconds remaining",
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
    fun `a one-second speech lead fires cues a second early`() = runTest {
        val controller = buildController()
        controller.speechLead = Duration.ofSeconds(1)
        controller.start(Duration.ofMinutes(2), AnnouncementSchedule.GAME)
        runCurrent()

        // The 1-minute cue lands with ~61 s left (a second early), not 60 s.
        advance(Duration.ofSeconds(59)) // 1:01 remaining
        assertEquals(
            listOf("Timer started: two minutes", "One minute remaining"),
            speaker.spoken,
        )
    }

    @Test
    fun `lowering the lead mid-run doesn't double a cue`() = runTest {
        // The lead is latched at start; lowering it must not move the running
        // loop's frontier back up, which would re-cross the 1-minute mark.
        val controller = buildController()
        controller.speechLead = Duration.ofSeconds(1)
        controller.start(Duration.ofMinutes(2), AnnouncementSchedule.GAME)
        runCurrent()

        advance(Duration.ofSeconds(59)) // "One minute remaining" fires ~1 s early
        controller.speechLead = Duration.ZERO // user lowers the lead mid-run
        advance(Duration.ofSeconds(5)) // count down through the real 60 s mark

        assertEquals(1, speaker.spoken.count { it == "One minute remaining" })
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
        assertEquals(listOf(Speaker.PRIORITY_TIMER), speaker.stoppedPriorities)
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

    @Test
    fun `a sixty-hour timer starts without crashing its announcement`() = runTest {
        val controller = buildController()

        controller.start(Duration.ofHours(60))
        runCurrent()

        assertEquals(listOf("Timer started: 60 hours"), speaker.spoken)
        assertEquals(TimerEngine.Phase.Running, controller.state.value.snapshot.phase)
        assertEquals(1, servicePokes)
    }
}
