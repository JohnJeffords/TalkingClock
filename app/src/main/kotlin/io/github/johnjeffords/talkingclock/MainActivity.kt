package io.github.johnjeffords.talkingclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnjeffords.talkingclock.ui.TalkingClockRoot
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.map

/**
 * The app's single Activity. It just hosts the Compose UI — all screens live
 * inside [TalkingClockRoot]. A single-activity, all-Compose structure is the
 * modern Android default: fewer moving parts, and navigation is handled in
 * Compose rather than by juggling Activities/Fragments.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars so the app background reaches the screen
        // edges (a modern, immersive look). Compose handles insets for us.
        enableEdgeToEdge()

        val app = application as TalkingClockApp
        setContent {
            // The theme setting drives the whole tree; changing it in
            // Settings restyles every screen instantly.
            val theme by app.settingsRepository.settings
                .map { it.theme }
                .collectAsStateWithLifecycle(initialValue = ThemeChoice.System)
            TalkingClockTheme(theme) {
                TalkingClockRoot()
            }
        }
    }
}
