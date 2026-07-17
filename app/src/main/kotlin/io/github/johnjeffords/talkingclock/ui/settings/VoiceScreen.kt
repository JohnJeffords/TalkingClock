package io.github.johnjeffords.talkingclock.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.speech.SpeakerState
import io.github.johnjeffords.talkingclock.ui.components.NoSpeechEngineCard

/**
 * Voice & speech settings (design frame 11): the no-engine warning when
 * needed, rate/pitch sliders, and the Test button. The voice-pack source
 * picker and import land with M7 — this screen grows those rows then.
 */
@Composable
fun VoiceScreen(
    speakerState: SpeakerState,
    rate: Float,
    pitch: Float,
    onSetRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onTest: () -> Unit,
    onInstallEngine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        if (speakerState == SpeakerState.NoEngine || speakerState == SpeakerState.Error) {
            NoSpeechEngineCard(onInstallEngine = onInstallEngine)
            Spacer(Modifier.height(20.dp))
        }

        // Rate. Sliders write the value on every drag tick; the repository
        // write is cheap (one key) and the TTS engine applies it live, so
        // dragging gives immediate feedback on the next Test.
        Text(
            text = stringResource(R.string.voice_rate),
            style = MaterialTheme.typography.titleSmall,
        )
        SliderWithLabel(value = rate, onChange = onSetRate)

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.voice_pitch),
            style = MaterialTheme.typography.titleSmall,
        )
        SliderWithLabel(value = pitch, onChange = onSetPitch)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onTest,
            enabled = speakerState == SpeakerState.Ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(stringResource(R.string.voice_test))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A 0.5×–2.0× slider with its current multiplier shown ("1.2×"). */
@Composable
private fun SliderWithLabel(value: Float, onChange: (Float) -> Unit) {
    Column {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.5f..2.0f,
            steps = 13, // 0.5 .. 2.0 in ~0.1 increments
        )
        Text(
            text = "%.1f×".format(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
