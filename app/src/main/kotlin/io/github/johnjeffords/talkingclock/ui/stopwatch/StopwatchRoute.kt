package io.github.johnjeffords.talkingclock.ui.stopwatch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import java.time.Duration

/** Wires [StopwatchViewModel] to [StopwatchScreen]. Thin by design. */
@Composable
fun StopwatchRoute() {
    val viewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The hundredths must look real-time. The controller samples at 100 ms
    // (plenty for the spoken announcements and the notification text), but a
    // readout refreshed only every 100 ms looks sluggish next to a real
    // stopwatch. So while the stopwatch is RUNNING and this screen is on
    // screen, drive the displayed elapsed from the frame clock instead —
    // re-reading the live monotonic elapsed every frame (~refresh rate),
    // which is always current. Paused/idle just show the settled state
    // value. The loop stops when the phase changes or the screen leaves
    // composition, so it costs nothing when not counting.
    var displayElapsed by remember { mutableStateOf(uiState.elapsed) }
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == StopwatchEngine.Phase.Running) {
            while (true) {
                withFrameNanos { } // suspend until the next frame
                displayElapsed = viewModel.liveElapsed()
            }
        } else {
            displayElapsed = uiState.elapsed
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
