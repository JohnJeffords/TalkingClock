package io.github.johnjeffords.talkingclock.ui.clock

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import java.time.LocalTime

/**
 * Connects the Clock [ClockViewModel] and the app's [io.github.johnjeffords
 * .talkingclock.speech.Speaker] to the [ClockScreen] UI. Keeping this
 * "wiring" composable separate means the screen stays a pure function of its
 * inputs (screenshot-testable with fake state) while this route handles the
 * live ViewModel, device settings, and the speech engine.
 */
@Composable
fun ClockRoute() {
    val viewModel: ClockViewModel = viewModel(factory = ClockViewModel.Factory)

    val context = LocalContext.current
    val speaker = (context.applicationContext as TalkingClockApp).speaker

    // Match the device's 12/24-hour system setting. Reading it here (rather
    // than inside the ViewModel) keeps the ViewModel free of Android classes.
    LaunchedEffect(Unit) {
        viewModel.setUse24Hour(DateFormat.is24HourFormat(context))
    }

    val readout by viewModel.readout.collectAsStateWithLifecycle()
    val speakerState by speaker.state.collectAsStateWithLifecycle()

    ClockScreen(
        readout = readout,
        speakerState = speakerState,
        onSpeakNow = {
            // Speak the time AT THE MOMENT OF THE TAP (not the displayed
            // tick, which can be up to a second old). Speaking style is
            // fixed to Conversational until the Settings screen (M6) lets
            // the user choose.
            speaker.speak(
                Phrasebook.timeAnnouncement(LocalTime.now(), SpeakingStyle.Conversational),
            )
        },
        onInstallEngine = {
            // Open RHVoice's F-Droid page in the browser. The intent is
            // handled by the browser app — this app itself still makes no
            // network requests and has no INTERNET permission.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, RHVOICE_FDROID_URL.toUri()),
            )
        },
    )
}

/** F-Droid page for RHVoice, the friendliest FOSS TTS engine to recommend. */
private const val RHVOICE_FDROID_URL =
    "https://f-droid.org/packages/com.github.olga_yakovleva.rhvoice.android/"
