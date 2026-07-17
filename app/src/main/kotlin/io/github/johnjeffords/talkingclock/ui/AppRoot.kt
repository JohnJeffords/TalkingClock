package io.github.johnjeffords.talkingclock.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.ui.alarm.AlarmRoute
import io.github.johnjeffords.talkingclock.ui.clock.ClockRoute
import io.github.johnjeffords.talkingclock.ui.clock.ClockViewModel
import io.github.johnjeffords.talkingclock.ui.clock.NightstandScreen
import io.github.johnjeffords.talkingclock.ui.settings.AboutScreen
import io.github.johnjeffords.talkingclock.ui.settings.QuietHoursScreen
import io.github.johnjeffords.talkingclock.ui.settings.SettingsScreen
import io.github.johnjeffords.talkingclock.ui.settings.SettingsViewModel
import io.github.johnjeffords.talkingclock.ui.settings.SpeakingStyleScreen
import io.github.johnjeffords.talkingclock.ui.settings.VoiceScreen
import io.github.johnjeffords.talkingclock.ui.stopwatch.StopwatchRoute
import io.github.johnjeffords.talkingclock.ui.timer.TimerRoute
import kotlinx.coroutines.launch

/**
 * The app's navigation shell. Two layers:
 *
 *  1. A NavHost with the "home" destination (the three tools behind bottom
 *     tabs + the drawer) and the settings destinations (Settings and its
 *     sub-screens), each with a back-arrow top bar.
 *  2. Inside "home", plain tab state switches between Clock/Timer/Stopwatch
 *     — tabs are peers, not a back stack, so they don't belong in the
 *     NavHost (back should exit the app from any tab, not walk tab history).
 */

/** Navigation route names. */
private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val VOICE = "settings/voice"
    const val SPEAKING_STYLE = "settings/speaking-style"
    const val QUIET_HOURS = "settings/quiet-hours"
    const val ABOUT = "settings/about"
}

/** The bottom-navigation tools, in the design's order (frame 01's bar). */
enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    Clock(R.string.nav_clock, Icons.Outlined.AccessTime),
    Alarm(R.string.nav_alarm, Icons.Outlined.Alarm),
    Timer(R.string.nav_timer, Icons.Outlined.Timer),
    Stopwatch(R.string.nav_stopwatch, Icons.Outlined.HourglassEmpty),
}

@Composable
fun TalkingClockRoot() {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeShell(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }

        composable(Routes.SETTINGS) {
            SettingsScaffold(
                title = stringResource(R.string.settings_title),
                onBack = { navController.popBackStack() },
            ) { padding ->
                SettingsScreen(
                    settings = settings,
                    onSetTheme = settingsViewModel::setTheme,
                    onSetTimeFormat = settingsViewModel::setTimeFormat,
                    onSetShowSeconds = settingsViewModel::setShowSeconds,
                    onSetShowDate = settingsViewModel::setShowDate,
                    onSetAutoOff = settingsViewModel::setAutoOffMinutes,
                    onSetTimerSchedule = settingsViewModel::setTimerScheduleName,
                    onSetStopwatchSpeakElapsed = settingsViewModel::setStopwatchSpeakElapsed,
                    onSetStopwatchSpeakLaps = settingsViewModel::setStopwatchSpeakLaps,
                    onSetSpeechLead = settingsViewModel::setSpeechLeadMillis,
                    onOpenVoice = { navController.navigate(Routes.VOICE) },
                    onOpenSpeakingStyle = { navController.navigate(Routes.SPEAKING_STYLE) },
                    onOpenQuietHours = { navController.navigate(Routes.QUIET_HOURS) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    modifier = padding,
                )
            }
        }

        composable(Routes.VOICE) {
            val speaker = (context.applicationContext as TalkingClockApp).speaker
            val speakerState by speaker.state.collectAsStateWithLifecycle()
            val installedPacks by settingsViewModel.installedPacks.collectAsStateWithLifecycle()
            val importError by settingsViewModel.importError.collectAsStateWithLifecycle()

            // The system file picker (Storage Access Framework): the user
            // grants access to exactly one file — no storage permission.
            // .tcvoice is a plain zip, so we accept generic types too.
            val packPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(settingsViewModel::importVoicePack) }

            SettingsScaffold(
                title = stringResource(R.string.settings_voice),
                onBack = { navController.popBackStack() },
            ) { padding ->
                VoiceScreen(
                    speakerState = speakerState,
                    rate = settings.ttsRate,
                    pitch = settings.ttsPitch,
                    selectedPackId = settings.voicePackId,
                    installedPacks = installedPacks,
                    importError = importError,
                    onSetRate = settingsViewModel::setTtsRate,
                    onSetPitch = settingsViewModel::setTtsPitch,
                    onSelectPack = settingsViewModel::selectVoicePack,
                    onImportPack = {
                        packPicker.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*"),
                        )
                    },
                    onDeletePack = settingsViewModel::deleteVoicePack,
                    onDismissImportError = settingsViewModel::dismissImportError,
                    onTest = { settingsViewModel.previewSpeech() },
                    onInstallEngine = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, VOICE_ENGINE_FDROID_URL.toUri()),
                        )
                    },
                    onOpenSystemSpeechSettings = {
                        // The system Text-to-speech screen. Its action isn't a
                        // public constant, so fall back to the top-level Settings
                        // app on the rare device that doesn't expose it directly.
                        try {
                            context.startActivity(Intent(ACTION_TTS_SETTINGS))
                        } catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    modifier = padding,
                )
            }
        }

        composable(Routes.SPEAKING_STYLE) {
            SettingsScaffold(
                title = stringResource(R.string.settings_speaking_style),
                onBack = { navController.popBackStack() },
            ) { padding ->
                SpeakingStyleScreen(
                    selected = settings.speakingStyle,
                    onSelect = settingsViewModel::setSpeakingStyle,
                    onPreview = settingsViewModel::previewSpeech,
                    modifier = padding,
                )
            }
        }

        composable(Routes.QUIET_HOURS) {
            SettingsScaffold(
                title = stringResource(R.string.settings_quiet_hours),
                onBack = { navController.popBackStack() },
            ) { padding ->
                QuietHoursScreen(
                    enabled = settings.quietHoursEnabled,
                    fromMinutes = settings.quietFromMinutes,
                    untilMinutes = settings.quietUntilMinutes,
                    allowTimers = settings.quietAllowTimers,
                    onSetEnabled = settingsViewModel::setQuietHoursEnabled,
                    onSetFrom = settingsViewModel::setQuietFrom,
                    onSetUntil = settingsViewModel::setQuietUntil,
                    onSetAllowTimers = settingsViewModel::setQuietAllowTimers,
                    modifier = padding,
                )
            }
        }

        composable(Routes.ABOUT) {
            SettingsScaffold(
                title = stringResource(R.string.settings_about),
                onBack = { navController.popBackStack() },
            ) { padding ->
                AboutScreen(
                    onOpenSource = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
                    },
                    onReportIssue = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, ISSUES_URL.toUri()))
                    },
                    modifier = padding,
                )
            }
        }
    }
}

/** The tools shell: drawer + top bar + bottom tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShell(onOpenSettings: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Clock) }
    var nightstand by rememberSaveable { mutableStateOf(false) }

    // Nightstand mode replaces everything — no chrome in a dark bedroom.
    if (nightstand) {
        val clockViewModel: ClockViewModel = viewModel(factory = ClockViewModel.Factory)
        val uiState by clockViewModel.uiState.collectAsStateWithLifecycle()
        NightstandScreen(
            readout = uiState.readout,
            onExit = { nightstand = false },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                selectedTab = selectedTab,
                onSelectTab = { tab ->
                    selectedTab = tab
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                // No title text — the design's top bar is just the menu
                // button and (on the Clock tab) the nightstand toggle.
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.clock_open_menu),
                            )
                        }
                    },
                    actions = {
                        if (selectedTab == HomeTab.Clock) {
                            IconButton(onClick = { nightstand = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Bedtime,
                                    contentDescription = "Nightstand mode",
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    HomeTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (selectedTab) {
                    HomeTab.Clock -> ClockRoute()
                    HomeTab.Alarm -> AlarmRoute()
                    HomeTab.Timer -> TimerRoute()
                    HomeTab.Stopwatch -> StopwatchRoute()
                }
            }
        }
    }
}

/** Top bar + back arrow wrapper shared by every settings destination.
 *  Passes the content a Modifier carrying the scaffold padding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

private const val REPO_URL = "https://github.com/JohnJeffords/TalkingClock"
private const val ISSUES_URL = "https://github.com/JohnJeffords/TalkingClock/issues"

/**
 * Android's system Text-to-speech settings screen. There's no public
 * constant for this action (it lives in the Settings app), but this string
 * has been stable across Android versions; we fall back to the top-level
 * Settings if a device doesn't expose it.
 */
private const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

/**
 * F-Droid page for the recommended FOSS speech engine. SherpaTTS is a proper
 * system TTS engine (GPL-3.0, on F-Droid) with natural-sounding neural voices
 * — a big step up from RHVoice's robotic quality. It fetches a voice model
 * once on first use, then runs fully offline. (Users who want the tiniest
 * option can still pick eSpeak NG from F-Droid.)
 */
const val VOICE_ENGINE_FDROID_URL =
    "https://f-droid.org/packages/org.woheller69.ttsengine/"
