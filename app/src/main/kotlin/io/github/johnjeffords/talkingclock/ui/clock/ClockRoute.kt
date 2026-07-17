package io.github.johnjeffords.talkingclock.ui.clock

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

/**
 * Connects the Clock [ClockViewModel] to the [ClockScreen] UI. Keeping this
 * "wiring" composable separate from [ClockScreen] means the screen stays a
 * pure function of its inputs and can be screenshot-tested with a fake time,
 * while this route handles the live ViewModel and device settings.
 */
@Composable
fun ClockRoute() {
    val viewModel: ClockViewModel = viewModel(factory = ClockViewModel.Factory)

    // Match the device's 12/24-hour system setting. Reading it here (rather
    // than inside the ViewModel) keeps the ViewModel free of Android classes.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.setUse24Hour(DateFormat.is24HourFormat(context))
    }

    val readout by viewModel.readout.collectAsStateWithLifecycle()

    ClockScreen(
        readout = readout,
        onSpeakNow = { /* Speaking the time is wired to the TTS engine in M2. */ },
    )
}
