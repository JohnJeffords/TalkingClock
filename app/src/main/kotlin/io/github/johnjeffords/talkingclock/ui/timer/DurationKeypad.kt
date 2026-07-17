package io.github.johnjeffords.talkingclock.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The Google-Clock-style duration keypad (design frame 05): digits shift in
 * from the right, "00" is a convenience key, backspace undoes. The keypad
 * itself is dumb — the shifting logic lives in TimerViewModel where it's
 * unit-testable; these are just buttons.
 */
@Composable
fun DurationKeypad(
    onDigit: (Char) -> Unit,
    onDoubleZero: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("123", "456", "789").forEach { rowDigits ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowDigits.forEach { digit ->
                    KeypadKey(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            KeypadKey(label = "00", onClick = onDoubleZero, modifier = Modifier.weight(1f))
            KeypadKey(label = "0", onClick = { onDigit('0') }, modifier = Modifier.weight(1f))
            KeypadKey(
                label = null,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Delete digit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

/** One round key. [label] text or an [icon] — exactly one is shown. */
@Composable
private fun KeypadKey(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.aspectRatio(1.6f), // wide ovals fit 3-per-row nicely
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                icon()
            } else {
                Text(
                    text = label.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
