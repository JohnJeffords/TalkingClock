package io.github.johnjeffords.talkingclock.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.ui.theme.NumericFontFamily
import java.time.Duration

/**
 * The Timer screen — one screen, one state machine, four looks (design
 * frames 05 idle / 06 running / 18 paused / 07 time's-up / 07b overtime):
 *
 *  - Idle: the typed duration + keypad + schedule picker + Start.
 *  - Running/Paused: the ring with mono remaining time, next-cue line,
 *    Pause-or-Resume + Reset.
 *  - Finished: loud amber "Time's up", red overtime counting up, Dismiss /
 *    Restart.
 *
 * Dumb-screen rule as everywhere: draws [uiState], reports intents.
 */
@Composable
fun TimerScreen(
    uiState: TimerUiState,
    onDigit: (Char) -> Unit,
    onDoubleZero: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSelectSchedule: (AnnouncementSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState.phase) {
            TimerEngine.Phase.Idle -> IdleContent(
                uiState, onDigit, onDoubleZero, onDelete, onStart, onSelectSchedule,
            )
            TimerEngine.Phase.Running, TimerEngine.Phase.Paused -> RunningContent(
                uiState, onPause, onResume, onReset,
            )
            TimerEngine.Phase.Finished -> FinishedContent(uiState, onReset, onStart)
        }
    }
}

// --- Idle: type a duration ---------------------------------------------------

// Content functions are ColumnScope extensions so Modifier.weight (a
// scoped modifier — it only exists inside a Column/Row) resolves.
@Composable
private fun ColumnScope.IdleContent(
    uiState: TimerUiState,
    onDigit: (Char) -> Unit,
    onDoubleZero: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onSelectSchedule: (AnnouncementSchedule) -> Unit,
) {
    Spacer(Modifier.height(20.dp))

    // The typed duration, Google-Clock style: H:MM:SS with dim zero state.
    val padded = uiState.typedDigits.padStart(6, '0')
    val entryColor = if (uiState.typedDigits.isEmpty()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "${padded.substring(0, 2)}:${padded.substring(2, 4)}:${padded.substring(4, 6)}",
        fontFamily = NumericFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 56.sp,
        color = entryColor,
    )
    Text(
        text = stringResource(R.string.timer_entry_units),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))
    DurationKeypad(onDigit = onDigit, onDoubleZero = onDoubleZero, onDelete = onDelete)
    Spacer(Modifier.weight(1f))

    ScheduleDropdown(current = uiState.schedule, onSelect = onSelectSchedule)
    Spacer(Modifier.height(14.dp))

    Button(
        onClick = onStart,
        enabled = uiState.canStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Text(stringResource(R.string.timer_start), style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(20.dp))
}

/** "Announcements: Game style ▾" — picks among the built-in schedules.
 *  (The full checkpoint editor is a later milestone.) */
@Composable
private fun ScheduleDropdown(
    current: AnnouncementSchedule,
    onSelect: (AnnouncementSchedule) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(
                text = stringResource(R.string.timer_announcements_label, current.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AnnouncementSchedule.BUILT_INS.forEach { schedule ->
                DropdownMenuItem(
                    text = {
                        Text(
                            schedule.name,
                            fontWeight = if (schedule == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { open = false; onSelect(schedule) },
                )
            }
        }
    }
}

// --- Running / Paused: the ring ----------------------------------------------

@Composable
private fun ColumnScope.RunningContent(
    uiState: TimerUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
) {
    val paused = uiState.phase == TimerEngine.Phase.Paused
    Spacer(Modifier.weight(1f))

    // Thin progress ring with the mono remaining time inside (design: 66sp).
    Box(contentAlignment = Alignment.Center) {
        ProgressRing(progress = uiState.progress, size = 280.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatClockLike(uiState.remaining),
                fontFamily = NumericFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 66.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (paused) {
                Text(
                    text = stringResource(R.string.timer_paused_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // "Next: one minute remaining" — what the voice says next.
                uiState.nextMarkAt?.let { mark ->
                    Text(
                        text = stringResource(
                            R.string.timer_next_cue,
                            Phrasebook.timerRemaining(mark).lowercase(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Paused announcements pause too — say so (design frame 18).
    if (paused) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.timer_paused_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.weight(1f))

    Row(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
        ) {
            Text(stringResource(R.string.timer_reset))
        }
        Spacer(Modifier.width(14.dp))
        Button(
            onClick = if (paused) onResume else onPause,
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
        ) {
            Text(
                stringResource(if (paused) R.string.timer_resume else R.string.timer_pause),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ProgressRing(progress: Float, size: androidx.compose.ui.unit.Dp) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val active = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = active,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
}

// --- Finished: time's up (+ overtime) -----------------------------------------

@Composable
private fun ColumnScope.FinishedContent(
    uiState: TimerUiState,
    onReset: () -> Unit,
    onRestart: () -> Unit,
) {
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.timer_times_up),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    if (uiState.overtime.seconds > 0) {
        Spacer(Modifier.height(10.dp))
        // Overtime counts UP in red: "+0:37" (design frame 07b).
        Text(
            text = "+${formatClockLike(uiState.overtime)}",
            fontFamily = NumericFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 72.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
        ) {
            Text(stringResource(R.string.timer_restart))
        }
        Spacer(Modifier.width(14.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
        ) {
            Text(stringResource(R.string.timer_dismiss), style = MaterialTheme.typography.titleMedium)
        }
    }
    Spacer(Modifier.height(20.dp))
}

/** 754 s → "12:34"; 3725 s → "1:02:05". Hours only when needed. */
private fun formatClockLike(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
