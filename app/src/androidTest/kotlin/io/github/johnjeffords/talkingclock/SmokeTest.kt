package io.github.johnjeffords.talkingclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.junit.runners.model.Statement

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

    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(NotificationStateResetRule())
        .around(composeRule)

    private val app: TalkingClockApp
        get() = composeRule.activity.application as TalkingClockApp

    @Before
    fun resetBackgroundFeatureState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assumeTrue(
                ContextCompat.checkSelfPermission(
                    composeRule.activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED,
            )
        }
        composeRule.runOnUiThread {
            app.timerController.reset()
            app.stopwatchController.reset()
            app.speakingClockController.disarm()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !app.currentSettings.notificationPermissionAsked
        }
        // The application collector and the root SettingsViewModel collect
        // the same DataStore independently. Let the composed tree observe
        // the reset before starting a feature.
        composeRule.waitForIdle()
    }

    @Test
    fun launches_ticks_speaks_and_arms() {
        // Let the app settle (TTS init, settings load, first tick).
        composeRule.waitForIdle()

        // The Clock screen composed — the tap hint is one of its fixtures.
        // (The screen scrolls, so we assert existence, then scroll targets
        // into view before touching them — the CI emulators show the tall
        // no-engine card, which can push the bottom control off-screen.)
        composeRule.onNodeWithText("Tap the time to speak it").assertExists()

        // Tap the time (speaks if an engine exists, silently drops if not —
        // either way it must not crash).
        composeRule.onNodeWithText("Tap the time to speak it").performScrollTo().performClick()

        // Arm at 5 min via the menu.
        composeRule.onNodeWithText("Speak every").performScrollTo().performClick()
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

    @Test
    fun timer_first_start_explains_denial_and_keeps_running() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        composeRule.onNodeWithTag("bottom_tab_Timer").performClick()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()

        denyNotificationExplanation()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            app.timerController.state.value.snapshot.phase == TimerEngine.Phase.Running
        }
        composeRule.runOnUiThread { app.timerController.pause() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Resume").assertExists()
        composeRule.onNodeWithText(deniedBanner()).assertExists()
        assertPermissionExplanationPersisted()
    }

    @Test
    fun stopwatch_first_start_explains_denial_and_keeps_running() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        composeRule.onNodeWithTag("bottom_tab_Stopwatch").performClick()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()

        denyNotificationExplanation()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            app.stopwatchController.state.value.snapshot.phase == StopwatchEngine.Phase.Running
        }
        composeRule.runOnUiThread { app.stopwatchController.pause() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Resume").assertExists()
        composeRule.onNodeWithText(deniedBanner()).assertExists()
        assertPermissionExplanationPersisted()
    }

    private fun denyNotificationExplanation() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Not now").performClick()
    }

    private fun deniedBanner(): String =
        composeRule.activity.getString(R.string.notif_permission_denied_banner)

    private fun assertPermissionExplanationPersisted() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            app.currentSettings.notificationPermissionAsked
        }
        assertTrue(app.currentSettings.notificationPermissionAsked)
    }

    /** DataStore must be reset before the Activity takes its initial snapshot. */
    private class NotificationStateResetRule : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<TalkingClockApp>()
                    runBlocking {
                        app.settingsRepository.setNotificationPermissionAsked(false)
                    }
                    base.evaluate()
                }
            }
    }
}
