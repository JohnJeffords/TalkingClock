package io.github.johnjeffords.talkingclock.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's color system, taken verbatim from the design handoff
 * (docs/DECISIONS.md D-019). The accent is a warm amber used as Material 3's
 * `primary`; blue/green/red are reserved for info/success/error only.
 *
 * There are three schemes — DARK (the default look), LIGHT, and AMOLED (dark
 * with a true-black background for OLED screens). Material 3 restyles every
 * screen from whichever scheme is active, so screens are written once and the
 * theme does the rest.
 *
 * Colors not part of Material 3 (success green, info blue, the nightstand
 * red) live in [TcExtraColors] because they need light/dark variants too.
 */

// --- The amber accent ---
// Bright amber reads beautifully on the DARK backgrounds, but as a text /
// icon color on the LIGHT theme's near-white background it fails WCAG
// contrast badly (~1.5:1 — you can barely see it). `primary` is used as a
// foreground color in lots of places (section headers, the "speaking" chip
// text, "Time's up", alarm "speaks" lines…), so light mode needs a DEEPER
// amber that stays legible. Buttons then pair that deep amber fill with
// white text, which is also high-contrast. Dark/AMOLED keep the bright
// amber. (Accessibility is a hard requirement — docs/DESIGN.md — and it
// wins over matching the exact hue in light mode.)
private val Amber = Color(0xFFFFC24B)          // dark themes
private val OnAmber = Color(0xFF2A1D00)         // dark text on bright amber
private val AmberLight = Color(0xFF8A5A00)      // light theme: ~5.4:1 on the light bg
private val OnAmberLight = Color(0xFFFFFFFF)    // white text on deep amber

/**
 * Foreground color for content sitting on the FIXED bright-amber fill — the
 * "Announcing" status chip's `#FFC24B → #FFB020` gradient, which is bright in
 * every theme. Such content must NOT use the theme's `onPrimary`: in light
 * mode that's now white (it pairs with the deep-amber button fill), which
 * would be illegible on bright amber. This dark brown pairs with bright amber
 * in all three themes.
 */
val OnBrightAmber = Color(0xFF2A1D00)

val TalkingClockDarkColors: ColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = Color(0xFF4A3600),   // the "speaking" control fill, nav pill, selected rows
    onPrimaryContainer = Color(0xFFFFDE9E),
    background = Color(0xFF0F0E11),
    onBackground = Color(0xFFF3F0F5),
    surface = Color(0xFF1B191E),             // raised cards
    onSurface = Color(0xFFF3F0F5),
    surfaceVariant = Color(0xFF221F26),      // menus / dialogs
    onSurfaceVariant = Color(0xFFB7B2BD),    // secondary text
    outline = Color(0xFF322E39),             // dividers
    outlineVariant = Color(0xFF26232A),
    error = Color(0xFFFF6B5E),               // overtime, missing coverage
    onError = OnAmber,
)

val TalkingClockLightColors: ColorScheme = lightColorScheme(
    primary = AmberLight,                    // deep amber — legible as text on white
    onPrimary = OnAmberLight,                // white on the deep-amber button fill
    primaryContainer = Color(0xFFFFE7B3),    // pale amber fill (armed control, chips)
    onPrimaryContainer = Color(0xFF4A3600),  // dark text on the pale container
    background = Color(0xFFF5F3F1),
    onBackground = Color(0xFF1D1B1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B1E),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5C5760),
    outline = Color(0xFFE0DBE6),
    outlineVariant = Color(0xFFE2DDE4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

/**
 * AMOLED = the dark scheme with a pure-black background and near-black
 * surfaces, so unlit OLED pixels save power. Everything else matches DARK.
 */
val TalkingClockAmoledColors: ColorScheme = TalkingClockDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF121212),
)
