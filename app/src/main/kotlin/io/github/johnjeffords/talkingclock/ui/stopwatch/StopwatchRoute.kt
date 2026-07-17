package io.github.johnjeffords.talkingclock.ui.stopwatch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Wires [StopwatchViewModel] to [StopwatchScreen]. Thin by design. */
@Composable
fun StopwatchRoute() {
    val viewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StopwatchScreen(
        uiState = uiState,
        onStartOrResume = viewModel::startOrResume,
        onPause = viewModel::pause,
        onLap = viewModel::lap,
        onReset = viewModel::reset,
    )
}
