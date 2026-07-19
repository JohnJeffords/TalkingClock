package io.github.johnjeffords.talkingclock.ui.clock

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.speech.Utterance
import io.github.johnjeffords.talkingclock.ui.StartBackgroundFeature
import io.github.johnjeffords.talkingclock.ui.VOICE_ENGINE_FDROID_URL
import java.time.LocalTime

/**
 * Connects the Clock [ClockViewModel] and the app's speech engine to the
 * [ClockScreen] UI, reports background starts to the shared notification
 * permission coordinator, and owns the reduced-motion check.
 */
@Composable
fun ClockRoute(startBackgroundFeature: StartBackgroundFeature) {
    val viewModel: ClockViewModel = viewModel(factory = ClockViewModel.Factory)

    val context = LocalContext.current
    val speaker = (context.applicationContext as TalkingClockApp).speaker

    // Report the device's 12/24-hour preference (used when the time-format
    // setting says "follow system"). Reading it here (rather than inside the
    // ViewModel) keeps the ViewModel free of Android classes.
    LaunchedEffect(Unit) {
        viewModel.setSystemUses24Hour(DateFormat.is24HourFormat(context))
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val speakerState by speaker.state.collectAsStateWithLifecycle()

    // Honor the OS reduced-motion setting: a zero animator scale means the
    // user asked for no decorative motion, so the pulse/glow hold still.
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    ClockScreen(
        uiState = uiState,
        speakerState = speakerState,
        lastCustomInterval = viewModel.lastCustomInterval,
        animationsEnabled = animationsEnabled,
        onSpeakNow = {
            // Speak the time AT THE MOMENT OF THE TAP (not the displayed
            // tick, which can be up to a second old), in the user's chosen
            // speaking style — through the announcer, so an active voice
            // pack renders it when it can.
            val app = context.applicationContext as TalkingClockApp
            app.announcer.announce(
                Utterance.TimeAnnouncement(
                    time = LocalTime.now(),
                    style = app.currentSettings.speakingStyle,
                ),
            )
        },
        onSelectInterval = { interval ->
            if (interval != null) {
                startBackgroundFeature { viewModel.selectInterval(interval) }
            } else {
                viewModel.selectInterval(null)
            }
        },
        onInstallEngine = {
            // Open the recommended engine's F-Droid page in the browser. The
            // intent is handled by the browser app — this app itself still
            // makes no network requests and has no INTERNET permission.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, VOICE_ENGINE_FDROID_URL.toUri()),
            )
        },
    )
}
