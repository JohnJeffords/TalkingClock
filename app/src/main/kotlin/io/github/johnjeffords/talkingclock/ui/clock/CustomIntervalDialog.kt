package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval

/**
 * "Custom interval" dialog (design frame 19): minutes and seconds fields,
 * Start enabled only while the value is inside the allowed range
 * (15 s … 24 h — the [SpeakInterval] guardrails).
 *
 * Input handling is deliberately forgiving: only digits are accepted, blanks
 * count as zero, and rather than throwing on a bad value the dialog just
 * keeps Start disabled with the range hint visible.
 */
@Composable
fun CustomIntervalDialog(
    onDismiss: () -> Unit,
    onConfirm: (SpeakInterval) -> Unit,
) {
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }

    // Blank fields read as zero, so "5" minutes alone is a valid entry.
    val totalSeconds = (minutesText.toIntOrNull() ?: 0) * 60 + (secondsText.toIntOrNull() ?: 0)
    val isValid = totalSeconds in SpeakInterval.MIN_SECONDS..SpeakInterval.MAX_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clock_custom_dialog_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Row {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.clock_custom_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.clock_custom_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.clock_custom_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(SpeakInterval(totalSeconds)) },
            ) {
                Text(stringResource(R.string.dialog_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
