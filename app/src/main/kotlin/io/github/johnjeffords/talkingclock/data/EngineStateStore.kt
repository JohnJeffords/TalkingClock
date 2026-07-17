package io.github.johnjeffords.talkingclock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * Saves and restores the timer's and stopwatch's progress across process
 * death (task-swipe, low memory, reboot). Restores always come back PAUSED —
 * monotonic anchors don't survive the process, so claiming a timer "kept
 * running" while the app was dead would be a lie (docs/ARCHITECTURE.md).
 *
 * Laps serialize as "cumulativeMs,cumulativeMs,…" — per-lap times are
 * derived, so cumulative values are the only truth worth storing.
 */
class EngineStateStore(private val dataStore: DataStore<Preferences>) {

    /** A paused timer to restore, or null if none was saved. */
    data class SavedTimer(val duration: Duration, val remaining: Duration)

    /** A paused stopwatch to restore, or null if none was saved. */
    data class SavedStopwatch(val elapsed: Duration, val laps: List<StopwatchEngine.Lap>)

    suspend fun saveTimer(duration: Duration, remaining: Duration) {
        dataStore.edit { prefs ->
            prefs[KEY_TIMER_DURATION_MS] = duration.toMillis()
            // Clamp: overtime (negative remaining) restores as just-finished.
            prefs[KEY_TIMER_REMAINING_MS] = remaining.toMillis().coerceAtLeast(0)
        }
    }

    suspend fun clearTimer() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TIMER_DURATION_MS)
            prefs.remove(KEY_TIMER_REMAINING_MS)
        }
    }

    suspend fun loadTimer(): SavedTimer? {
        val prefs = dataStore.data.first()
        val duration = prefs[KEY_TIMER_DURATION_MS] ?: return null
        val remaining = prefs[KEY_TIMER_REMAINING_MS] ?: return null
        if (duration <= 0 || remaining < 0 || remaining > duration) return null
        return SavedTimer(Duration.ofMillis(duration), Duration.ofMillis(remaining))
    }

    suspend fun saveStopwatch(elapsed: Duration, laps: List<StopwatchEngine.Lap>) {
        dataStore.edit { prefs ->
            prefs[KEY_SW_ELAPSED_MS] = elapsed.toMillis()
            prefs[KEY_SW_LAPS] = laps.joinToString(",") { it.cumulative.toMillis().toString() }
        }
    }

    suspend fun clearStopwatch() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SW_ELAPSED_MS)
            prefs.remove(KEY_SW_LAPS)
        }
    }

    suspend fun loadStopwatch(): SavedStopwatch? {
        val prefs = dataStore.data.first()
        val elapsedMs = prefs[KEY_SW_ELAPSED_MS] ?: return null
        if (elapsedMs < 0) return null
        val laps = parseLaps(prefs[KEY_SW_LAPS].orEmpty())
        return SavedStopwatch(Duration.ofMillis(elapsedMs), laps)
    }

    /** Rebuild Lap objects from cumulative values (numbers + lap times derive). */
    private fun parseLaps(csv: String): List<StopwatchEngine.Lap> {
        if (csv.isBlank()) return emptyList()
        val cumulatives = csv.split(",").mapNotNull { it.toLongOrNull() }
        var previous = 0L
        return cumulatives.mapIndexed { index, cumulative ->
            val lap = StopwatchEngine.Lap(
                number = index + 1,
                cumulative = Duration.ofMillis(cumulative),
                lapTime = Duration.ofMillis(cumulative - previous),
            )
            previous = cumulative
            lap
        }
    }

    companion object {
        private val KEY_TIMER_DURATION_MS = longPreferencesKey("saved_timer_duration_ms")
        private val KEY_TIMER_REMAINING_MS = longPreferencesKey("saved_timer_remaining_ms")
        private val KEY_SW_ELAPSED_MS = longPreferencesKey("saved_sw_elapsed_ms")
        private val KEY_SW_LAPS = stringPreferencesKey("saved_sw_laps_csv")
    }
}
