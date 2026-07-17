package io.github.johnjeffords.talkingclock.ui.timer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Goldens for the Timer screen's four looks (design frames 05/06/18/07b),
 * each from a fixed [TimerUiState] so the pixels are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class TimerScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timer_idle_dark() {
        capture(
            TimerUiState(
                phase = TimerEngine.Phase.Idle,
                typedDigits = "1500",
                typedDuration = Duration.ofMinutes(15),
                canStart = true,
                schedule = AnnouncementSchedule.GAME,
            ),
            "src/test/screenshots/timer_idle_dark.png",
        )
    }

    @Test
    fun timer_running_dark() {
        capture(
            TimerUiState(
                phase = TimerEngine.Phase.Running,
                remaining = Duration.ofMinutes(7).plusSeconds(23),
                progress = 7.4f / 15f,
                schedule = AnnouncementSchedule.GAME,
                nextMarkAt = Duration.ofMinutes(5),
            ),
            "src/test/screenshots/timer_running_dark.png",
        )
    }

    @Test
    fun timer_paused_dark() {
        capture(
            TimerUiState(
                phase = TimerEngine.Phase.Paused,
                remaining = Duration.ofMinutes(7).plusSeconds(23),
                progress = 7.4f / 15f,
                schedule = AnnouncementSchedule.GAME,
            ),
            "src/test/screenshots/timer_paused_dark.png",
        )
    }

    @Test
    fun timer_overtime_dark() {
        capture(
            TimerUiState(
                phase = TimerEngine.Phase.Finished,
                overtime = Duration.ofSeconds(37),
                progress = 0f,
            ),
            "src/test/screenshots/timer_overtime_dark.png",
        )
    }

    private fun capture(uiState: TimerUiState, outputPath: String) {
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TimerScreen(
                        uiState = uiState,
                        onDigit = {},
                        onDoubleZero = {},
                        onDelete = {},
                        onStart = {},
                        onPause = {},
                        onResume = {},
                        onReset = {},
                        onSelectSchedule = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(outputPath)
    }
}
