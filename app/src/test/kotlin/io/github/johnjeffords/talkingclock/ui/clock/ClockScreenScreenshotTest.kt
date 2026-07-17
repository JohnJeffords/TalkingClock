package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.domain.time.ClockReadout
import io.github.johnjeffords.talkingclock.speech.SpeakerState
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
 * Screenshot ("golden image") tests for the Clock screen. Roborazzi renders
 * the real composable on the plain JVM — no emulator — and compares it to a
 * committed reference PNG, so any accidental visual change (a color, a size,
 * a layout shift) shows up as a failing test with a side-by-side diff.
 *
 * References are (re)generated with `-Proborazzi.test.record=true` and
 * eyeballed against the design handoff frames (01-clock-off, 02-speaking);
 * plain `test` then verifies pixel-for-pixel. See docs/TESTING_AND_CI.md.
 *
 * All inputs are FIXED (fake readout, fixed durations, animations off) so
 * the image is byte-for-byte deterministic — exactly why ClockScreen is a
 * pure function of its inputs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ClockScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Matches the content of design frame 01-clock-off exactly (10:24 and
    // 53 seconds on Tuesday July 15) so golden-vs-design is like-for-like.
    private val sampleReadout = ClockReadout(
        time = "10:24",
        seconds = "53",
        meridiem = "AM",
        date = "Tuesday · July 15",
    )

    private val offState = ClockUiState(readout = sampleReadout)

    // Armed at 5 min, next announcement in 3:12, auto-off in 58 min —
    // the design frame 02 content.
    private val armedState = ClockUiState(
        readout = sampleReadout,
        armedInterval = SpeakInterval(5 * 60),
        nextIn = Duration.ofMinutes(3).plusSeconds(12),
        autoOffIn = Duration.ofMinutes(58),
    )

    @Test
    fun clockScreen_off_dark() {
        capture(offState, ThemeChoice.Dark, "src/test/screenshots/clock_off_dark.png")
    }

    @Test
    fun clockScreen_off_light() {
        capture(offState, ThemeChoice.Light, "src/test/screenshots/clock_off_light.png")
    }

    @Test
    fun clockScreen_speaking_dark() {
        capture(armedState, ThemeChoice.Dark, "src/test/screenshots/clock_speaking_dark.png")
    }

    @Test
    fun clockScreen_speaking_light() {
        capture(armedState, ThemeChoice.Light, "src/test/screenshots/clock_speaking_light.png")
    }

    @Test
    fun clockScreen_noEngine_dark() {
        // The GrapheneOS/CalyxOS out-of-box state: warning card visible.
        capture(
            offState,
            ThemeChoice.Dark,
            "src/test/screenshots/clock_no_engine_dark.png",
            speakerState = SpeakerState.NoEngine,
        )
    }

    private fun capture(
        uiState: ClockUiState,
        theme: ThemeChoice,
        outputPath: String,
        speakerState: SpeakerState = SpeakerState.Ready,
    ) {
        composeRule.setContent {
            TalkingClockTheme(theme) {
                // Paint the BACKGROUND color behind the screen, matching what
                // Scaffold does in the real app (Surface's default fill is
                // `surface`, against which the cards would be invisible).
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ClockScreen(
                        uiState = uiState,
                        speakerState = speakerState,
                        lastCustomInterval = null,
                        animationsEnabled = false, // deterministic pixels
                        onSpeakNow = {},
                        onSelectInterval = {},
                        onInstallEngine = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(outputPath)
    }
}
