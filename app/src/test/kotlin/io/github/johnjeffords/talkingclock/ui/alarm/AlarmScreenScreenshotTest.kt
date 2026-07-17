package io.github.johnjeffords.talkingclock.ui.alarm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Duration

/** Goldens for the alarm screens (design frames 23/24/25), fixed inputs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AlarmScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val weekdayAlarm = Alarm(
        id = "a1",
        hour = 6,
        minute = 45,
        days = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        label = "Work",
        handoffIntervalSeconds = 5 * 60,
    )
    private val onceAlarm = Alarm(
        id = "a2",
        hour = 14,
        minute = 30,
        label = "Meds",
        enabled = false,
        handoffIntervalSeconds = null,
    )

    @Test
    fun alarm_list_dark() {
        capture("src/test/screenshots/alarm_list_dark.png") {
            AlarmListScreen(
                uiState = AlarmViewModel.UiState(
                    alarms = listOf(weekdayAlarm, onceAlarm),
                    nextAlarmIn = Duration.ofHours(9).plusMinutes(24),
                ),
                onAdd = {},
                onEdit = {},
                onSetEnabled = { _, _ -> },
            )
        }
    }

    @Test
    fun alarm_edit_dark() {
        capture("src/test/screenshots/alarm_edit_dark.png") {
            AlarmEditScreen(initial = weekdayAlarm, onSave = {}, onDelete = {})
        }
    }

    @Test
    fun alarm_ringing_dark() {
        capture("src/test/screenshots/alarm_ringing_dark.png") {
            AlarmRingingScreen(alarm = weekdayAlarm, onSnooze = {}, onDismiss = {})
        }
    }

    private fun capture(
        outputPath: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage(outputPath)
    }
}
