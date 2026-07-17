package io.github.johnjeffords.talkingclock.ui.clock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.speech.Utterance
import java.time.LocalTime

/**
 * Connects the Clock [ClockViewModel] and the app's speech engine to the
 * [ClockScreen] UI, and owns the two purely-Android concerns the screen
 * can't: the POST_NOTIFICATIONS request flow (explainer sheet → system
 * dialog → arm) and the reduced-motion check.
 */
@Composable
fun ClockRoute() {
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

    // --- POST_NOTIFICATIONS flow -------------------------------------------
    // Arming can outlive the screen, so its status notification matters.
    // First arm on Android 13+ without the permission: show the honest
    // explainer sheet, then (only if the user opts in) the system dialog.
    // EITHER answer still arms the clock — the permission gates visibility,
    // never function.
    var pendingInterval by remember { mutableStateOf<SpeakInterval?>(null) }
    var explainerShown by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Granted or denied — proceed identically (see above).
        pendingInterval?.let(viewModel::selectInterval)
        pendingInterval = null
    }

    fun needsNotificationAsk(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !explainerShown &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

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
            if (interval != null && needsNotificationAsk()) {
                pendingInterval = interval // opens the explainer sheet below
            } else {
                viewModel.selectInterval(interval)
            }
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

    pendingInterval?.let {
        NotificationExplainerSheet(
            onAllow = {
                explainerShown = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDeny = {
                explainerShown = true
                pendingInterval?.let(viewModel::selectInterval)
                pendingInterval = null
            },
        )
    }
}

/** F-Droid page for RHVoice, the friendliest FOSS TTS engine to recommend. */
private const val RHVOICE_FDROID_URL =
    "https://f-droid.org/packages/com.github.olga_yakovleva.rhvoice.android/"
