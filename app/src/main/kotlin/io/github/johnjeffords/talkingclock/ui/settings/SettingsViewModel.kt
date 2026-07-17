package io.github.johnjeffords.talkingclock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * One ViewModel for the whole settings area (the main screen and its
 * sub-screens all edit the same [SettingsRepository.Settings] snapshot).
 * Each setter fires a small coroutine that writes one key; the repository's
 * flow echoes the change back to every observer.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val speaker: Speaker,
) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.Settings(),
        )

    // --- Display ---
    fun setTheme(theme: ThemeChoice) = write { repository.setTheme(theme) }
    fun setTimeFormat(format: SettingsRepository.TimeFormat) =
        write { repository.setTimeFormat(format) }
    fun setShowSeconds(show: Boolean) = write { repository.setShowSeconds(show) }
    fun setShowDate(show: Boolean) = write { repository.setShowDate(show) }

    // --- Voice ---
    fun setSpeakingStyle(style: SpeakingStyle) = write { repository.setSpeakingStyle(style) }
    fun setTtsRate(rate: Float) = write { repository.setTtsRate(rate) }
    fun setTtsPitch(pitch: Float) = write { repository.setTtsPitch(pitch) }

    /** The Voice screen's Test button and the style picker's previews. */
    fun previewSpeech(style: SpeakingStyle = settings.value.speakingStyle) {
        speaker.speak(
            Phrasebook.timeAnnouncement(LocalTime.now(), style),
            Speaker.PRIORITY_CLOCK,
        )
    }

    // --- Speaking clock / timer / stopwatch ---
    fun setAutoOffMinutes(minutes: Int) = write { repository.setAutoOffMinutes(minutes) }
    fun setTimerScheduleName(name: String) = write { repository.setTimerScheduleName(name) }
    fun setStopwatchAnnounceEvery(seconds: Int) =
        write { repository.setStopwatchAnnounceEvery(seconds) }
    fun setStopwatchSpeakLaps(speak: Boolean) =
        write { repository.setStopwatchSpeakLaps(speak) }

    // --- Quiet hours ---
    fun setQuietHoursEnabled(enabled: Boolean) =
        write { repository.setQuietHoursEnabled(enabled) }
    fun setQuietFrom(minutes: Int) = write { repository.setQuietFrom(minutes) }
    fun setQuietUntil(minutes: Int) = write { repository.setQuietUntil(minutes) }
    fun setQuietAllowTimers(allow: Boolean) = write { repository.setQuietAllowTimers(allow) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TalkingClockApp
                SettingsViewModel(app.settingsRepository, app.speaker)
            }
        }
    }
}
