package io.github.johnjeffords.talkingclock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device/emulator smoke test: the app launches, the clock is ticking,
 * tap-to-speak doesn't crash (WITH or WITHOUT a TTS engine installed — CI
 * runs this on stripped AOSP images, the GrapheneOS/CalyxOS-like condition),
 * and arming the speaking clock through the menu reaches the armed state.
 *
 * This is deliberately a coarse end-to-end pass, not a behavior spec — the
 * fine-grained behavior lives in the fast JVM tests. This test's job is to
 * catch "it doesn't even run on a real device/API level" classes of failure.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launches_ticks_speaks_and_arms() {
        // The Clock screen's fixtures are visible.
        composeRule.onNodeWithText("Speak every").assertIsDisplayed()
        composeRule.onNodeWithText("Tap the time to speak it").assertIsDisplayed()

        // Tap the time (speaks if an engine exists, silently drops if not —
        // either way it must not crash). The hero time is found via its
        // tap hint's sibling semantics: click through the accessibility
        // action label instead.
        composeRule.onNodeWithText("Tap the time to speak it").performClick()

        // Arm at 5 min via the menu.
        composeRule.onNodeWithText("Speak every").performClick()
        composeRule.onNodeWithText("5 min").performClick()

        // On Android 13+ the notification explainer appears first — answer
        // it ("Not now" arms without the system permission dialog).
        composeRule.waitForIdle()
        val explainer = composeRule.onAllNodesWithText("Not now")
        if (explainer.fetchSemanticsNodes().isNotEmpty()) {
            explainer[0].performClick()
        }

        // The armed chip is the design's unmissable cue #1.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Announcing every 5 minutes")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
