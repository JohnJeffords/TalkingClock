package io.github.johnjeffords.talkingclock.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * Tests the cue-crossing logic — the rule that every spoken cue fires
 * exactly once, in order, regardless of how uneven the sampling ticks are.
 */
class AnnouncementScheduleTest {

    private val game = AnnouncementSchedule.GAME
    private fun min(m: Long): Duration = Duration.ofMinutes(m)
    private fun sec(s: Long): Duration = Duration.ofSeconds(s)

    @Test
    fun `checkpoint fires when crossed`() {
        val cues = cuesBetween(game, min(20), prevRemaining = sec(5 * 60 + 1), nowRemaining = sec(5 * 60))
        assertEquals(listOf(TimerCue.Checkpoint(min(5))), cues)
    }

    @Test
    fun `checkpoint does not fire twice`() {
        // Next tick after the 5-min cue: no re-fire.
        val cues = cuesBetween(game, min(20), prevRemaining = sec(300), nowRemaining = sec(299))
        assertEquals(emptyList<TimerCue>(), cues)
    }

    @Test
    fun `checkpoints at or above the duration never fire`() {
        // A 2-minute timer: crossing 1 min fires, but 30/20/10/5/3/2 min don't exist for it.
        val cues = cuesBetween(game, min(2), prevRemaining = sec(61), nowRemaining = sec(60))
        assertEquals(listOf(TimerCue.Checkpoint(min(1))), cues)
    }

    @Test
    fun `a delayed tick fires all skipped cues in order`() {
        // One giant gap from 2:01 down to 0:25 (e.g. the process was starved):
        // both the 2-min... no, prev is 2:01 -> crossed 2 min? prev must be >
        // the mark: 2:01 > 2:00 yes -> fires; then 1 min; then 30 s.
        val cues = cuesBetween(game, min(20), prevRemaining = sec(121), nowRemaining = sec(25))
        assertEquals(
            listOf(
                TimerCue.Checkpoint(min(2)),
                TimerCue.Checkpoint(min(1)),
                TimerCue.Checkpoint(sec(30)),
            ),
            cues,
        )
    }

    @Test
    fun `countdown numbers fire one per second`() {
        assertEquals(
            listOf(TimerCue.Countdown(5)),
            cuesBetween(game, min(20), prevRemaining = sec(6), nowRemaining = sec(5)),
        )
        assertEquals(
            listOf(TimerCue.Countdown(4)),
            cuesBetween(game, min(20), prevRemaining = sec(5), nowRemaining = sec(4)),
        )
    }

    @Test
    fun `ten second checkpoint yields to the frequent countdown`() {
        // FREQUENT counts down from 10 — the 10 s checkpoint would collide
        // with the countdown's "ten", so the checkpoint is suppressed.
        val cues = cuesBetween(
            AnnouncementSchedule.FREQUENT,
            min(5),
            prevRemaining = sec(11),
            nowRemaining = sec(10),
        )
        assertEquals(listOf(TimerCue.Countdown(10)), cues)
    }

    @Test
    fun `times up fires on the zero crossing`() {
        val cues = cuesBetween(game, min(20), prevRemaining = sec(1), nowRemaining = Duration.ZERO)
        assertEquals(listOf(TimerCue.TimesUp), cues)
    }

    @Test
    fun `times up can be toggled off`() {
        val silentEnd = game.copy(announceTimesUp = false)
        val cues = cuesBetween(silentEnd, min(20), prevRemaining = sec(1), nowRemaining = Duration.ZERO)
        assertEquals(emptyList<TimerCue>(), cues)
    }

    @Test
    fun `halfway fires at half duration with the real remaining first`() {
        val cues = cuesBetween(
            AnnouncementSchedule.MINIMAL,
            min(10),
            prevRemaining = sec(5 * 60 + 1),
            nowRemaining = sec(5 * 60),
        )
        assertEquals(listOf(TimerCue.Halfway(min(5))), cues)
    }

    @Test
    fun `every minute style announces each whole minute`() {
        val cues = cuesBetween(
            AnnouncementSchedule.FREQUENT,
            min(5),
            prevRemaining = sec(4 * 60 + 1),
            nowRemaining = sec(4 * 60),
        )
        assertEquals(listOf(TimerCue.Checkpoint(min(4))), cues)
    }

    @Test
    fun `paused (no progress) fires nothing`() {
        assertEquals(
            emptyList<TimerCue>(),
            cuesBetween(game, min(20), prevRemaining = sec(300), nowRemaining = sec(300)),
        )
    }
}
