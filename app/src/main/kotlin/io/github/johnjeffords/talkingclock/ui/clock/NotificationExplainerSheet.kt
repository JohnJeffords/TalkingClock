package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R

/**
 * The one-time bottom sheet shown BEFORE requesting POST_NOTIFICATIONS
 * (design frame 12). Its whole job is honesty: explain that the only
 * notification this app ever shows is the "something is running" status
 * handle, and say plainly that refusing costs nothing but that visibility.
 *
 * Asking in-context like this — right as the user first arms the speaking
 * clock, with an explanation — is both the Android-recommended pattern and
 * far more likely to be answered thoughtfully than a cold system dialog at
 * first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationExplainerSheet(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDeny) {
        Column(Modifier.padding(horizontal = 26.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.notif_explainer_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.notif_explainer_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notif_explainer_honest),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_explainer_deny))
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAllow, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_explainer_allow))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
