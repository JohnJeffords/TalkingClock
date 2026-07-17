package io.github.johnjeffords.talkingclock.domain.stopwatch

import java.time.Duration

/**
 * Count-up arithmetic over a monotonic millisecond source — the stopwatch
 * sibling of TimerEngine, with the same non-negotiable: never the wall
 * clock, and nothing accumulates per tick (elapsed is always derived from
 * anchors, so it cannot drift). See docs/ARCHITECTURE.md → Timekeeping.
 *
 * Laps record the CUMULATIVE elapsed at each press; per-lap times are
 * derived by differencing, so laps can never disagree with the total.
 */
class StopwatchEngine(private val monotonicMs: () -> Long) {

    enum class Phase { Idle, Running, Paused }

    /** One recorded lap. [number] starts at 1 (people count laps from one). */
    data class Lap(
        val number: Int,
        /** Total elapsed when the lap was pressed. */
        val cumulative: Duration,
        /** This lap's own length (cumulative minus the previous lap's). */
        val lapTime: Duration,
    )

    data class Snapshot(
        val phase: Phase,
        val elapsed: Duration,
        val laps: List<Lap>,
    )

    private var accumulatedMs: Long = 0
    private var anchorMs: Long? = null // non-null while running
    private var laps = mutableListOf<Lap>()

    /** Begin counting (from zero when Idle, else continues — see [resume]). */
    fun start() {
        if (anchorMs != null) return // already running
        anchorMs = monotonicMs()
    }

    /** Freeze the count. */
    fun pause() {
        val anchor = anchorMs ?: return
        accumulatedMs += monotonicMs() - anchor
        anchorMs = null
    }

    /** Alias of [start]; reads better at call sites resuming from pause. */
    fun resume() = start()

    /** Record a lap at this instant (running only — a paused lap makes no sense). */
    fun lap() {
        if (anchorMs == null) return
        val now = currentElapsed()
        val previous = laps.lastOrNull()?.cumulative ?: Duration.ZERO
        laps += Lap(
            number = laps.size + 1,
            cumulative = now,
            lapTime = now - previous,
        )
    }

    /** Back to zero, laps cleared. */
    fun reset() {
        accumulatedMs = 0
        anchorMs = null
        laps = mutableListOf()
    }

    /**
     * Recreate a PAUSED stopwatch from persisted values (process-death
     * restore — same always-comes-back-paused rule as the timer; see
     * TimerEngine.restorePaused).
     */
    fun restorePaused(elapsed: Duration, restoredLaps: List<Lap>) {
        require(!elapsed.isNegative) { "Elapsed can't be negative" }
        accumulatedMs = elapsed.toMillis()
        anchorMs = null
        laps = restoredLaps.toMutableList()
    }

    fun snapshot(): Snapshot {
        val elapsed = currentElapsed()
        val phase = when {
            anchorMs != null -> Phase.Running
            elapsed.isZero && laps.isEmpty() -> Phase.Idle
            else -> Phase.Paused
        }
        return Snapshot(phase, elapsed, laps.toList())
    }

    private fun currentElapsed(): Duration {
        val running = anchorMs?.let { monotonicMs() - it } ?: 0
        return Duration.ofMillis(accumulatedMs + running)
    }
}
