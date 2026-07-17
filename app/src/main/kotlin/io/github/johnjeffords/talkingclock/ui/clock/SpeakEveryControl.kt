package io.github.johnjeffords.talkingclock.ui.clock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval

/**
 * The "Speak every" control and its friends — the heart of the Clock screen.
 *
 * The design's rule (handoff README, "the active state"): when armed, FOUR
 * redundant cues fire together, and none of them relies on hue alone —
 *  1. the amber "Announcing every …" chip with a pulsing dot (drawn by
 *     [AnnouncingChip], placed by ClockScreen near the top),
 *  2. this control fills amber-container with an amber border + glow ring,
 *  3. the live "Next in m:ss" countdown + "Auto-off in NN min" pill under it,
 *  4. the foreground-service notification (AnnouncerService's job).
 *
 * Motion (the ring pulse, the dot pulse) is decorative: [animationsEnabled]
 * is false when the OS reduced-motion setting is on — and in screenshot
 * tests, where an endlessly animating frame would never settle.
 */
@Composable
fun SpeakEveryControl(
    armedInterval: SpeakInterval?,
    lastCustomInterval: SpeakInterval?,
    animationsEnabled: Boolean,
    onSelectInterval: (SpeakInterval?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var customDialogOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        if (armedInterval == null) {
            OffControl(onClick = { menuOpen = true })
        } else {
            ArmedControl(
                interval = armedInterval,
                animationsEnabled = animationsEnabled,
                onClick = { menuOpen = true },
            )
        }

        // The interval menu (design frame 03). Anchored to the control.
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            IntervalMenuItem(
                label = stringResource(R.string.clock_interval_off),
                selected = armedInterval == null,
                onClick = { menuOpen = false; onSelectInterval(null) },
            )
            SpeakInterval.PRESETS.forEach { preset ->
                IntervalMenuItem(
                    label = preset.label,
                    selected = preset == armedInterval,
                    onClick = { menuOpen = false; onSelectInterval(preset) },
                )
            }
            // The most recent custom interval keeps its own slot (design:
            // "12 min (last custom)") so re-arming a favorite is one tap.
            lastCustomInterval?.let { custom ->
                IntervalMenuItem(
                    label = custom.label,
                    selected = custom == armedInterval,
                    onClick = { menuOpen = false; onSelectInterval(custom) },
                )
            }
            IntervalMenuItem(
                label = stringResource(R.string.clock_interval_custom),
                selected = false,
                onClick = { menuOpen = false; customDialogOpen = true },
            )
        }
    }

    if (customDialogOpen) {
        CustomIntervalDialog(
            onDismiss = { customDialogOpen = false },
            onConfirm = { interval ->
                customDialogOpen = false
                onSelectInterval(interval)
            },
        )
    }
}

/** One row of the interval menu; the current choice is highlighted. */
@Composable
private fun IntervalMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        onClick = onClick,
    )
}

/** The control at rest: muted card, "Speak every / Off" (design frame 01). */
@Composable
private fun OffControl(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ControlRow(
            icon = { Icon(Icons.Outlined.VolumeOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            value = stringResource(R.string.clock_interval_off),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The control while announcing: amber-container fill, 1.5dp amber border, a
 * soft static glow ring, and (motion permitting) a slow expanding-ring pulse
 * — the design's cue #2.
 */
@Composable
private fun ArmedControl(
    interval: SpeakInterval,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val amber = MaterialTheme.colorScheme.primary

    // The expanding ring: grows outward and fades, every 2.4 s (per the
    // design's `tcring` keyframes). Skipped entirely under reduced motion.
    val ringProgress = if (animationsEnabled) {
        rememberInfiniteTransition(label = "armed-ring")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 2400, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
                label = "armed-ring-progress",
            ).value
    } else {
        0f
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.5.dp, amber),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Static soft ring, always present (design: 4dp at 14% amber).
                drawRoundRect(
                    color = amber.copy(alpha = 0.14f),
                    topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                    size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx()),
                )
                // Expanding pulse ring: offset grows 0→10dp while fading out.
                if (ringProgress > 0f) {
                    val spread = 10.dp.toPx() * ringProgress
                    drawRoundRect(
                        color = amber.copy(alpha = 0.35f * (1f - ringProgress)),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + 2 * spread, size.height + 2 * spread),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx() + spread),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            },
    ) {
        ControlRow(
            icon = { Icon(Icons.Outlined.VolumeUp, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
            value = interval.label,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Shared inner layout of both control states: icon · label/value · chevron. */
@Composable
private fun ControlRow(
    icon: @Composable () -> Unit,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        icon()
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.clock_speak_every),
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = labelColor)
    }
}

/**
 * The amber status chip shown near the top of the Clock screen while
 * announcing — cue #1 of the armed state. Gradient amber pill, dark text,
 * pulsing dot (static under reduced motion / in screenshots).
 */
@Composable
fun AnnouncingChip(
    interval: SpeakInterval,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotAlpha = if (animationsEnabled) {
        rememberInfiniteTransition(label = "chip-dot")
            .animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "chip-dot-alpha",
            ).value
    } else {
        1f
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    // The design's amber gradient: #FFC24B → #FFB020.
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFFC24B), Color(0xFFFFB020)),
                    ),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(dotAlpha)
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.clock_announcing_chip, interval.spokenLabel),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
