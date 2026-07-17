package io.github.johnjeffords.talkingclock.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

/**
 * Round-trips every setting through a real (temp-file-backed) DataStore —
 * write, read back, compare — plus the engine-state store's save/restore.
 * Catches key typos, default drift, and serialization mistakes in one place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // DataStore requires its own scope that outlives each test body.
    private val storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())

    private fun buildStore() = PreferenceDataStoreFactory.create(scope = storeScope) {
        tmp.newFile("test.preferences_pb")
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `defaults match the documented product defaults`() = runTest {
        val repo = SettingsRepository(buildStore())
        val s = repo.settings.first()
        assertEquals(ThemeChoice.System, s.theme)
        assertEquals(SettingsRepository.TimeFormat.System, s.timeFormat)
        assertEquals(true, s.showSeconds)
        assertEquals(SpeakingStyle.Conversational, s.speakingStyle)
        assertEquals(1000, s.speechLeadMillis)
        assertEquals(60, s.autoOffMinutes)
        assertEquals("Milestones", s.timerScheduleName)
        assertEquals(15 * 60L, s.lastTimerDurationSeconds)
        assertEquals(true, s.stopwatchSpeakElapsed)
        assertEquals(false, s.quietHoursEnabled)
        assertEquals(22 * 60, s.quietFromMinutes)
        assertEquals(7 * 60, s.quietUntilMinutes)
        assertNull(s.voicePackId)
    }

    @Test
    fun `settings round-trip`() = runTest {
        val repo = SettingsRepository(buildStore())

        repo.setTheme(ThemeChoice.Amoled)
        repo.setTimeFormat(SettingsRepository.TimeFormat.TwentyFourHour)
        repo.setShowSeconds(false)
        repo.setSpeakingStyle(SpeakingStyle.Formal)
        repo.setTtsRate(1.4f)
        repo.setSpeechLeadMillis(1500)
        repo.setAutoOffMinutes(30)
        repo.setTimerScheduleName("Minimal")
        repo.setLastTimerDuration(90)
        repo.setStopwatchSpeakElapsed(false)
        repo.setStopwatchSpeakLaps(true)
        repo.setQuietHoursEnabled(true)
        repo.setQuietFrom(21 * 60)
        repo.setQuietUntil(6 * 60 + 30)
        repo.setLastCustomInterval(7 * 60)

        val s = repo.settings.first()
        assertEquals(ThemeChoice.Amoled, s.theme)
        assertEquals(SettingsRepository.TimeFormat.TwentyFourHour, s.timeFormat)
        assertEquals(false, s.showSeconds)
        assertEquals(SpeakingStyle.Formal, s.speakingStyle)
        assertEquals(1.4f, s.ttsRate, 0.001f)
        assertEquals(1500, s.speechLeadMillis)
        assertEquals(30, s.autoOffMinutes)
        assertEquals("Minimal", s.timerScheduleName)
        assertEquals(90L, s.lastTimerDurationSeconds)
        assertEquals(false, s.stopwatchSpeakElapsed)
        assertEquals(true, s.stopwatchSpeakLaps)
        assertEquals(true, s.quietHoursEnabled)
        assertEquals(21 * 60, s.quietFromMinutes)
        assertEquals(6 * 60 + 30, s.quietUntilMinutes)
        assertEquals(7 * 60, s.lastCustomIntervalSeconds)
    }

    @Test
    fun `tts rate and pitch are clamped to sane bounds`() = runTest {
        val repo = SettingsRepository(buildStore())
        repo.setTtsRate(99f)
        repo.setTtsPitch(0.01f)
        val s = repo.settings.first()
        assertEquals(2.0f, s.ttsRate, 0.001f)
        assertEquals(0.5f, s.ttsPitch, 0.001f)
    }

    @Test
    fun `engine state store saves and restores a paused timer`() = runTest {
        val store = EngineStateStore(buildStore())
        store.saveTimer(Duration.ofMinutes(10), Duration.ofMinutes(4))

        val saved = store.loadTimer()!!
        assertEquals(Duration.ofMinutes(10), saved.duration)
        assertEquals(Duration.ofMinutes(4), saved.remaining)

        store.clearTimer()
        assertNull(store.loadTimer())
    }

    @Test
    fun `engine state store round-trips stopwatch laps`() = runTest {
        val store = EngineStateStore(buildStore())
        val laps = listOf(
            io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine.Lap(
                1, Duration.ofSeconds(70), Duration.ofSeconds(70),
            ),
            io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine.Lap(
                2, Duration.ofSeconds(120), Duration.ofSeconds(50),
            ),
        )
        store.saveStopwatch(Duration.ofSeconds(150), laps)

        val saved = store.loadStopwatch()!!
        assertEquals(Duration.ofSeconds(150), saved.elapsed)
        assertEquals(laps, saved.laps) // numbers + lap times rebuilt exactly

        store.clearStopwatch()
        assertNull(store.loadStopwatch())
    }

    @Test
    fun `overtime timer save clamps remaining to zero`() = runTest {
        val store = EngineStateStore(buildStore())
        store.saveTimer(Duration.ofMinutes(5), Duration.ofSeconds(-30))
        assertEquals(Duration.ZERO, store.loadTimer()!!.remaining)
    }
}
