package io.github.johnjeffords.talkingclock.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

private val LocalHapticFeedbackEnabled = staticCompositionLocalOf { true }

@Composable
fun ProvideHapticFeedback(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHapticFeedbackEnabled provides enabled, content = content)
}

/** Add the app's short, system-respecting tap feedback to an action. */
@Composable
fun rememberHapticAction(action: () -> Unit): () -> Unit {
    val currentAction by rememberUpdatedState(action)
    val view = LocalView.current
    val enabled = LocalHapticFeedbackEnabled.current
    return remember(view, enabled) {
        {
            if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentAction()
        }
    }
}

/** Argument-carrying counterpart used by keypad, picker, and list actions. */
@Composable
fun <T> rememberHapticAction(action: (T) -> Unit): (T) -> Unit {
    val currentAction by rememberUpdatedState(action)
    val view = LocalView.current
    val enabled = LocalHapticFeedbackEnabled.current
    return remember(view, enabled) {
        { value ->
            if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentAction(value)
        }
    }
}

@Composable
fun <T, U> rememberHapticAction(action: (T, U) -> Unit): (T, U) -> Unit {
    val currentAction by rememberUpdatedState(action)
    val view = LocalView.current
    val enabled = LocalHapticFeedbackEnabled.current
    return remember(view, enabled) {
        { first, second ->
            if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentAction(first, second)
        }
    }
}
