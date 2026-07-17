package io.github.johnjeffords.talkingclock.domain.timer

import java.time.Duration

/**
 * The countdown itself: start / pause / resume / reset arithmetic over a
 * MONOTONIC millisecond source (`SystemClock.elapsedRealtime()` in the app;
 * a fake counter in tests). Never the wall clock — wall time jumps with NTP
 * corrections and timezone changes, and a timer that jumps with it is broken
 * (docs/ARCHITECTURE.md → Timekeeping rules).
 *
 * There is no ticking loop in here and nothing accumulates per tick: the
 * engine stores anchor points and derives the remaining time on demand, so
 * it cannot drift. `elapsed = accumulated + (now - anchor)` — pause banks
 * the elapsed time into `accumulated`, resume plants a fresh anchor.
 *
 * Remaining time goes NEGATIVE once the timer finishes — that's overtime
 * ("+0:37" in the design), and the presentation layer decides whether to
 * show it or clamp at zero. The engine just reports the truth.
 */
class TimerEngine(private val monotonicMs: () -> Long) {

    /** Where the timer is in its life (design frames 05/06/18/07/07b). */
    enum class Phase { Idle, Running, Paused, Finished }

    /** One consistent view of the timer, derived at a single instant. */
    data class Snapshot(
        val phase: Phase,
        /** The full length this run was started with (zero when Idle). */
        val duration: Duration,
        /** Time left; NEGATIVE while in overtime after finishing. */
        val remaining: Duration,
    ) {
        /** How far past zero we are (positive only after the finish). */
        val overtime: Duration
            get() = if (remaining.isNegative) remaining.negated() else Duration.ZERO
    }

    private var durationMs: Long = 0
    private var accumulatedMs: Long = 0
    private var anchorMs: Long? = null // non-null while running

    /** Begin a fresh countdown of [duration]. Replaces any previous run. */
    fun start(duration: Duration) {
        require(!duration.isNegative && !duration.isZero) { "Timer needs a positive duration" }
        durationMs = duration.toMillis()
        accumulatedMs = 0
        anchorMs = monotonicMs()
    }

    /** Freeze the countdown (and the announcements with it). */
    fun pause() {
        val anchor = anchorMs ?: return // not running: nothing to pause
        accumulatedMs += monotonicMs() - anchor
        anchorMs = null
    }

    /** Continue after [pause]. */
    fun resume() {
        if (anchorMs != null || durationMs == 0L) return // running or idle
        anchorMs = monotonicMs()
    }

    /** Back to Idle; forgets the run entirely. */
    fun reset() {
        durationMs = 0
        accumulatedMs = 0
        anchorMs = null
    }

    /**
     * Recreate a PAUSED run from persisted values — the process-death
     * restore path. Monotonic anchors are only meaningful within one boot
     * of one process, so a restored timer always comes back paused at its
     * last known remaining time and the user resumes it deliberately.
     */
    fun restorePaused(duration: Duration, remaining: Duration) {
        require(!duration.isNegative && !duration.isZero) { "Timer needs a positive duration" }
        require(!remaining.isNegative && remaining <= duration) {
            "Remaining must be within 0..duration"
        }
        durationMs = duration.toMillis()
        accumulatedMs = duration.toMillis() - remaining.toMillis()
        anchorMs = null
    }

    /** The current truth, derived (not stored) — see class doc. */
    fun snapshot(): Snapshot {
        if (durationMs == 0L) {
            return Snapshot(Phase.Idle, Duration.ZERO, Duration.ZERO)
        }
        val anchor = anchorMs
        val elapsed = accumulatedMs + if (anchor != null) monotonicMs() - anchor else 0
        val remaining = durationMs - elapsed
        val phase = when {
            remaining <= 0 -> Phase.Finished
            anchor != null -> Phase.Running
            else -> Phase.Paused
        }
        return Snapshot(phase, Duration.ofMillis(durationMs), Duration.ofMillis(remaining))
    }
}
