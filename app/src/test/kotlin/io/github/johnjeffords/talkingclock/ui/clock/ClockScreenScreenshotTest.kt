package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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

/**
 * Screenshot ("golden image") tests for the Clock screen. Roborazzi renders
 * the real composable on the plain JVM — no emulator — and compares it to a
 * committed reference PNG, so any accidental visual change (a color, a size,
 * a layout shift) shows up as a failing test with a side-by-side diff.
 *
 * The reference PNGs are generated once by running with `-Proborazzi.test.record=true`
 * and then eyeballed against the design handoff screenshots (frame
 * 01-clock-off in dark and light). After that, plain `test` verifies the UI
 * still matches. See docs/TESTING_AND_CI.md.
 *
 * We feed a FIXED [ClockReadout] rather than the live clock so the image is
 * byte-for-byte deterministic every run — that's exactly why ClockScreen is a
 * pure function of its inputs.
 *
 * The device qualifier pins a 412dp-wide phone (the design's canvas) at a
 * fixed density so goldens are stable across machines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ClockScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Matches the content of design frame 01-clock-off exactly (10:24 and
    // 53 seconds on Tuesday July 15), so comparing golden to design frame
    // is a like-for-like eyeball.
    private val sampleReadout = ClockReadout(
        time = "10:24",
        seconds = "53",
        meridiem = "AM",
        date = "Tuesday · July 15",
    )

    @Test
    fun clockScreen_off_dark() {
        captureClockScreen(ThemeChoice.Dark, "src/test/screenshots/clock_off_dark.png")
    }

    @Test
    fun clockScreen_off_light() {
        captureClockScreen(ThemeChoice.Light, "src/test/screenshots/clock_off_light.png")
    }

    @Test
    fun clockScreen_noEngine_dark() {
        // The GrapheneOS/CalyxOS out-of-box state: warning card visible.
        captureClockScreen(
            ThemeChoice.Dark,
            "src/test/screenshots/clock_no_engine_dark.png",
            speakerState = SpeakerState.NoEngine,
        )
    }

    private fun captureClockScreen(
        theme: ThemeChoice,
        outputPath: String,
        speakerState: SpeakerState = SpeakerState.Ready,
    ) {
        composeRule.setContent {
            TalkingClockTheme(theme) {
                // Paint the BACKGROUND color behind the screen, matching what
                // Scaffold does in the real app. (Surface's default fill is
                // the `surface` color, which made the card invisible in the
                // first golden — the card only contrasts against background.)
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ClockScreen(
                        readout = sampleReadout,
                        speakerState = speakerState,
                        onSpeakNow = {},
                        onInstallEngine = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(outputPath)
    }
}
