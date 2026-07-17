package io.github.johnjeffords.talkingclock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.voicepack.coverageOf
import io.github.johnjeffords.talkingclock.speech.SpeakerState
import io.github.johnjeffords.talkingclock.ui.components.NoSpeechEngineCard
import io.github.johnjeffords.talkingclock.voicepack.VoicePackStore

/**
 * Voice & speech settings (design frame 11): the voice-source picker
 * (system TTS or an installed voice pack, each pack with its coverage
 * report), pack import/delete, rate/pitch sliders, the Test button, and
 * the no-engine warning when needed.
 */
@Composable
fun VoiceScreen(
    speakerState: SpeakerState,
    rate: Float,
    pitch: Float,
    selectedPackId: String?,
    installedPacks: List<VoicePackStore.InstalledPack>,
    importError: String?,
    onSetRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSelectPack: (String?) -> Unit,
    onImportPack: () -> Unit,
    onDeletePack: (String) -> Unit,
    onDismissImportError: () -> Unit,
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

        // --- Voice source: system TTS or an installed pack ------------------
        Text(
            text = stringResource(R.string.voice_source),
            style = MaterialTheme.typography.titleSmall,
        )
        VoiceSourceRow(
            title = stringResource(R.string.voice_source_system),
            subtitle = null,
            selected = selectedPackId == null,
            onSelect = { onSelectPack(null) },
            onDelete = null,
        )
        installedPacks.forEach { pack ->
            val coverage = coverageOf(pack.manifest)
            VoiceSourceRow(
                title = pack.manifest.name,
                // The spec's coverage report, e.g.
                // "Can speak: clock ✓ · timer ✓ · stopwatch ✗ — falls back to TTS"
                subtitle = buildString {
                    append("Can speak: ")
                    append("clock ").append(if (coverage.clock) "✓" else "✗")
                    append(" · timer ").append(if (coverage.timer) "✓" else "✗")
                    append(" · stopwatch ").append(if (coverage.stopwatch) "✓" else "✗")
                    if (!coverage.clock || !coverage.timer || !coverage.stopwatch) {
                        append(" — missing parts fall back to the system voice")
                    }
                },
                selected = selectedPackId == pack.id,
                onSelect = { onSelectPack(pack.id) },
                onDelete = { onDeletePack(pack.id) },
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImportPack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_import_pack))
        }

        importError?.let { error ->
            Spacer(Modifier.height(8.dp))
            // Import problems are shown verbatim — pack authors need the
            // real reason ("Manifest references missing clip …"), not a shrug.
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismissImportError),
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- Rate / pitch ----------------------------------------------------
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
            enabled = speakerState == SpeakerState.Ready || selectedPackId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(stringResource(R.string.voice_test))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One selectable voice source with an optional delete button. */
@Composable
private fun VoiceSourceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onDelete?.let {
            IconButton(onClick = it) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.voice_delete_pack),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
