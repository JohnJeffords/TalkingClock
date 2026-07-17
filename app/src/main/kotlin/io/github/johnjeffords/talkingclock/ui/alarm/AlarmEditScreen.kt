package io.github.johnjeffords.talkingclock.ui.alarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.ui.settings.ChoiceDialog
import io.github.johnjeffords.talkingclock.ui.settings.SettingsSwitchRow
import io.github.johnjeffords.talkingclock.ui.settings.TimePickerDialog
import io.github.johnjeffords.talkingclock.ui.theme.NumericFontFamily
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The alarm editor (design frame 24): tap the big time to open the Material
 * time picker, day-of-week chips, label, ring behavior toggles, and the
 * signature amber card — "Start the speaking clock" on dismiss, with its
 * interval and duration. Edits are local until Save.
 */
@Composable
fun AlarmEditScreen(
    initial: Alarm,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The whole draft is one state object; Save hands it back.
    var draft by remember { mutableStateOf(initial) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var intervalPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))

        // The time — tap to change (opens the standard M3 picker).
        Text(
            text = "%d:%02d %s".format(
                ((draft.hour + 11) % 12) + 1,
                draft.minute,
                if (draft.hour < 12) "AM" else "PM",
            ),
            fontFamily = NumericFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 64.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable { timePickerOpen = true },
        )
        Text(
            text = stringResource(R.string.alarm_tap_to_change),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        // Day chips, Monday-first. Empty selection = one-shot ("Once").
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in draft.days
                Surface(
                    onClick = {
                        draft = draft.copy(
                            days = if (selected) draft.days - day else draft.days + day,
                        )
                    },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = if (selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .size(44.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        Text(
            text = if (draft.isOneShot) {
                stringResource(R.string.alarm_once_hint)
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = draft.label,
            onValueChange = { draft = draft.copy(label = it.take(40)) },
            label = { Text(stringResource(R.string.alarm_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        SettingsSwitchRow(
            icon = Icons.Outlined.RecordVoiceOver,
            title = stringResource(R.string.alarm_announce_time),
            checked = draft.announceTime,
            onCheckedChange = { draft = draft.copy(announceTime = it) },
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.Vibration,
            title = stringResource(R.string.alarm_vibrate),
            checked = draft.vibrate,
            onCheckedChange = { draft = draft.copy(vibrate = it) },
        )

        Spacer(Modifier.height(14.dp))

        // The signature feature: the amber handoff card (design frame 24).
        HandoffCard(
            handoffInterval = draft.handoffIntervalSeconds?.let(::SpeakInterval),
            onToggle = { enabled ->
                draft = draft.copy(
                    handoffIntervalSeconds = if (enabled) 5 * 60 else null,
                )
            },
            onPickInterval = { intervalPickerOpen = true },
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSave(draft) },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
        ) {
            Text(stringResource(R.string.alarm_save), style = MaterialTheme.typography.titleMedium)
        }
        onDelete?.let { delete ->
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = delete) {
                Text(
                    stringResource(R.string.alarm_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (timePickerOpen) {
        TimePickerDialog(
            initialMinutes = draft.hour * 60 + draft.minute,
            onConfirm = { minutes ->
                draft = draft.copy(hour = minutes / 60, minute = minutes % 60)
                timePickerOpen = false
            },
            onDismiss = { timePickerOpen = false },
        )
    }

    if (intervalPickerOpen) {
        ChoiceDialog(
            title = stringResource(R.string.alarm_handoff_interval),
            options = SpeakInterval.PRESETS.map { it.label },
            selectedIndex = SpeakInterval.PRESETS.indexOfFirst {
                it.seconds == draft.handoffIntervalSeconds
            },
            onSelect = { index ->
                draft = draft.copy(
                    handoffIntervalSeconds = SpeakInterval.PRESETS[index].seconds,
                )
                intervalPickerOpen = false
            },
            onDismiss = { intervalPickerOpen = false },
        )
    }
}

/** The amber "Start the speaking clock" card — the getting-ready hook. */
@Composable
private fun HandoffCard(
    handoffInterval: SpeakInterval?,
    onToggle: (Boolean) -> Unit,
    onPickInterval: () -> Unit,
) {
    val active = handoffInterval != null
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.alarm_handoff_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = stringResource(R.string.alarm_handoff_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                androidx.compose.material3.Switch(checked = active, onCheckedChange = onToggle)
            }
            if (active && handoffInterval != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.alarm_handoff_value,
                        handoffInterval.label,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable(onClick = onPickInterval),
                )
            }
        }
    }
}
