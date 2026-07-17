package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
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
 * The stopwatch's brain — the third and simplest of the announce
 * controllers (same singleton/Android-free/virtual-time-testable shape as
 * the other two; see SpeakingClockController's class doc for the pattern).
 *
 * Speech is opt-in and QUIET by design here: an optional every-N elapsed
 * announcement ("Five minutes") and an optional spoken lap on the Lap press
 * — both off by default, both at [Speaker.PRIORITY_STOPWATCH], which LOSES
 * every collision (a stopwatch line never talks over a timer or the clock).
 */
class StopwatchController(
    monotonicMs: () -> Long,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val ensureServiceRunning: () -> Unit,
) {

    data class State(
        val snapshot: StopwatchEngine.Snapshot =
            StopwatchEngine.Snapshot(StopwatchEngine.Phase.Idle, Duration.ZERO, emptyList()),
        /** Announce the elapsed time every this often; null = silent. */
        val announceEvery: Duration? = null,
        /** Speak "Lap three: …" on each Lap press. */
        val speakLaps: Boolean = false,
    )

    private val engine = StopwatchEngine(monotonicMs)
    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    private var tickJob: Job? = null

    fun start() {
        engine.start()
        publish()
        ensureServiceRunning()
        tickJob?.cancel()
        tickJob = scope.launch { tickLoop() }
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
            speaker.speak(
                Phrasebook.stopwatchLap(lap.number, lap.lapTime),
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
    fun setAnnounceEvery(every: Duration?) {
        stateFlow.value = stateFlow.value.copy(announceEvery = every)
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
     * Sample at 100 ms while running — the tenths digit on screen updates
     * that fast, and nothing runs at all once paused/reset. The every-N
     * announcement uses the crossed-a-multiple rule (same idea as the
     * timer's cues): announce when elapsed crosses a whole multiple of N.
     */
    private suspend fun tickLoop() {
        var prevElapsed = stateFlow.value.snapshot.elapsed
        while (true) {
            delay(TICK_MILLIS)
            publish()
            val now = stateFlow.value.snapshot.elapsed
            val every = stateFlow.value.announceEvery
            if (every != null && !every.isZero) {
                val prevMultiple = prevElapsed.toMillis() / every.toMillis()
                val nowMultiple = now.toMillis() / every.toMillis()
                if (nowMultiple > prevMultiple && !isQuietNow()) {
                    speaker.speak(
                        Phrasebook.stopwatchElapsed(
                            Duration.ofMillis(nowMultiple * every.toMillis()),
                        ),
                        Speaker.PRIORITY_STOPWATCH,
                    )
                }
            }
            prevElapsed = now
        }
    }

    private fun publish() {
        stateFlow.value = stateFlow.value.copy(snapshot = engine.snapshot())
    }

    companion object {
        private const val TICK_MILLIS = 100L
    }
}
