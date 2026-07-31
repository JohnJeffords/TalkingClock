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

    /**
     * How far AHEAD of each cue to start speaking, to cancel out the TTS
     * engine's own latency (without it, "One minute remaining" lands a beat
     * late — "one second behind"). Pushed by the settings collector; 0
     * restores exact on-the-mark timing. Applied by looking ahead: cues are
     * tested against remaining − lead, so each fires that much sooner.
     *
     * This is the DESIRED lead; the value actually used by a run is latched
     * into [runLead] when it starts — see there.
     */
    @Volatile
    var speechLead: Duration = Duration.ZERO

    /**
     * The lead in force for the current run, latched when it starts and held
     * across pause/resume. Changing [speechLead] mid-run must NOT shift an
     * in-flight loop's frontier: the loop tests cues against remaining −
     * runLead, and if that quantity jumped when the setting changed it would
     * re-cross an already-spoken cue (double-fire) or skip an un-spoken one
     * (drop), breaking cuesBetween's exactly-once guarantee. A new lead
     * therefore applies to the NEXT run, not the one already counting.
     */
    private var runLead: Duration = Duration.ZERO

    /** Start a fresh countdown (replaces any current one — the design's
     *  "one active talking timer" rule). */
    fun start(duration: Duration, schedule: AnnouncementSchedule = stateFlow.value.schedule) {
        runLead = speechLead // latch the lead for this whole run
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
        // Fresh run: seed the look-ahead at the true remaining (unshifted) so
        // a cue within one lead of the start still fires.
        startTickLoop(engine.snapshot().remaining)
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
        // Resume: seed already-shifted (by the run's latched lead, not the
        // live setting) so a cue announced early before the pause can't fire
        // a second time here.
        startTickLoop(engine.snapshot().remaining - runLead)
    }

    /** Stop and forget the run (Reset / Dismiss). */
    fun reset() {
        stopTickLoop()
        engine.reset()
        announcer.stop(Speaker.PRIORITY_TIMER)
        publish()
    }

    /** Change the announcement schedule for FUTURE runs (design dropdown). */
    fun selectSchedule(schedule: AnnouncementSchedule) {
        stateFlow.value = stateFlow.value.copy(schedule = schedule)
    }

    /**
     * The live snapshot, read straight off the monotonic clock right now —
     * not the last published one. The Timer screen uses this to redraw the
     * ring and countdown every frame so they move at the display's refresh
     * rate; the 200 ms [tickLoop] is enough for the announcements and the
     * notification, but a ring that only steps 5×/second looks janky.
     */
    fun currentSnapshot(): TimerEngine.Snapshot = engine.snapshot()

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
        runLead = speechLead // latch for the restored run's eventual resume
        engine.restorePaused(duration, remaining)
        publish()
        ensureServiceRunning()
    }

    // --- The sampling loop ---------------------------------------------------

    private fun startTickLoop(seedRemaining: Duration) {
        tickJob?.cancel()
        tickJob = scope.launch { tickLoop(seedRemaining) }
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
    private suspend fun tickLoop(seedRemaining: Duration) {
        val duration = stateFlow.value.snapshot.duration
        val schedule = stateFlow.value.schedule
        var prevRemaining = seedRemaining
        while (true) {
            delay(TICK_MILLIS)
            val snap = engine.snapshot()
            publish()

            // Look ahead by the run's latched lead (remaining − lead) so each
            // cue starts early enough to LAND on its mark despite TTS latency.
            val nowRemaining = snap.remaining - runLead
            val cues = cuesBetween(schedule, duration, prevRemaining, nowRemaining)
            if (cues.isNotEmpty() && !isQuietNow()) {
                // A tick's cues travel as ONE utterance (several only after
                // a stall) so the voice pack or TTS renders them together.
                announcer.announce(Utterance.TimerCues(cues), Speaker.PRIORITY_TIMER)
            }
            prevRemaining = nowRemaining
        }
    }

    private fun publish() {
        stateFlow.value = stateFlow.value.copy(snapshot = engine.snapshot())
    }

    companion object {
        private const val TICK_MILLIS = 200L
    }
}
