package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.domain.timer.cuesBetween
import io.github.johnjeffords.talkingclock.speech.Announcer
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.Utterance
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
 * a time, by design), samples it a few times a second while it's alive,
 * speaks whatever cues were crossed, and publishes state for the Timer
 * screen and the foreground-service notification.
 *
 * Same shape as [SpeakingClockController] and for the same reasons: a
 * process-wide singleton, Android-free (the monotonic source and the
 * service poke are injected), fully drivable under virtual time in tests.
 *
 * Announcing correctness rests on [cuesBetween]'s crossed-the-line rule:
 * the tick loop just feeds it (prevRemaining, nowRemaining) pairs, so
 * uneven ticks can't skip or double-fire a cue. Cues speak at
 * [Speaker.PRIORITY_TIMER] — they interrupt clock lines, never vice versa.
 *
 * Loop lifecycle: the sampling loop runs ONLY while the timer is alive and
 * un-paused — [start] and [resume] launch it, [pause] and [reset] cancel it
 * (a paused timer needs no sampling; its display is frozen). Each launch
 * seeds prevRemaining from the engine's CURRENT remaining, so a pause/
 * resume — or a process-death restore — can never double-fire a cue.
 */
class TimerController(
    monotonicMs: () -> Long,
    private val announcer: Announcer,
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

    /**
     * Quiet-hours check for timers, injected by the app wiring. Only active
     * when the user has quiet hours on AND has turned the "allow timers"
     * exception OFF — the default is that timers ring through quiet hours
     * (missing a timer is usually worse than hearing it at night).
     */
    @Volatile
    var isQuietNow: () -> Boolean = { false }

    /** Start a fresh countdown (replaces any current one — the design's
     *  "one active talking timer" rule). */
    fun start(duration: Duration, schedule: AnnouncementSchedule = stateFlow.value.schedule) {
        engine.start(duration)
        stateFlow.value = State(
            snapshot = engine.snapshot(),
            schedule = schedule,
            lastDuration = duration,
        )
        if (schedule.announceStart && !isQuietNow()) {
            announcer.announce(Utterance.TimerStarted(duration), Speaker.PRIORITY_TIMER)
        }
        ensureServiceRunning()
        startTickLoop()
    }

    /** Freeze the countdown; announcements (and sampling) pause with it. */
    fun pause() {
        engine.pause()
        stopTickLoop()
        publish()
    }

    /** Continue after [pause] (or after a process-death restore). */
    fun resume() {
        if (stateFlow.value.snapshot.phase != TimerEngine.Phase.Paused) return
        engine.resume()
        publish()
        ensureServiceRunning()
        startTickLoop()
    }

    /** Stop and forget the run (Reset / Dismiss). */
    fun reset() {
        stopTickLoop()
        engine.reset()
        announcer.stop()
        publish()
    }

    /** Change the announcement schedule for FUTURE runs (design dropdown). */
    fun selectSchedule(schedule: AnnouncementSchedule) {
        stateFlow.value = stateFlow.value.copy(schedule = schedule)
    }

    /** Settings restore: the keypad prefill from the previous app run. */
    fun restoreLastDuration(duration: Duration) {
        if (stateFlow.value.snapshot.phase == TimerEngine.Phase.Idle) {
            stateFlow.value = stateFlow.value.copy(lastDuration = duration)
        }
    }

    /**
     * Process-death restore: bring back an interrupted run as PAUSED at its
     * last known remaining time. Never auto-running — monotonic anchors
     * don't survive the process, and silently "resuming" a timer that
     * wasn't actually counting would lie (docs/ARCHITECTURE.md).
     */
    fun restorePaused(duration: Duration, remaining: Duration) {
        if (stateFlow.value.snapshot.phase != TimerEngine.Phase.Idle) return
        if (remaining.isNegative || remaining > duration) return // corrupt state: ignore
        engine.restorePaused(duration, remaining)
        publish()
        ensureServiceRunning()
    }

    // --- The sampling loop ---------------------------------------------------

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch { tickLoop() }
    }

    private fun stopTickLoop() {
        tickJob?.cancel()
        tickJob = null
    }

    /**
     * While the timer runs: sample, speak crossed cues, publish. A 200 ms
     * cadence keeps the countdown numbers ("five, four, three…") landing
     * within a fifth of a second of their true instants — tight enough for
     * speech — without meaningful battery cost. After the zero crossing the
     * loop keeps ticking so the overtime display counts up, until reset().
     */
    private suspend fun tickLoop() {
        val duration = stateFlow.value.snapshot.duration
        val schedule = stateFlow.value.schedule
        var prevRemaining = engine.snapshot().remaining
        while (true) {
            delay(TICK_MILLIS)
            val snap = engine.snapshot()
            publish()

            val cues = cuesBetween(schedule, duration, prevRemaining, snap.remaining)
            if (cues.isNotEmpty() && !isQuietNow()) {
                // A tick's cues travel as ONE utterance (several only after
                // a stall) so the voice pack or TTS renders them together.
                announcer.announce(Utterance.TimerCues(cues), Speaker.PRIORITY_TIMER)
            }
            prevRemaining = snap.remaining
        }
    }

    private fun publish() {
        stateFlow.value = stateFlow.value.copy(snapshot = engine.snapshot())
    }

    companion object {
        private const val TICK_MILLIS = 200L
    }
}
