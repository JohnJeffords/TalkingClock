package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.time.ClockReadout
import io.github.johnjeffords.talkingclock.ui.theme.ClockHeroTextStyle
import io.github.johnjeffords.talkingclock.ui.theme.ClockSecondsTextStyle

/**
 * The Clock home screen's body (the top bar and bottom nav are drawn by the
 * app shell in AppRoot). Layout follows design frame `01-clock-off`:
 * date line at the top, the huge tappable time centered with a small
 * "tap to speak" hint under it, and the "Speak every" card pinned near the
 * bottom with a status caption beneath it.
 *
 * This composable is intentionally "dumb": it only draws the [readout] it's
 * given and reports taps back through callbacks. All the time logic lives in
 * ClockViewModel — which is what lets us screenshot-test this screen with a
 * fixed, fake time (see ClockScreenScreenshotTest).
 *
 * Note the hero shows NO AM/PM, matching the design (like a bedside clock);
 * the meridiem still goes into the accessibility label and the spoken text,
 * where the ambiguity would actually matter.
 *
 * @param readout the formatted time to display, or null before the first tick.
 * @param onSpeakNow invoked when the user taps the time to hear it spoken.
 */
@Composable
fun ClockScreen(
    readout: ClockReadout?,
    onSpeakNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))

        // Date line — regular (non-mono) font, muted color, top of the screen.
        Text(
            text = readout?.date.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Push the time to the vertical center of the remaining space.
        Spacer(Modifier.weight(1f))

        // The hero time. One accessibility label covers the whole row so
        // TalkBack reads "2:23 and 7 seconds PM" as a unit — and this text is
        // deliberately NOT a live region: announcing every second would be
        // unbearable; users tap to hear the time (see docs/DESIGN.md).
        val spokenLabel = buildTimeContentDescription(readout)
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .semantics { contentDescription = spokenLabel }
                .clickable(onClickLabel = "Speak the time now", onClick = onSpeakNow),
        ) {
            Text(
                text = readout?.time ?: "",
                style = ClockHeroTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Seconds — smaller, muted, riding the same baseline area.
            readout?.seconds?.let { seconds ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = seconds,
                    style = ClockSecondsTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Discoverability hint: small speaker glyph + "Tap the time to speak it".
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.clock_tap_to_speak),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        // The "Speak every" control (OFF state) near the bottom, then the
        // status caption. The armed state, dropdown, and countdown arrive
        // with the speaking-clock milestone (M3).
        SpeakEveryControlOff()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.clock_no_announcements),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The resting/OFF state of the "Speak every" control: a rounded card with a
 * muted volume-off glyph, a small "Speak every" label over a bold "Off"
 * value, and a chevron hinting it opens a menu (design frame 01).
 */
@Composable
private fun SpeakEveryControlOff() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            // Volume OFF while no interval is armed; becomes an active/amber
            // treatment in the armed state (M3).
            Icon(
                imageVector = Icons.Outlined.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.clock_speak_every),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.clock_interval_off),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Builds a natural-language time label for screen readers, e.g.
 * "2:23 and 7 seconds PM". The meridiem is included HERE even though the
 * visual hero omits it — a screen-reader user can't glance at the sky, so
 * the ambiguity matters more. Falls back to empty before the first tick.
 */
private fun buildTimeContentDescription(readout: ClockReadout?): String {
    if (readout == null) return ""
    val secondsPart = readout.seconds?.let { " and ${it.toInt()} seconds" } ?: ""
    val meridiemPart = readout.meridiem?.let { " $it" } ?: ""
    return "${readout.time}$secondsPart$meridiemPart"
}
