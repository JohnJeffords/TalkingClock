package io.github.johnjeffords.talkingclock.ui.alarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.ui.theme.NumericFontFamily
import java.time.Duration
import java.time.format.TextStyle
import java.util.Locale

/**
 * The alarm list (design frame 23): "Next alarm in …" header, one row per
 * alarm — big mono time, days/label line, WHAT IT SPEAKS (the chip line:
 * "Starts speaking clock · every 5 min" or "Announces the time"), and the
 * enable switch. FAB adds a new alarm; tapping a row edits it.
 */
@Composable
fun AlarmListScreen(
    uiState: AlarmViewModel.UiState,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onSetEnabled: (Alarm, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            uiState.nextAlarmIn?.let { untilNext ->
                Text(
                    text = stringResource(
                        R.string.alarm_next_in,
                        formatCoarseDuration(untilNext),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
                )
            }
            if (uiState.alarms.isEmpty()) {
                Text(
                    text = stringResource(R.string.alarm_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(26.dp),
                )
            }
            LazyColumn {
                items(uiState.alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onClick = { onEdit(alarm) },
                        onSetEnabled = { onSetEnabled(alarm, it) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 26.dp),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(26.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.alarm_add))
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    onClick: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    val dimmed = !alarm.enabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%d:%02d".format(((alarm.hour + 11) % 12) + 1, alarm.minute),
                    fontFamily = NumericFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 34.sp,
                    color = if (dimmed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (alarm.hour < 12) "AM" else "PM",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = daysLine(alarm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // What this alarm SPEAKS — the line that makes it a talking alarm.
            Text(
                text = speaksLine(alarm),
                style = MaterialTheme.typography.bodySmall,
                color = if (dimmed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Switch(checked = alarm.enabled, onCheckedChange = onSetEnabled)
    }
}

@Composable
private fun daysLine(alarm: Alarm): String {
    val days = if (alarm.isOneShot) {
        stringResource(R.string.alarm_once)
    } else {
        alarm.days
            .sortedBy { it.value }
            .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    return if (alarm.label.isBlank()) days else "$days · ${alarm.label}"
}

@Composable
private fun speaksLine(alarm: Alarm): String {
    val handoff = alarm.handoffIntervalSeconds
    return when {
        handoff != null -> stringResource(
            R.string.alarm_speaks_handoff,
            SpeakInterval(handoff).spokenLabel,
        )
        alarm.announceTime -> stringResource(R.string.alarm_speaks_time)
        else -> stringResource(R.string.alarm_speaks_sound_only)
    }
}

/** "in 9 h 24 min" / "in 3 min" — coarse on purpose; it's a glance line. */
private fun formatCoarseDuration(d: Duration): String {
    val hours = d.toHours()
    val minutes = (d.toMinutes() % 60).coerceAtLeast(if (hours == 0L) 1 else 0)
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
