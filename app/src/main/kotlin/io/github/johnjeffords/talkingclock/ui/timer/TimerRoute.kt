package io.github.johnjeffords.talkingclock.ui.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.ui.StartBackgroundFeature
import io.github.johnjeffords.talkingclock.ui.rememberHapticAction

/**
 * Wires [TimerViewModel] to [TimerScreen]. Thin by design — the screen is a
 * pure function of state (screenshot-testable), the ViewModel owns the
 * keypad logic, the controller owns the timer itself.
 */
@Composable
fun TimerRoute(startBackgroundFeature: StartBackgroundFeature) {
    val viewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The progress ring and countdown must move at the display's refresh rate,
    // not in the 5-per-second steps the controller's 200 ms sample loop would
    // give. While the timer is RUNNING (counting down) or FINISHED (overtime
    // counting up), re-read the live remaining/overtime/progress every frame
    // and merge them over the controller's state; idle/paused just use the
    // settled state. The controller's slower loop still drives the spoken
    // cues and the notification. The frame loop stops when the phase changes
    // or the screen leaves composition.
    var live by remember {
        mutableStateOf(LiveTimerValues(uiState.remaining, uiState.overtime, uiState.progress))
    }
    LaunchedEffect(uiState.phase) {
        val moving = uiState.phase == TimerEngine.Phase.Running ||
            uiState.phase == TimerEngine.Phase.Finished
        if (moving) {
            while (true) {
                withFrameNanos { } // suspend until the next frame
                live = viewModel.liveTimer()
            }
        } else {
            live = LiveTimerValues(uiState.remaining, uiState.overtime, uiState.progress)
        }
    }

    TimerScreen(
        uiState = uiState.copy(
            remaining = live.remaining,
            overtime = live.overtime,
            progress = live.progress,
        ),
        onDigit = rememberHapticAction(viewModel::typeDigit),
        onDoubleZero = rememberHapticAction(viewModel::typeDoubleZero),
        onDelete = rememberHapticAction(viewModel::deleteDigit),
        onStart = rememberHapticAction { startBackgroundFeature(viewModel::start) },
        onPause = rememberHapticAction(viewModel::pause),
        onResume = rememberHapticAction(viewModel::resume),
        onReset = rememberHapticAction(viewModel::reset),
        onSelectSchedule = rememberHapticAction(viewModel::selectSchedule),
    )
}
