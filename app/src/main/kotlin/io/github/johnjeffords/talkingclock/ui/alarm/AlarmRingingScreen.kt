package io.github.johnjeffords.talkingclock.ui.alarm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.ui.theme.NumericFontFamily

/**
 * The ringing screen (design frame 25): the huge mono time, the label,
 * giant Snooze/Dismiss, and — when this alarm hands off to the speaking
 * clock — the honest note saying dismissing starts it. Someone half-asleep
 * has to read this; everything is enormous and there are exactly two
 * choices.
 */
@Composable
fun AlarmRingingScreen(
    alarm: Alarm,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                text = "%d:%02d".format(((alarm.hour + 11) % 12) + 1, alarm.minute),
                fontFamily = NumericFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 104.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))

            // The honest handoff note (design law: nothing happens silently).
            if (alarm.handoffIntervalSeconds != null) {
                Text(
                    text = stringResource(R.string.alarm_handoff_note),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
            }

            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(66.dp),
                ) {
                    Text(
                        stringResource(R.string.alarm_snooze),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(66.dp),
                ) {
                    Text(
                        stringResource(R.string.alarm_dismiss),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
