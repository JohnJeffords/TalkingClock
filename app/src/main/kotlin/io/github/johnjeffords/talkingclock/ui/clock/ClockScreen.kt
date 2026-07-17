package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.domain.time.ClockReadout
import io.github.johnjeffords.talkingclock.speech.SpeakerState
import io.github.johnjeffords.talkingclock.ui.components.NoSpeechEngineCard
import io.github.johnjeffords.talkingclock.ui.theme.ClockHeroTextStyle
import io.github.johnjeffords.talkingclock.ui.theme.ClockSecondsTextStyle
import java.time.Duration

/**
 * The Clock home screen's body (the top bar and bottom nav are drawn by the
 * app shell in AppRoot). Layout follows design frames 01 (off) and 02
 * (announcing): date at the top — with the amber [AnnouncingChip] under it
 * while armed — the huge tappable time centered, and the [SpeakEveryControl]
 * over a status line that is either "No announcements scheduled" or the live
 * "Next in m:ss" countdown + auto-off pill.
 *
 * The content is vertically CENTERED when it fits and SCROLLS when it
 * doesn't: `heightIn(min = maxHeight)` makes the column at least a screen
 * tall so Arrangement.Center can center a short layout, and when the content
 * is taller than a screen — the no-engine card is showing, or the system
 * font is scaled up (the design requires supporting 200%) — the column grows
 * past the viewport and the scroll keeps every part reachable. A plain fixed
 * Column clipped the bottom control off-screen on short displays.
 *
 * This composable is intentionally "dumb": it draws [uiState] and reports
 * intents through callbacks — which is what lets us screenshot-test every
 * state (off, armed, no-engine) with fixed fake data.
 *
 * The hero shows NO AM/PM, matching the design (like a bedside clock); the
 * meridiem still goes into the accessibility label and the spoken text,
 * where the ambiguity actually matters.
 */
@Composable
fun ClockScreen(
    uiState: ClockUiState,
    speakerState: SpeakerState,
    lastCustomInterval: SpeakInterval?,
    animationsEnabled: Boolean,
    onSpeakNow: () -> Unit,
    onSelectInterval: (SpeakInterval?) -> Unit,
    onInstallEngine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val minColumnHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minColumnHeight)
                .padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClockContent(
                uiState = uiState,
                speakerState = speakerState,
                lastCustomInterval = lastCustomInterval,
                animationsEnabled = animationsEnabled,
                onSpeakNow = onSpeakNow,
                onSelectInterval = onSelectInterval,
                onInstallEngine = onInstallEngine,
            )
        }
    }
}

/**
 * The scrolling content of the Clock screen, extracted so the scroll/center
 * wrapper above stays readable. A [ColumnScope] extension so it renders as
 * direct children of the centered, scrollable Column.
 */
@Composable
private fun ColumnScope.ClockContent(
    uiState: ClockUiState,
    speakerState: SpeakerState,
    lastCustomInterval: SpeakInterval?,
    animationsEnabled: Boolean,
    onSpeakNow: () -> Unit,
    onSelectInterval: (SpeakInterval?) -> Unit,
    onInstallEngine: () -> Unit,
) {
    val readout = uiState.readout

    Spacer(Modifier.height(28.dp))

    // Date line — regular (non-mono) font, muted color, top of screen.
    // A setting can hide it; the empty Text keeps the layout stable.
    Text(
        text = if (uiState.showDate) readout?.date.orEmpty() else "",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Armed cue #1: the amber chip, directly under the date.
    uiState.armedInterval?.let { interval ->
        Spacer(Modifier.height(14.dp))
        AnnouncingChip(interval = interval, animationsEnabled = animationsEnabled)
    }

    Spacer(Modifier.height(72.dp))

    // The hero time. One accessibility label covers the whole row so
    // TalkBack reads "10:24 and 53 seconds AM" as a unit — and it is
    // deliberately NOT a live region (announcing every second would be
    // unbearable; users tap to hear the time).
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

    Spacer(Modifier.height(72.dp))

    // A clock that can't talk should say so right here, not only in
    // settings: warn when the phone has no working speech engine.
    if (speakerState == SpeakerState.NoEngine || speakerState == SpeakerState.Error) {
        NoSpeechEngineCard(onInstallEngine = onInstallEngine)
        Spacer(Modifier.height(12.dp))
    }

    SpeakEveryControl(
        armedInterval = uiState.armedInterval,
        lastCustomInterval = lastCustomInterval,
        animationsEnabled = animationsEnabled,
        onSelectInterval = onSelectInterval,
    )
    Spacer(Modifier.height(12.dp))

    // Status line: countdown + auto-off pill while armed (cue #3),
    // otherwise the quiet "nothing scheduled" caption.
    if (uiState.armedInterval != null) {
        CountdownLine(nextIn = uiState.nextIn, autoOffIn = uiState.autoOffIn)
    } else {
        Text(
            text = stringResource(R.string.clock_no_announcements),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(28.dp))
}

/** "Next in 3:12" + the "Auto-off in 58 min" pill (armed cue #3). */
@Composable
private fun CountdownLine(nextIn: Duration?, autoOffIn: Duration?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nextIn != null) {
            Text(
                text = stringResource(R.string.clock_next_in, formatMinutesSeconds(nextIn)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (autoOffIn != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.clock_auto_off_in, formatApproxDuration(autoOffIn)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** 3 min 12 s -> "3:12" — the ticking countdown format. */
private fun formatMinutesSeconds(d: Duration): String =
    "%d:%02d".format(d.toMinutes(), d.seconds % 60)

/** A coarse human duration for the auto-off pill: "58 min", "1 h 5 min", "45 s". */
private fun formatApproxDuration(d: Duration): String {
    val totalMinutes = d.toMinutes()
    return when {
        totalMinutes >= 60 && totalMinutes % 60 == 0L -> "${totalMinutes / 60} h"
        totalMinutes >= 60 -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
        totalMinutes >= 1 -> "$totalMinutes min"
        else -> "${d.seconds} s"
    }
}

/**
 * Builds a natural-language time label for screen readers, e.g.
 * "10:24 and 53 seconds AM". The meridiem is included HERE even though the
 * visual hero omits it — a screen-reader user can't glance at the sky, so
 * the ambiguity matters more. Falls back to empty before the first tick.
 */
private fun buildTimeContentDescription(readout: ClockReadout?): String {
    if (readout == null) return ""
    val secondsPart = readout.seconds?.let { " and ${it.toInt()} seconds" } ?: ""
    val meridiemPart = readout.meridiem?.let { " $it" } ?: ""
    return "${readout.time}$secondsPart$meridiemPart"
}
