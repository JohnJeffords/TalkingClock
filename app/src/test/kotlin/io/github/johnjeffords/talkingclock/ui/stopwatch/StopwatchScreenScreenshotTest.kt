package io.github.johnjeffords.talkingclock.ui.stopwatch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Goldens for the Stopwatch screen (design frame 09), fixed inputs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class StopwatchScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stopwatch_running_with_laps_dark() {
        // Three laps so the fastest/slowest highlights engage: lap 2 fastest
        // (0:50), lap 1 slowest (1:50.3-ish values from the design frame).
        val laps = listOf(
            StopwatchEngine.Lap(1, Duration.ofMillis(110_300), Duration.ofMillis(110_300)),
            StopwatchEngine.Lap(2, Duration.ofMillis(161_100), Duration.ofMillis(50_800)),
            StopwatchEngine.Lap(3, Duration.ofMillis(277_200), Duration.ofMillis(116_100)),
        )
        capture(
            StopwatchUiState(
                phase = StopwatchEngine.Phase.Running,
                elapsed = Duration.ofMillis(277_200),
                laps = laps.asReversed().map { lap ->
                    LapRow(
                        lap = lap,
                        isFastest = lap.number == 2,
                        isSlowest = lap.number == 3,
                    )
                },
            ),
            "src/test/screenshots/stopwatch_running_dark.png",
        )
    }

    @Test
    fun stopwatch_idle_dark() {
        capture(StopwatchUiState(), "src/test/screenshots/stopwatch_idle_dark.png")
    }

    private fun capture(uiState: StopwatchUiState, outputPath: String) {
        composeRule.setContent {
            TalkingClockTheme(ThemeChoice.Dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    StopwatchScreen(
                        uiState = uiState,
                        onStartOrResume = {},
                        onPause = {},
                        onLap = {},
                        onReset = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(outputPath)
    }
}
