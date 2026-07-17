package io.github.johnjeffords.talkingclock.ui.stopwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.announce.StopwatchController
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Everything the Stopwatch screen draws. Laps come newest-first (that's the
 * reading order of the list) with the fastest/slowest of 3+ laps marked so
 * the UI can highlight them (design frame 09).
 */
data class StopwatchUiState(
    val phase: StopwatchEngine.Phase = StopwatchEngine.Phase.Idle,
    val elapsed: java.time.Duration = java.time.Duration.ZERO,
    val laps: List<LapRow> = emptyList(),
)

/** One row of the lap list, ready to render. */
data class LapRow(
    val lap: StopwatchEngine.Lap,
    val isFastest: Boolean,
    val isSlowest: Boolean,
)

/** Thin state holder: maps controller state to display rows. */
class StopwatchViewModel(
    private val controller: StopwatchController,
) : ViewModel() {

    val uiState: StateFlow<StopwatchUiState> =
        controller.state.map { ctrl ->
            val laps = ctrl.snapshot.laps
            // Highlighting one lap of two as "fastest" is noise; wait for 3.
            val fastest = if (laps.size >= 3) laps.minByOrNull { it.lapTime } else null
            val slowest = if (laps.size >= 3) laps.maxByOrNull { it.lapTime } else null
            StopwatchUiState(
                phase = ctrl.snapshot.phase,
                elapsed = ctrl.snapshot.elapsed,
                laps = laps.asReversed().map { lap ->
                    LapRow(
                        lap = lap,
                        isFastest = lap == fastest,
                        isSlowest = lap == slowest,
                    )
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = StopwatchUiState(),
        )

    fun startOrResume() {
        if (controller.state.value.snapshot.phase == StopwatchEngine.Phase.Running) return
        controller.start()
    }

    fun pause() = controller.pause()
    fun lap() = controller.lap()
    fun reset() = controller.reset()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TalkingClockApp
                StopwatchViewModel(app.stopwatchController)
            }
        }
    }
}
