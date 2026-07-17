package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerCue
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.domain.timer.cuesBetween
import io.github.johnjeffords.talkingclock.speech.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The talking timer's brain: owns the ONE [TimerEngine] (one active timer at
 * a time, by design), samples it a few times a second while running, speaks
 * whatever cues were crossed, and publishes state for the Timer screen and
 * the foreground-service notification.
 *
 * Same shape as [SpeakingClockController] and for the same reasons: a
 * process-wide singleton, Android-free (the monotonic source and the
 * service poke are injected), fully drivable under virtual time in tests.
 *
 * Announcing correctness rests on [cuesBetween]'s crossed-the-line rule:
 * this loop just feeds it (prevRemaining, nowRemaining) pairs, so uneven
 * ticks can't skip or double-fire a cue. Cues speak at [Speaker
 * .PRIORITY_TIMER] — they interrupt clock announcements, never vice versa.
 */
class TimerController(
    monotonicMs: () -> Long,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val ensureServiceRunning: () -> Unit,
) {

    /** What the Timer screen and the notification observe. */
    data class State(
        val snapshot: TimerEngine.Snapshot =
            TimerEngine.Snapshot(TimerEngine.Phase.Idle, Duration.ZERO, Duration.ZERO),
        val schedule: AnnouncementSchedule = AnnouncementSchedule.GAME,
        /** Pre-fill for the duration keypad: the last duration used. */
        val lastDuration: Duration = Duration.ofMinutes(15),
    )

    private val engine = TimerEngine(monotonicMs)
    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    private var tickJob: Job? = null

    /** Start a fresh countdown (replaces any current one — the design's
     *  "one active talking timer" rule). */
    fun start(duration: Duration, schedule: AnnouncementSchedule = stateFlow.value.schedule) {
        engine.start(duration)
        stateFlow.value = State(
            snapshot = engine.snapshot(),
            schedule = schedule,
            lastDuration = duration,
        )
        if (schedule.announceStart) {
            speaker.speak(Phrasebook.timerStarted(duration), Speaker.PRIORITY_TIMER)
        }
        ensureServiceRunning()

        tickJob?.cancel()
        tickJob = scope.launch { tickLoop(duration, schedule) }
    }

    /** Freeze the countdown; announcements pause with it (design frame 18). */
    fun pause() {
        engine.pause()
        publish()
    }

    /** Continue after [pause]. */
    fun resume() {
        engine.resume()
        publish()
    }

    /** Stop and forget the run (Reset / Dismiss). */
    fun reset() {
        tickJob?.cancel()
        tickJob = null
        engine.reset()
        speaker.stop()
        publish()
    }

    /** Change the announcement schedule for FUTURE runs (design dropdown). */
    fun selectSchedule(schedule: AnnouncementSchedule) {
        stateFlow.value = stateFlow.value.copy(schedule = schedule)
    }

    /**
     * While the timer lives: sample, speak crossed cues, publish. A 200 ms
     * cadence keeps the countdown numbers ("five, four, three…") landing
     * within a fifth of a second of their true instants — tight enough for
     * speech — without meaningful battery cost, and only while running.
     */
    private suspend fun tickLoop(duration: Duration, schedule: AnnouncementSchedule) {
        var prevRemaining = duration
        while (true) {
            delay(TICK_MILLIS)
            val snap = engine.snapshot()
            publish()

            if (snap.phase == TimerEngine.Phase.Running || snap.phase == TimerEngine.Phase.Finished) {
                val cues = cuesBetween(schedule, duration, prevRemaining, snap.remaining)
                if (cues.isNotEmpty()) speakCues(cues)
                prevRemaining = snap.remaining
            }

            // Once finished we keep ticking (the overtime display counts up)
            // but there are no more cues to fire past the zero crossing —
            // stop burning CPU shortly after overtime passes announcement
            // range... unless overtime display needs live updates, which it
            // does. The loop therefore runs until reset() cancels it; it's
            // 5 emissions/second only while a timer is alive.
        }
    }

    /** Speak a tick's cues as one utterance (multiple only after a stall). */
    private fun speakCues(cues: List<TimerCue>) {
        val text = cues.joinToString(". ") { cue ->
            when (cue) {
                is TimerCue.Checkpoint -> Phrasebook.timerRemaining(cue.remaining)
                is TimerCue.Halfway -> Phrasebook.timerHalfway(cue.remaining)
                is TimerCue.Countdown -> Phrasebook.numberWords(cue.number)
                TimerCue.TimesUp -> Phrasebook.TIMES_UP
            }
        }
        speaker.speak(text, Speaker.PRIORITY_TIMER)
    }

    private fun publish() {
        stateFlow.value = stateFlow.value.copy(snapshot = engine.snapshot())
    }

    companion object {
        private const val TICK_MILLIS = 200L
    }
}
