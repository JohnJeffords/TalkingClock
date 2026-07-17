package io.github.johnjeffords.talkingclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The user's theme preference. Stored in settings later (M6); for now the
 * default (`System`) follows the device's light/dark setting.
 */
enum class ThemeChoice {
    /** Follow the device's light/dark system setting. */
    System,
    Light,
    Dark,

    /** Dark, but with a true-black background for OLED screens. */
    Amoled,
}

/**
 * Wraps the whole app in the Talking Clock color system and type scale.
 * Every screen is a child of this, so switching [choice] (or the device's
 * dark-mode setting) restyles the entire app at once.
 *
 * @param choice which color scheme to use; `System` defers to the device.
 * @param content the app UI to theme.
 */
@Composable
fun TalkingClockTheme(
    choice: ThemeChoice = ThemeChoice.System,
    content: @Composable () -> Unit,
) {
    // Resolve `System` down to an actual light-or-dark choice using the
    // device setting, then map the choice to a concrete color scheme.
    val useDark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark, ThemeChoice.Amoled -> true
    }
    val colorScheme = when {
        choice == ThemeChoice.Amoled -> TalkingClockAmoledColors
        useDark -> TalkingClockDarkColors
        else -> TalkingClockLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TalkingClockTypography,
        content = content,
    )
}
