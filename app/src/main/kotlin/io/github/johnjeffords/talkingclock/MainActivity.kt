package io.github.johnjeffords.talkingclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.johnjeffords.talkingclock.ui.TalkingClockRoot
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme

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
        setContent {
            TalkingClockTheme {
                TalkingClockRoot()
            }
        }
    }
}
