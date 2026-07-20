package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.domain.time.ClockReadout
import io.github.johnjeffords.talkingclock.ui.theme.NumericFontFamily
import io.github.johnjeffords.talkingclock.ui.theme.SevenSegmentFontFamily

/**
 * Nightstand mode (design frame 04): a dark-bedroom presentation of the
 * clock — pure black, low-glare red digits, no chrome at all. Tap anywhere
 * to leave. Three details matter here:
 *
 *  - the screen STAYS AWAKE while this is showing (that's the point of a
 *    nightstand clock) via the view's keepScreenOn flag — no permission
 *    needed, and the flag is dropped the moment the mode closes;
 *  - the digits DRIFT a few pixels as the minutes pass ("burn-in shift"),
 *    so OLED panels don't get the same pixels lit all night;
 *  - the red is the design's #7A241B — dim enough not to light the room.
 */
@Composable
fun NightstandScreen(
    readout: ClockReadout?,
    clockStyle: SettingsRepository.ClockStyle,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the screen on for exactly as long as nightstand mode is visible.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Burn-in shift: a slow square-ish orbit driven by the minute value, a
    // few dp per step. Deterministic (no randomness — see CODE_STYLE on
    // testability), imperceptible in the moment, meaningful over a night.
    val minute = readout?.time?.takeLast(2)?.toIntOrNull() ?: 0
    val xShift = ((minute % 8) - 4) * 3
    val yShift = (((minute / 8) % 8) - 4) * 6

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                // No ripple: a flash of light defeats a night clock.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Exit nightstand mode",
                onClick = onExit,
            ),
    ) {
        Text(
            text = readout?.time ?: "",
            fontFamily = if (clockStyle == SettingsRepository.ClockStyle.SevenSegment) {
                SevenSegmentFontFamily
            } else {
                NumericFontFamily
            },
            fontWeight = FontWeight.Medium,
            fontSize = 104.sp,
            color = NightRed,
            modifier = Modifier.offset(x = xShift.dp, y = yShift.dp),
        )
    }
}

/** The design's night-mode red: visible in the dark, dim enough to sleep by. */
private val NightRed = Color(0xFF7A241B)
