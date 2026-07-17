package io.github.johnjeffords.talkingclock.ui.stopwatch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine

/** Wires [StopwatchViewModel] to [StopwatchScreen]. Thin by design. */
@Composable
fun StopwatchRoute() {
    val viewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The tenths must look real-time. The controller samples at 100 ms (plenty
    // for the spoken announcements and the notification text), but a readout
    // frozen for up to 100 ms between samples reads as "slightly behind." So
    // while the stopwatch is RUNNING and this screen is on-screen, drive the
    // displayed elapsed from the frame clock instead — re-reading the live
    // monotonic elapsed every frame (~60 Hz), which is always current. Paused
    // and idle just show the settled value from the controller's state. The
    // frame loop stops automatically when the phase changes or the screen
    // leaves composition, so it costs nothing when not counting.
    val displayElapsed by produceState(uiState.elapsed, uiState.phase) {
        if (uiState.phase == StopwatchEngine.Phase.Running) {
            while (true) {
                withFrameMillis { } // suspend until the next frame
                value = viewModel.liveElapsed()
            }
        } else {
            value = uiState.elapsed
        }
    }

    StopwatchScreen(
        uiState = uiState.copy(elapsed = displayElapsed),
        onStartOrResume = viewModel::startOrResume,
        onPause = viewModel::pause,
        onLap = viewModel::lap,
        onReset = viewModel::reset,
    )
}
