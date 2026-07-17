package io.github.johnjeffords.talkingclock.ui.stopwatch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.ui.theme.NumericFontFamily
import java.time.Duration

/**
 * The Stopwatch screen (design frame 09) — deliberately the simplest tool:
 * the big counting readout with muted tenths, Start/Pause · Lap · Reset,
 * and the lap list newest-first with fastest/slowest highlighted once
 * there are enough laps to compare.
 */
@Composable
fun StopwatchScreen(
    uiState: StopwatchUiState,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = uiState.phase == StopwatchEngine.Phase.Running
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // Hero readout: mono time + smaller muted tenths (design: 76/40sp).
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatElapsed(uiState.elapsed),
                fontFamily = NumericFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 76.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                // Hundredths, like a real stopwatch — the last digit blurs as
                // it counts (the display is frame-driven, see StopwatchRoute).
                text = ".%02d".format(uiState.elapsed.toMillis() % 1000 / 10),
                fontFamily = NumericFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        // Start/Pause · Lap · Reset. Lap only lands while running; Reset
        // only while paused (fat-finger protection, design frame 09).
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onReset,
                enabled = uiState.phase == StopwatchEngine.Phase.Paused,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
            ) {
                Text(stringResource(R.string.stopwatch_reset))
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onLap,
                enabled = running,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
            ) {
                Text(stringResource(R.string.stopwatch_lap))
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = if (running) onPause else onStartOrResume,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            running -> R.string.stopwatch_pause
                            uiState.phase == StopwatchEngine.Phase.Paused ->
                                R.string.stopwatch_resume
                            else -> R.string.stopwatch_start
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Lap list: lap number · lap time · cumulative, newest on top.
        LazyColumn(Modifier.weight(1f)) {
            items(uiState.laps, key = { it.lap.number }) { row ->
                LapRowItem(row)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LapRowItem(row: LapRow) {
    // Fastest laps read green, slowest red — color plus the label below,
    // never hue alone (the design's accessibility rule).
    val highlightColor = when {
        row.isFastest -> Color_Success()
        row.isSlowest -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stopwatch_lap_n, row.lap.number),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.isFastest) {
                Text(
                    text = stringResource(R.string.stopwatch_fastest),
                    style = MaterialTheme.typography.labelSmall,
                    color = highlightColor,
                )
            } else if (row.isSlowest) {
                Text(
                    text = stringResource(R.string.stopwatch_slowest),
                    style = MaterialTheme.typography.labelSmall,
                    color = highlightColor,
                )
            }
        }
        Text(
            text = formatLap(row.lap.lapTime),
            fontFamily = NumericFontFamily,
            style = MaterialTheme.typography.titleMedium,
            color = highlightColor,
        )
        Spacer(Modifier.width(24.dp))
        Text(
            text = formatLap(row.lap.cumulative),
            fontFamily = NumericFontFamily,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The design's success green (dark scheme value; fine on light too). */
@Composable
private fun Color_Success() = androidx.compose.ui.graphics.Color(0xFF8FE39B)

/** 4357.2 s → "1:12:37"; under an hour → "12:37"; under a minute "0:12". */
private fun formatElapsed(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Lap column format: "1:02.14" (minutes:seconds.hundredths). */
private fun formatLap(d: Duration): String {
    val m = d.toMinutes()
    val s = d.seconds % 60
    val hundredths = d.toMillis() % 1000 / 10
    return "%d:%02d.%02d".format(m, s, hundredths)
}
