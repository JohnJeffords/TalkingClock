package io.github.johnjeffords.talkingclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.R

/**
 * Typography.
 *
 * Labels, titles, and body text use the device's default font (Roboto on
 * most Androids) via the standard Material 3 [Typography].
 *
 * Every NUMERIC readout — clock, timer, stopwatch, alarm times, durations —
 * must use a MONOSPACED face so digits occupy equal width and the layout
 * doesn't twitch as numbers change (a hard rule from the design handoff).
 * We use [FontFamily.Monospace], which maps to the system's built-in
 * monospace font. The optional main-clock style uses the small bundled
 * seven-segment subset below; other numeric tools keep the system face.
 */

// The base Material 3 type scale — inherits the system font.
val TalkingClockTypography = Typography()

/** Font family for all numeric time displays. See the note above. */
val NumericFontFamily: FontFamily = FontFamily.Monospace

/** OFL-licensed digits-and-colon font used only when the clock style requests it. */
val SevenSegmentFontFamily: FontFamily = FontFamily(
    Font(R.font.talking_clock_seven_segment),
)

/**
 * The huge clock readout on the home screen. Hours:minutes at 84sp; the
 * seconds are rendered smaller and muted by the caller. The slightly negative
 * letter spacing (-0.03em, i.e. 3% of the font size) tightens the mono digits
 * so they read as one number rather than a row of separate glyphs.
 */
val ClockHeroTextStyle = TextStyle(
    fontFamily = NumericFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 84.sp,
    letterSpacing = (-0.03).em,
)

/** The smaller, muted seconds shown beside the hero time. */
val ClockSecondsTextStyle = TextStyle(
    fontFamily = NumericFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 40.sp,
    letterSpacing = (-0.03).em,
)
