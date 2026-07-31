package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.speech.FakeAnnouncer
import io.github.johnjeffords.talkingclock.speech.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Drives the whole armed lifecycle — arm, boundary announcements, re-arm,
 * disarm, auto-off — under VIRTUAL time, so an hour of speaking clock runs
 * in milliseconds of test time and the assertions are exact.
 *
 * Two clocks have to advance together for that to work: the coroutine test
 * scheduler (which fires the controller's `delay`s) and the [MutableClock]
 * the controller reads wall-clock time from. The [advanceSeconds] helper
 * moves both in one-second lockstep, which is exactly how real time behaves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeakingClockControllerTest {

    /** A [Clock] the test can push forward by hand. */
    private class MutableClock(
        private var current: Instant,
        private var currentZone: ZoneId,
    ) : Clock() {
        fun advance(duration: Duration) {
            current += duration
        }

        fun changeZone(zone: ZoneId) {
            currentZone = zone
        }

        override fun instant(): Instant = current
        override fun getZone(): ZoneId = currentZone
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    }

    // Test fixture: 10:00:07 UTC — deliberately NOT on a boundary.
    private val startAt = LocalDateTime.of(2000, 1, 1, 10, 0, 7)
    private val clock = MutableClock(startAt.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val speaker = FakeAnnouncer()

    /** Times the controller poked "make sure the service is up". Stopping
     *  is the service's own decision, so there is no stop signal to count. */
    private var servicePokes = 0

    private fun TestScope.buildController() = SpeakingClockController(
        clock = clock,
        announcer = speaker,
        scope = backgroundScope,
        ensureServiceRunning = { servicePokes++ },
    )

    /** Advance wall clock and coroutine scheduler together, 1 s at a time. */
    private fun TestScope.advanceSeconds(seconds: Int) {
        repeat(seconds) {
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1_000)
            runCurrent()
        }
    }

    @Test
    fun `announces at each wall-clock boundary`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(60))
        runCurrent()

        // Armed at 10:00:07 -> first boundary is 10:01:00, 53 s away.
        advanceSeconds(52)
        assertEquals(emptyList<String>(), speaker.spoken) // not yet

        advanceSeconds(1) // 10:01:00
        assertEquals(listOf("It's ten oh one"), speaker.spoken)

        advanceSeconds(60) // 10:02:00
        assertEquals(
            listOf("It's ten oh one", "It's ten oh two"),
            speaker.spoken,
        )
    }

    @Test
    fun `sub-minute interval speaks the seconds`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(15))
        runCurrent()

        advanceSeconds(8) // 10:00:07 -> 10:00:15
        assertEquals(listOf("It's ten o'clock and fifteen seconds"), speaker.spoken)
    }

    @Test
    fun `disarm stops announcements and the service`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(60))
        runCurrent()
        controller.disarm()

        advanceSeconds(120) // two boundaries pass in silence
        assertEquals(emptyList<String>(), speaker.spoken)
        assertFalse(controller.state.value.isArmed)
        assertNull(controller.state.value.nextAt)
        assertEquals(listOf(Speaker.PRIORITY_CLOCK), speaker.stoppedPriorities)
        assertEquals(1, servicePokes) // armed once; the service stops itself
    }

    @Test
    fun `auto-off disarms by itself and stops the service`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(60), autoOff = Duration.ofMinutes(2))
        runCurrent()

        // 10:01 and 10:02 announce; the auto-off moment is 10:02:07, checked
        // on the next wake after it passes.
        advanceSeconds(3 * 60)
        assertEquals(2, speaker.spoken.size)
        assertFalse(controller.state.value.isArmed)
        assertEquals(1, servicePokes)
    }

    @Test
    fun `re-arming replaces the interval without doubling announcements`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(60))
        runCurrent()
        controller.arm(SpeakInterval(300)) // switch to every 5 min
        runCurrent()

        // Next 5-min boundary after 10:00:07 is 10:05:00.
        advanceSeconds(5 * 60)
        assertEquals(listOf("It's ten oh five"), speaker.spoken)
    }

    @Test
    fun `custom interval is remembered for the menu slot`() = runTest {
        val controller = buildController()
        assertNull(controller.lastCustomInterval)

        controller.arm(SpeakInterval(7 * 60)) // 7 min — not a preset
        assertEquals(SpeakInterval(7 * 60), controller.lastCustomInterval)

        controller.arm(SpeakInterval(300)) // a preset — must NOT overwrite it
        assertEquals(SpeakInterval(7 * 60), controller.lastCustomInterval)
    }

    @Test
    fun `arming exposes next announcement and auto-off in state`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(300))
        runCurrent()

        val state = controller.state.value
        assertTrue(state.isArmed)
        assertEquals(LocalDateTime.of(2000, 1, 1, 10, 5, 0), state.nextAt)
        assertEquals(startAt.plusMinutes(60), state.autoOffAt)
        assertEquals(1, servicePokes)
    }

    @Test
    fun `timezone change realigns speech and preserves the auto-off instant`() = runTest {
        val controller = buildController()
        controller.arm(SpeakInterval(300))
        runCurrent()

        // The instant is unchanged, but local time jumps from 10:00 to 13:00.
        clock.changeZone(ZoneOffset.ofHours(3))
        controller.realign()
        runCurrent()

        assertEquals(
            LocalDateTime.of(2000, 1, 1, 13, 5, 0),
            controller.state.value.nextAt,
        )
        assertEquals(
            LocalDateTime.of(2000, 1, 1, 14, 0, 7),
            controller.state.value.autoOffAt,
        )

        advanceSeconds(4 * 60 + 53)
        assertEquals(listOf("It's one oh five"), speaker.spoken)
        assertTrue(controller.state.value.isArmed)
    }
}
