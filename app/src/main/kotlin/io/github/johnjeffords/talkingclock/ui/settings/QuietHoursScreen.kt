package io.github.johnjeffords.talkingclock.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.johnjeffords.talkingclock.R

/**
 * Quiet-hours settings (design frame 21): master switch, From/Until rows
 * opening the standard Material time picker, and the "timers still speak"
 * exception. The window logic itself lives in domain/announce/QuietWindow
 * (including the overnight wrap) — this screen only edits the numbers.
 */
@Composable
fun QuietHoursScreen(
    enabled: Boolean,
    fromMinutes: Int,
    untilMinutes: Int,
    allowTimers: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSetFrom: (Int) -> Unit,
    onSetUntil: (Int) -> Unit,
    onSetAllowTimers: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which of the two time pickers is open (null = none).
    var editing by remember { mutableStateOf<QuietEdge?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SettingsSwitchRow(
            icon = Icons.Outlined.Bedtime,
            title = stringResource(R.string.quiet_enable),
            subtitle = stringResource(R.string.quiet_enable_subtitle),
            checked = enabled,
            onCheckedChange = onSetEnabled,
        )
        SettingsNavRow(
            icon = Icons.Outlined.Schedule,
            title = stringResource(R.string.quiet_from),
            value = formatMinutes(fromMinutes),
            onClick = { editing = QuietEdge.From },
        )
        SettingsNavRow(
            icon = Icons.Outlined.Schedule,
            title = stringResource(R.string.quiet_until),
            value = formatMinutes(untilMinutes),
            onClick = { editing = QuietEdge.Until },
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.Timer,
            title = stringResource(R.string.quiet_allow_timers),
            subtitle = stringResource(R.string.quiet_allow_timers_subtitle),
            checked = allowTimers,
            onCheckedChange = onSetAllowTimers,
        )
    }

    editing?.let { edge ->
        val initial = if (edge == QuietEdge.From) fromMinutes else untilMinutes
        TimePickerDialog(
            initialMinutes = initial,
            onConfirm = { minutes ->
                if (edge == QuietEdge.From) onSetFrom(minutes) else onSetUntil(minutes)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private enum class QuietEdge { From, Until }

/** The standard Material 3 time picker wrapped in a confirm/cancel dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
