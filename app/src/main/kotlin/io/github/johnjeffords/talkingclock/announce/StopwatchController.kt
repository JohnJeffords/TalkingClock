package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchCue
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.domain.stopwatch.stopwatchCuesBetween
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
 * The stopwatch's brain — the third and simplest of the announce
 * controllers (same singleton/Android-free/virtual-time-testable shape as
 * the other two; see SpeakingClockController's class doc for the pattern).
 *
 * It speaks the ascending milestones ([stopwatchCuesBetween]) as it counts
 * up — "one, two, three, four, five", then "Ten seconds", "Thirty seconds",
 * "One minute"… — ON by default (the whole point of a talking stopwatch),
 * with a settings toggle. Spoken laps on the Lap press are a separate,
 * off-by-default option. Everything here speaks at [Speaker
 * .PRIORITY_STOPWATCH], which LOSES every collision (a stopwatch line never
 * talks over a timer or the clock).
 */
class StopwatchController(
    monotonicMs: () -> Long,
    private val announcer: Announcer,
    private val scope: CoroutineScope,
    private val ensureServiceRunning: () -> Unit,
) {

    data class State(
        val snapshot: StopwatchEngine.Snapshot =
            StopwatchEngine.Snapshot(StopwatchEngine.Phase.Idle, Duration.ZERO, emptyList()),
        /** Announce the ascending milestones as it counts up. ON by default:
         *  a talking stopwatch that stays silent isn't a talking stopwatch. */
        val speakElapsed: Boolean = true,
        /** Speak "Lap three: …" on each Lap press. */
        val speakLaps: Boolean = false,
    )

    private val engine = StopwatchEngine(monotonicMs)
    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    private var tickJob: Job? = null

    /**
     * How far AHEAD of each milestone to start speaking, to cancel out the
     * TTS engine's own latency (without it, a word said at the 10 s mark
     * comes out of the speaker a beat late — "one second behind"). Pushed by
     * the settings collector; 0 restores exact on-the-mark timing. Applied by
     * looking ahead: the loop tests the milestones against elapsed + lead.
     */
    @Volatile
    var speechLead: Duration = Duration.ZERO

    fun start() {
        // Fresh runs seed the look-ahead at the true start (so a mark within
        // one lead of zero — the opening "one" — still fires); resuming seeds
        // it already-shifted so a mark announced early before the pause can't
        // fire twice. resume() routes here as Paused, a fresh press as Idle.
        val resuming = engine.snapshot().phase == StopwatchEngine.Phase.Paused
        engine.start()
        publish()
        ensureServiceRunning()
        tickJob?.cancel()
        val seed = engine.snapshot().elapsed + if (resuming) speechLead else Duration.ZERO
        tickJob = scope.launch { tickLoop(seed) }
    }

    fun pause() {
        engine.pause()
        publish()
        // The display is frozen; no reason to keep sampling.
        tickJob?.cancel()
        tickJob = null
    }

    fun resume() = start()

    fun lap() {
        engine.lap()
        publish()
        val lap = stateFlow.value.snapshot.laps.lastOrNull() ?: return
        if (stateFlow.value.speakLaps) {
            announcer.announce(
                Utterance.StopwatchLap(lap.number, lap.lapTime),
                Speaker.PRIORITY_STOPWATCH,
            )
        }
    }

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        engine.reset()
        publish()
    }

    /** Settings hooks, pushed by the app-level settings collector. */
    fun setSpeakElapsed(speak: Boolean) {
        stateFlow.value = stateFlow.value.copy(speakElapsed = speak)
    }

    fun setSpeakLaps(speak: Boolean) {
        stateFlow.value = stateFlow.value.copy(speakLaps = speak)
    }

    /**
     * Quiet-hours check (same clock-side rule as the speaking clock: the
     * stopwatch's ambient elapsed announcements go silent during quiet
     * hours; lap presses are a deliberate user action and still speak).
     */
    @Volatile
    var isQuietNow: () -> Boolean = { false }

    /** Process-death restore: paused at the persisted elapsed time + laps. */
    fun restorePaused(elapsed: Duration, laps: List<StopwatchEngine.Lap>) {
        if (stateFlow.value.snapshot.phase != StopwatchEngine.Phase.Idle) return
        engine.restorePaused(elapsed, laps)
        publish()
    }

    /**
     * The live elapsed time, read straight off the monotonic clock right now
     * — not the last published snapshot. The display uses this to redraw the
     * tenths every frame so the readout is never up to a tick stale (which
     * reads as "slightly slow"); the 100 ms [tickLoop] is enough for the
     * announcements and the notification, but not for a crisp counter.
     */
    fun currentElapsed(): Duration = engine.snapshot().elapsed

    /**
     * Sample at 100 ms while running — fast enough to catch each milestone
     * as it's crossed, and nothing runs at all once paused/reset. Every
     * milestone the [stopwatchCuesBetween] rule reports is spoken (a late
     * tick fires all it skipped, in order).
     */
    private suspend fun tickLoop(seedElapsed: Duration) {
        var prevElapsed = seedElapsed
        while (true) {
            delay(TICK_MILLIS)
            publish()
            // Look ahead by the speech lead so the word starts early enough
            // to LAND on the milestone despite TTS latency.
            val now = stateFlow.value.snapshot.elapsed + speechLead
            if (stateFlow.value.speakElapsed && !isQuietNow()) {
                stopwatchCuesBetween(prevElapsed, now).forEach(::announceCue)
            }
            prevElapsed = now
        }
    }

    /** Speak one milestone: bare numbers for the opening count, the reached
     *  elapsed time otherwise. */
    private fun announceCue(cue: StopwatchCue) {
        val utterance = when (cue) {
            is StopwatchCue.Count -> Utterance.Raw(Phrasebook.numberWords(cue.number))
            is StopwatchCue.Elapsed -> Utterance.StopwatchElapsed(cue.at)
        }
        announcer.announce(utterance, Speaker.PRIORITY_STOPWATCH)
    }

    private fun publish() {
        stateFlow.value = stateFlow.value.copy(snapshot = engine.snapshot())
    }

    companion object {
        private const val TICK_MILLIS = 100L
    }
}
