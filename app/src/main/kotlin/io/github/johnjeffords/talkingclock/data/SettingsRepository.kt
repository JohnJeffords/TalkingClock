package io.github.johnjeffords.talkingclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * All user settings, persisted with Preferences DataStore (a small async
 * key-value store — the modern SharedPreferences). One [Settings] snapshot
 * flows out; typed setters flow in. Every consumer (theme, controllers,
 * screens) observes the same flow, so a change anywhere applies everywhere
 * immediately.
 *
 * Defaults here ARE the product defaults documented in docs/DESIGN.md —
 * change them there first, then here.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** How the clock's displayed hours follow (or override) the device. */
    enum class TimeFormat { System, TwelveHour, TwentyFourHour }

    /** One immutable snapshot of every setting. */
    data class Settings(
        // Display
        val theme: ThemeChoice = ThemeChoice.System,
        val timeFormat: TimeFormat = TimeFormat.System,
        val showSeconds: Boolean = true,
        val showDate: Boolean = true,
        // Voice
        val speakingStyle: SpeakingStyle = SpeakingStyle.Conversational,
        val ttsRate: Float = 1.0f,
        val ttsPitch: Float = 1.0f,
        /** Selected voice pack directory name, or null for system TTS (M7). */
        val voicePackId: String? = null,
        // Speaking clock
        val autoOffMinutes: Int = 60,
        val lastCustomIntervalSeconds: Int? = null,
        // Timer
        val timerScheduleName: String = "Game style",
        val lastTimerDurationSeconds: Long = 15 * 60,
        // Stopwatch
        val stopwatchAnnounceEverySeconds: Int = 0, // 0 = off
        val stopwatchSpeakLaps: Boolean = false,
        // Quiet hours
        val quietHoursEnabled: Boolean = false,
        /** Minutes since midnight, local time. Defaults 22:00 → 07:00. */
        val quietFromMinutes: Int = 22 * 60,
        val quietUntilMinutes: Int = 7 * 60,
        /** When true, timers still speak during quiet hours. */
        val quietAllowTimers: Boolean = true,
    )

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            theme = prefs[KEY_THEME]?.let(::themeOrDefault) ?: ThemeChoice.System,
            timeFormat = prefs[KEY_TIME_FORMAT]?.let(::formatOrDefault) ?: TimeFormat.System,
            showSeconds = prefs[KEY_SHOW_SECONDS] ?: true,
            showDate = prefs[KEY_SHOW_DATE] ?: true,
            speakingStyle = prefs[KEY_SPEAKING_STYLE]?.let(::styleOrDefault)
                ?: SpeakingStyle.Conversational,
            ttsRate = prefs[KEY_TTS_RATE] ?: 1.0f,
            ttsPitch = prefs[KEY_TTS_PITCH] ?: 1.0f,
            voicePackId = prefs[KEY_VOICE_PACK],
            autoOffMinutes = prefs[KEY_AUTO_OFF_MINUTES] ?: 60,
            lastCustomIntervalSeconds = prefs[KEY_LAST_CUSTOM_INTERVAL],
            timerScheduleName = prefs[KEY_TIMER_SCHEDULE] ?: "Game style",
            lastTimerDurationSeconds = prefs[KEY_LAST_TIMER_DURATION] ?: (15 * 60L),
            stopwatchAnnounceEverySeconds = prefs[KEY_SW_ANNOUNCE_EVERY] ?: 0,
            stopwatchSpeakLaps = prefs[KEY_SW_SPEAK_LAPS] ?: false,
            quietHoursEnabled = prefs[KEY_QUIET_ENABLED] ?: false,
            quietFromMinutes = prefs[KEY_QUIET_FROM] ?: (22 * 60),
            quietUntilMinutes = prefs[KEY_QUIET_UNTIL] ?: (7 * 60),
            quietAllowTimers = prefs[KEY_QUIET_ALLOW_TIMERS] ?: true,
        )
    }

    // Enum values are stored as their names; if a stored name no longer
    // exists after an app update, fall back to the default rather than crash.
    private fun themeOrDefault(name: String) =
        ThemeChoice.entries.find { it.name == name } ?: ThemeChoice.System

    private fun formatOrDefault(name: String) =
        TimeFormat.entries.find { it.name == name } ?: TimeFormat.System

    private fun styleOrDefault(name: String) =
        SpeakingStyle.entries.find { it.name == name } ?: SpeakingStyle.Conversational

    // --- Setters (one per user action; each writes exactly one key) --------

    suspend fun setTheme(theme: ThemeChoice) =
        dataStore.edit { it[KEY_THEME] = theme.name }

    suspend fun setTimeFormat(format: TimeFormat) =
        dataStore.edit { it[KEY_TIME_FORMAT] = format.name }

    suspend fun setShowSeconds(show: Boolean) =
        dataStore.edit { it[KEY_SHOW_SECONDS] = show }

    suspend fun setShowDate(show: Boolean) =
        dataStore.edit { it[KEY_SHOW_DATE] = show }

    suspend fun setSpeakingStyle(style: SpeakingStyle) =
        dataStore.edit { it[KEY_SPEAKING_STYLE] = style.name }

    suspend fun setTtsRate(rate: Float) =
        dataStore.edit { it[KEY_TTS_RATE] = rate.coerceIn(0.5f, 2.0f) }

    suspend fun setTtsPitch(pitch: Float) =
        dataStore.edit { it[KEY_TTS_PITCH] = pitch.coerceIn(0.5f, 2.0f) }

    suspend fun setVoicePackId(id: String?) =
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_VOICE_PACK) else prefs[KEY_VOICE_PACK] = id
        }

    suspend fun setAutoOffMinutes(minutes: Int) =
        dataStore.edit { it[KEY_AUTO_OFF_MINUTES] = minutes.coerceIn(5, 24 * 60) }

    suspend fun setLastCustomInterval(seconds: Int) =
        dataStore.edit { it[KEY_LAST_CUSTOM_INTERVAL] = seconds }

    suspend fun setTimerScheduleName(name: String) =
        dataStore.edit { it[KEY_TIMER_SCHEDULE] = name }

    suspend fun setLastTimerDuration(seconds: Long) =
        dataStore.edit { it[KEY_LAST_TIMER_DURATION] = seconds }

    suspend fun setStopwatchAnnounceEvery(seconds: Int) =
        dataStore.edit { it[KEY_SW_ANNOUNCE_EVERY] = seconds }

    suspend fun setStopwatchSpeakLaps(speak: Boolean) =
        dataStore.edit { it[KEY_SW_SPEAK_LAPS] = speak }

    suspend fun setQuietHoursEnabled(enabled: Boolean) =
        dataStore.edit { it[KEY_QUIET_ENABLED] = enabled }

    suspend fun setQuietFrom(minutesSinceMidnight: Int) =
        dataStore.edit { it[KEY_QUIET_FROM] = minutesSinceMidnight }

    suspend fun setQuietUntil(minutesSinceMidnight: Int) =
        dataStore.edit { it[KEY_QUIET_UNTIL] = minutesSinceMidnight }

    suspend fun setQuietAllowTimers(allow: Boolean) =
        dataStore.edit { it[KEY_QUIET_ALLOW_TIMERS] = allow }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_TIME_FORMAT = stringPreferencesKey("time_format")
        private val KEY_SHOW_SECONDS = booleanPreferencesKey("show_seconds")
        private val KEY_SHOW_DATE = booleanPreferencesKey("show_date")
        private val KEY_SPEAKING_STYLE = stringPreferencesKey("speaking_style")
        private val KEY_TTS_RATE = floatPreferencesKey("tts_rate")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_VOICE_PACK = stringPreferencesKey("voice_pack_id")
        private val KEY_AUTO_OFF_MINUTES = intPreferencesKey("auto_off_minutes")
        private val KEY_LAST_CUSTOM_INTERVAL = intPreferencesKey("last_custom_interval_s")
        private val KEY_TIMER_SCHEDULE = stringPreferencesKey("timer_schedule")
        private val KEY_LAST_TIMER_DURATION = longPreferencesKey("last_timer_duration_s")
        private val KEY_SW_ANNOUNCE_EVERY = intPreferencesKey("sw_announce_every_s")
        private val KEY_SW_SPEAK_LAPS = booleanPreferencesKey("sw_speak_laps")
        private val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        private val KEY_QUIET_FROM = intPreferencesKey("quiet_from_min")
        private val KEY_QUIET_UNTIL = intPreferencesKey("quiet_until_min")
        private val KEY_QUIET_ALLOW_TIMERS = booleanPreferencesKey("quiet_allow_timers")
    }
}

/** The app's one settings DataStore file ("settings.preferences_pb"). */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
