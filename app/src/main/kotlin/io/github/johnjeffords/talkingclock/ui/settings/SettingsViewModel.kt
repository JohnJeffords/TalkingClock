package io.github.johnjeffords.talkingclock.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.speech.Announcer
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.Utterance
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import io.github.johnjeffords.talkingclock.voicepack.VoicePackStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * One ViewModel for the whole settings area (the main screen and its
 * sub-screens all edit the same [SettingsRepository.Settings] snapshot).
 * Each setter fires a small coroutine that writes one key; the repository's
 * flow echoes the change back to every observer. Voice-pack management
 * (import / select / delete) lives here too, feeding the Voice screen.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val announcer: Announcer,
    private val packStore: VoicePackStore,
) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.Settings(),
        )

    /** Installed voice packs, refreshed after every import/delete. */
    private val installedPacksFlow =
        MutableStateFlow<List<VoicePackStore.InstalledPack>>(emptyList())
    val installedPacks: StateFlow<List<VoicePackStore.InstalledPack>> =
        installedPacksFlow.asStateFlow()

    /** Human-readable import failure for the Voice screen (null = fine). */
    private val importErrorFlow = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = importErrorFlow.asStateFlow()

    init {
        refreshPacks()
    }

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
    fun setSpeechLeadMillis(millis: Int) = write { repository.setSpeechLeadMillis(millis) }

    /** The Voice screen's Test button and the style picker's previews —
     *  spoken through the announcer, so the active pack is what's tested. */
    fun previewSpeech(style: SpeakingStyle = settings.value.speakingStyle) {
        announcer.announce(
            Utterance.TimeAnnouncement(LocalTime.now(), style),
            Speaker.PRIORITY_CLOCK,
        )
    }

    // --- Voice packs ---

    /** Select a pack as the voice source (null = system TTS). */
    fun selectVoicePack(id: String?) = write { repository.setVoicePackId(id) }

    /** Import a `.tcvoice` file the user picked. Errors surface on screen. */
    fun importVoicePack(uri: Uri) {
        viewModelScope.launch {
            packStore.import(uri)
                .onSuccess {
                    importErrorFlow.value = null
                    refreshPacks()
                }
                .onFailure { e ->
                    importErrorFlow.value = e.message ?: "Import failed"
                }
        }
    }

    fun deleteVoicePack(id: String) {
        viewModelScope.launch {
            packStore.delete(id)
            // Deleting the active pack reverts the source to system TTS.
            if (settings.value.voicePackId == id) repository.setVoicePackId(null)
            refreshPacks()
        }
    }

    fun dismissImportError() {
        importErrorFlow.value = null
    }

    private fun refreshPacks() {
        viewModelScope.launch { installedPacksFlow.value = packStore.listInstalled() }
    }

    // --- Speaking clock / timer / stopwatch ---
    fun setAutoOffMinutes(minutes: Int) = write { repository.setAutoOffMinutes(minutes) }
    fun setTimerScheduleName(name: String) = write { repository.setTimerScheduleName(name) }
    fun setStopwatchSpeakElapsed(speak: Boolean) =
        write { repository.setStopwatchSpeakElapsed(speak) }
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
                SettingsViewModel(app.settingsRepository, app.announcer, app.voicePackStore)
            }
        }
    }
}
