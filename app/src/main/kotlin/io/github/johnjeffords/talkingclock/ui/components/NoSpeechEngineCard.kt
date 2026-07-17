package io.github.johnjeffords.talkingclock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R

/**
 * The amber warning card shown when no text-to-speech engine is installed —
 * the normal out-of-box state on GrapheneOS/CalyxOS, where Google TTS is
 * absent (docs/DECISIONS.md D-011). Design frame 11 places this in Voice
 * settings; it also appears on the Clock screen because a talking clock
 * that can't talk should say so where the user actually is.
 *
 * @param onInstallEngine opens where the user can get a FOSS engine
 *   (RHVoice / eSpeak NG on F-Droid).
 */
@Composable
fun NoSpeechEngineCard(
    onInstallEngine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Icon(
                imageVector = Icons.Outlined.VoiceOverOff,
                contentDescription = null,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.no_engine_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.no_engine_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onInstallEngine) {
                    Text(stringResource(R.string.no_engine_install))
                }
            }
        }
    }
}
