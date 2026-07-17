package io.github.johnjeffords.talkingclock.ui.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Wires [TimerViewModel] to [TimerScreen]. Thin by design — the screen is a
 * pure function of state (screenshot-testable), the ViewModel owns the
 * keypad logic, the controller owns the timer itself.
 */
@Composable
fun TimerRoute() {
    val viewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TimerScreen(
        uiState = uiState,
        onDigit = viewModel::typeDigit,
        onDoubleZero = viewModel::typeDoubleZero,
        onDelete = viewModel::deleteDigit,
        onStart = viewModel::start,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onReset = viewModel::reset,
        onSelectSchedule = viewModel::selectSchedule,
    )
}
