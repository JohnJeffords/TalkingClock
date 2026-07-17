package io.github.johnjeffords.talkingclock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice

/**
 * The main Settings screen (design frame 10): one scrollable list of
 * sections. Rows either toggle directly, open a small choice dialog, or
 * navigate to a sub-screen (Voice, Speaking style, Quiet hours, About).
 *
 * Not present yet, on purpose: seven-segment clock style (needs the DSEG
 * font — parked), hourly chime, and haptics (needs VIBRATE — decided with
 * the alarm permissions, D-020). docs/DESIGN.md tracks these.
 */
@Composable
fun SettingsScreen(
    settings: SettingsRepository.Settings,
    onSetTheme: (ThemeChoice) -> Unit,
    onSetTimeFormat: (SettingsRepository.TimeFormat) -> Unit,
    onSetShowSeconds: (Boolean) -> Unit,
    onSetShowDate: (Boolean) -> Unit,
    onSetAutoOff: (Int) -> Unit,
    onSetTimerSchedule: (String) -> Unit,
    onSetStopwatchSpeakElapsed: (Boolean) -> Unit,
    onSetStopwatchSpeakLaps: (Boolean) -> Unit,
    onSetSpeechLead: (Int) -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSpeakingStyle: () -> Unit,
    onOpenQuietHours: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which choice dialog is open (null = none).
    var openDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionHeader(stringResource(R.string.settings_section_clock))
        SettingsNavRow(
            icon = Icons.Outlined.AccessTime,
            title = stringResource(R.string.settings_time_format),
            value = settings.timeFormat.label(),
            onClick = { openDialog = SettingsDialog.TimeFormat },
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.Visibility,
            title = stringResource(R.string.settings_show_seconds),
            checked = settings.showSeconds,
            onCheckedChange = onSetShowSeconds,
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.CalendarMonth,
            title = stringResource(R.string.settings_show_date),
            checked = settings.showDate,
            onCheckedChange = onSetShowDate,
        )
        SettingsNavRow(
            icon = Icons.Outlined.RecordVoiceOver,
            title = stringResource(R.string.settings_speaking_style),
            value = settings.speakingStyle.label(),
            onClick = onOpenSpeakingStyle,
        )
        SettingsNavRow(
            icon = Icons.Outlined.PowerSettingsNew,
            title = stringResource(R.string.settings_auto_off),
            value = stringResource(R.string.settings_auto_off_value, settings.autoOffMinutes),
            onClick = { openDialog = SettingsDialog.AutoOff },
        )

        SettingsSectionHeader(stringResource(R.string.settings_section_timer))
        SettingsNavRow(
            icon = Icons.Outlined.Timer,
            title = stringResource(R.string.settings_timer_schedule),
            value = settings.timerScheduleName,
            onClick = { openDialog = SettingsDialog.TimerSchedule },
        )

        SettingsSectionHeader(stringResource(R.string.settings_section_stopwatch))
        SettingsSwitchRow(
            icon = Icons.Outlined.HourglassEmpty,
            title = stringResource(R.string.settings_sw_announce),
            subtitle = stringResource(R.string.settings_sw_announce_subtitle),
            checked = settings.stopwatchSpeakElapsed,
            onCheckedChange = onSetStopwatchSpeakElapsed,
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.Flag,
            title = stringResource(R.string.settings_sw_speak_laps),
            checked = settings.stopwatchSpeakLaps,
            onCheckedChange = onSetStopwatchSpeakLaps,
        )

        SettingsSectionHeader(stringResource(R.string.settings_section_voice))
        SettingsNavRow(
            icon = Icons.Outlined.VolumeUp,
            title = stringResource(R.string.settings_voice),
            value = null,
            onClick = onOpenVoice,
        )
        SettingsNavRow(
            icon = Icons.Outlined.Schedule,
            title = stringResource(R.string.settings_speech_lead),
            value = speechLeadLabel(settings.speechLeadMillis),
            onClick = { openDialog = SettingsDialog.SpeechLead },
        )

        SettingsSectionHeader(stringResource(R.string.settings_section_display))
        SettingsNavRow(
            icon = Icons.Outlined.DarkMode,
            title = stringResource(R.string.settings_theme),
            value = settings.theme.label(),
            onClick = { openDialog = SettingsDialog.Theme },
        )

        SettingsSectionHeader(stringResource(R.string.settings_section_behavior))
        SettingsNavRow(
            icon = Icons.Outlined.Bedtime,
            title = stringResource(R.string.settings_quiet_hours),
            value = if (settings.quietHoursEnabled) {
                stringResource(
                    R.string.settings_quiet_value,
                    formatMinutes(settings.quietFromMinutes),
                    formatMinutes(settings.quietUntilMinutes),
                )
            } else {
                stringResource(R.string.settings_quiet_off)
            },
            onClick = onOpenQuietHours,
        )

        SettingsSectionHeader(stringResource(R.string.settings_section_about))
        SettingsNavRow(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.settings_about),
            value = null,
            onClick = onOpenAbout,
        )
    }

    // --- Choice dialogs ------------------------------------------------------
    when (openDialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeChoice.entries.map { it.label() },
            selectedIndex = ThemeChoice.entries.indexOf(settings.theme),
            onSelect = { onSetTheme(ThemeChoice.entries[it]); openDialog = null },
            onDismiss = { openDialog = null },
        )
        SettingsDialog.TimeFormat -> ChoiceDialog(
            title = stringResource(R.string.settings_time_format),
            options = SettingsRepository.TimeFormat.entries.map { it.label() },
            selectedIndex = SettingsRepository.TimeFormat.entries.indexOf(settings.timeFormat),
            onSelect = {
                onSetTimeFormat(SettingsRepository.TimeFormat.entries[it])
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        SettingsDialog.AutoOff -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_off),
            options = AUTO_OFF_CHOICES.map {
                stringResource(R.string.settings_auto_off_value, it)
            },
            selectedIndex = AUTO_OFF_CHOICES.indexOf(settings.autoOffMinutes),
            onSelect = { onSetAutoOff(AUTO_OFF_CHOICES[it]); openDialog = null },
            onDismiss = { openDialog = null },
        )
        SettingsDialog.TimerSchedule -> ChoiceDialog(
            title = stringResource(R.string.settings_timer_schedule),
            options = AnnouncementSchedule.BUILT_INS.map { it.name },
            selectedIndex = AnnouncementSchedule.BUILT_INS
                .indexOfFirst { it.name == settings.timerScheduleName },
            onSelect = {
                onSetTimerSchedule(AnnouncementSchedule.BUILT_INS[it].name)
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        SettingsDialog.SpeechLead -> ChoiceDialog(
            title = stringResource(R.string.settings_speech_lead),
            options = SPEECH_LEAD_CHOICES.map { speechLeadLabel(it) },
            selectedIndex = SPEECH_LEAD_CHOICES.indexOf(settings.speechLeadMillis)
                .let { if (it == -1) SPEECH_LEAD_CHOICES.indexOf(1000) else it },
            onSelect = { onSetSpeechLead(SPEECH_LEAD_CHOICES[it]); openDialog = null },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

private enum class SettingsDialog { Theme, TimeFormat, AutoOff, TimerSchedule, SpeechLead }

private val AUTO_OFF_CHOICES = listOf(15, 30, 60, 120)

/** Speech-lead options in milliseconds (Off, then half-second steps). */
private val SPEECH_LEAD_CHOICES = listOf(0, 500, 1000, 1500, 2000, 2500)

/** "1.0 s ahead" / "Off" label for a speech-lead value in ms. */
@Composable
private fun speechLeadLabel(millis: Int): String =
    if (millis == 0) {
        stringResource(R.string.settings_speech_lead_off)
    } else {
        stringResource(R.string.settings_speech_lead_value, millis / 1000f)
    }

/** "22:00"-style label for a minutes-since-midnight value. */
fun formatMinutes(minutesSinceMidnight: Int): String =
    "%d:%02d".format(minutesSinceMidnight / 60, minutesSinceMidnight % 60)

@Composable
fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.System -> stringResource(R.string.theme_system)
    ThemeChoice.Light -> stringResource(R.string.theme_light)
    ThemeChoice.Dark -> stringResource(R.string.theme_dark)
    ThemeChoice.Amoled -> stringResource(R.string.theme_amoled)
}

@Composable
private fun SettingsRepository.TimeFormat.label(): String = when (this) {
    SettingsRepository.TimeFormat.System -> stringResource(R.string.format_system)
    SettingsRepository.TimeFormat.TwelveHour -> stringResource(R.string.format_12h)
    SettingsRepository.TimeFormat.TwentyFourHour -> stringResource(R.string.format_24h)
}

/** Shared with the speaking-style picker screen. */
@Composable
fun SpeakingStyle.label(): String = when (this) {
    SpeakingStyle.Conversational -> stringResource(R.string.style_conversational)
    SpeakingStyle.Digits -> stringResource(R.string.style_digits)
    SpeakingStyle.Formal -> stringResource(R.string.style_formal)
    SpeakingStyle.TwentyFourHour -> stringResource(R.string.style_24h)
}

/** A minimal radio-list dialog used by every multi-choice row. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                        )
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
