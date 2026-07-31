package io.github.johnjeffords.talkingclock.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens for the Settings screens (design frames 10/21), fixed inputs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class SettingsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings_main_dark() {
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SettingsScreen(
                        settings = SettingsRepository.Settings(),
                        onSetTheme = {},
                        onSetTimeFormat = {},
                        onSetClockStyle = {},
                        onSetShowSeconds = {},
                        onSetShowDate = {},
                        onSetAutoOff = {},
                        onSetTimerSchedule = {},
                        onSetStopwatchSpeakElapsed = {},
                        onSetStopwatchSpeakLaps = {},
                        onSetSpeechLead = {},
                        onSetHapticFeedback = {},
                        onOpenVoice = {},
                        onOpenSpeakingStyle = {},
                        onOpenQuietHours = {},
                        onOpenAbout = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_main_dark.png")
    }

    @Test
    fun settings_main_light() {
        // Light theme specifically: the amber section headers ("Clock",
        // "Voice"…) and chevrons must be legible on the near-white background
        // (the contrast bug). Guards the deep-amber light-theme primary.
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Light) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SettingsScreen(
                        settings = SettingsRepository.Settings(),
                        onSetTheme = {},
                        onSetTimeFormat = {},
                        onSetClockStyle = {},
                        onSetShowSeconds = {},
                        onSetShowDate = {},
                        onSetAutoOff = {},
                        onSetTimerSchedule = {},
                        onSetStopwatchSpeakElapsed = {},
                        onSetStopwatchSpeakLaps = {},
                        onSetSpeechLead = {},
                        onSetHapticFeedback = {},
                        onOpenVoice = {},
                        onOpenSpeakingStyle = {},
                        onOpenQuietHours = {},
                        onOpenAbout = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_main_light.png")
    }

    @Test
    fun quiet_hours_dark() {
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    QuietHoursScreen(
                        enabled = true,
                        fromMinutes = 22 * 60,
                        untilMinutes = 7 * 60,
                        allowTimers = true,
                        onSetEnabled = {},
                        onSetFrom = {},
                        onSetUntil = {},
                        onSetAllowTimers = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/quiet_hours_dark.png")
    }

    @Test
    fun speaking_style_dark() {
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SpeakingStyleScreen(
                        selected = io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle.Conversational,
                        onSelect = {},
                        onPreview = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/speaking_style_dark.png")
    }
}
