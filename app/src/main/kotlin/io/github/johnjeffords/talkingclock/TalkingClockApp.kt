package io.github.johnjeffords.talkingclock

import android.app.Application
import android.os.SystemClock
import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import io.github.johnjeffords.talkingclock.announce.StopwatchController
import io.github.johnjeffords.talkingclock.announce.TimerController
import io.github.johnjeffords.talkingclock.data.EngineStateStore
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.data.settingsDataStore
import io.github.johnjeffords.talkingclock.domain.announce.QuietWindow
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.service.AnnouncerService
import io.github.johnjeffords.talkingclock.speech.Announcer
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.SpeechAnnouncer
import io.github.johnjeffords.talkingclock.speech.TtsSpeaker
import io.github.johnjeffords.talkingclock.voicepack.VoicePackPlayer
import io.github.johnjeffords.talkingclock.voicepack.VoicePackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

/**
 * The Application object — created once when the app process starts, before
 * any Activity. It owns the app's shared dependencies, wired together by
 * hand (manual dependency injection: this app is small enough that a DI
 * framework like Hilt would add build time, APK size, and indirection
 * without paying for itself — see docs/ARCHITECTURE.md).
 *
 * Beyond construction, this class runs the SETTINGS PLUMBING: one collector
 * pushes every settings change into the components that consume it, and two
 * more persist timer/stopwatch progress so a killed process restores as
 * paused (docs/ARCHITECTURE.md → Timekeeping).
 */
class TalkingClockApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var engineStateStore: EngineStateStore
        private set

    /**
     * The one app-wide speech engine. Created EAGERLY at process start
     * rather than lazily on first use: TTS initialization is asynchronous
     * and takes a moment, so warming it here means the user's very first
     * tap-to-speak actually speaks instead of merely starting the engine.
     */
    lateinit var speaker: Speaker
        private set

    /** Concrete handle for settings-only calls (rate/pitch). */
    private lateinit var ttsSpeaker: TtsSpeaker

    /** Voice-pack storage (import/list/delete). */
    lateinit var voicePackStore: VoicePackStore
        private set

    /** What everything speaks through: pack when selected, TTS fallback. */
    lateinit var announcer: Announcer
        private set

    /** The active pack's player, swapped live by the settings collector. */
    @Volatile
    private var activePackPlayer: VoicePackPlayer? = null

    /** Which pack id the current player was built for (null = system TTS). */
    private var activePackId: String? = null

    lateinit var speakingClockController: SpeakingClockController
        private set

    lateinit var timerController: TimerController
        private set

    lateinit var stopwatchController: StopwatchController
        private set

    /** The latest settings snapshot, readable synchronously by wiring code. */
    @Volatile
    var currentSettings: SettingsRepository.Settings = SettingsRepository.Settings()
        private set

    /** Process-lifetime scope for announce loops and settings plumbing. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(settingsDataStore)
        engineStateStore = EngineStateStore(settingsDataStore)
        ttsSpeaker = TtsSpeaker.create(this)
        speaker = ttsSpeaker
        voicePackStore = VoicePackStore(this)
        announcer = SpeechAnnouncer(speaker) { activePackPlayer }

        speakingClockController = SpeakingClockController(
            clock = java.time.Clock.systemDefaultZone(),
            announcer = announcer,
            scope = appScope,
            ensureServiceRunning = { AnnouncerService.ensureRunning(this) },
        )
        timerController = TimerController(
            // Monotonic time — never the wall clock (ARCHITECTURE.md).
            monotonicMs = SystemClock::elapsedRealtime,
            announcer = announcer,
            scope = appScope,
            ensureServiceRunning = { AnnouncerService.ensureRunning(this) },
        )
        stopwatchController = StopwatchController(
            monotonicMs = SystemClock::elapsedRealtime,
            announcer = announcer,
            scope = appScope,
            ensureServiceRunning = { AnnouncerService.ensureRunning(this) },
        )

        wireQuietHours()
        wireSettingsIntoConsumers()
        wireEnginePersistence()
        restoreSavedState()
    }

    /** Quiet-hours checks: clock/stopwatch silenced by the window; timers
     *  only when the user turned the allow-timers exception OFF. */
    private fun wireQuietHours() {
        fun window() = QuietWindow(
            currentSettings.quietFromMinutes,
            currentSettings.quietUntilMinutes,
        )

        val clockQuiet = {
            currentSettings.quietHoursEnabled && window().contains(LocalTime.now())
        }
        speakingClockController.isQuietNow = clockQuiet
        stopwatchController.isQuietNow = clockQuiet
        timerController.isQuietNow = {
            currentSettings.quietHoursEnabled &&
                !currentSettings.quietAllowTimers &&
                window().contains(LocalTime.now())
        }
    }

    /** One collector pushes each settings change to every consumer. */
    private fun wireSettingsIntoConsumers() {
        settingsRepository.settings
            .onEach { settings ->
                currentSettings = settings
                speakingClockController.speakingStyle = settings.speakingStyle
                speakingClockController.defaultAutoOff =
                    Duration.ofMinutes(settings.autoOffMinutes.toLong())
                speakingClockController.lastCustomInterval =
                    settings.lastCustomIntervalSeconds
                        ?.takeIf { it in SpeakInterval.MIN_SECONDS..SpeakInterval.MAX_SECONDS }
                        ?.let(::SpeakInterval)
                timerController.selectSchedule(
                    AnnouncementSchedule.BUILT_INS.find { it.name == settings.timerScheduleName }
                        ?: AnnouncementSchedule.GAME,
                )
                timerController.restoreLastDuration(
                    Duration.ofSeconds(settings.lastTimerDurationSeconds),
                )
                stopwatchController.setAnnounceEvery(
                    settings.stopwatchAnnounceEverySeconds
                        .takeIf { it > 0 }
                        ?.let { Duration.ofSeconds(it.toLong()) },
                )
                stopwatchController.setSpeakLaps(settings.stopwatchSpeakLaps)
                ttsSpeaker.setRate(settings.ttsRate)
                ttsSpeaker.setPitch(settings.ttsPitch)
                switchVoicePackIfNeeded(settings.voicePackId)
            }
            .launchIn(appScope)
    }

    /** Build/tear down the pack player when the voice-source setting changes. */
    private suspend fun switchVoicePackIfNeeded(packId: String?) {
        if (packId == activePackId) return
        activePackPlayer?.release()
        activePackPlayer = null
        activePackId = packId
        if (packId != null) {
            // A deleted/corrupt pack silently reverts to TTS — the Announcer
            // falls back whenever activePackPlayer is null.
            voicePackStore.loadPack(packId)?.let { pack ->
                activePackPlayer = VoicePackPlayer(voicePackStore, pack, appScope)
            }
        }
    }

    /**
     * Persist progress so a killed process restores honestly. Saves are
     * throttled to whole-second changes (distinctUntilChanged), cleared the
     * moment a run is reset — a stale save must never resurrect a dismissed
     * timer.
     */
    private fun wireEnginePersistence() {
        timerController.state
            .map { st ->
                Triple(st.snapshot.phase, st.snapshot.duration, st.snapshot.remaining.seconds)
            }
            .distinctUntilChanged()
            .onEach { (phase, duration, _) ->
                when (phase) {
                    TimerEngine.Phase.Idle -> engineStateStore.clearTimer()
                    else -> engineStateStore.saveTimer(
                        duration,
                        timerController.state.value.snapshot.remaining,
                    )
                }
                // The keypad prefill persists too.
                if (phase != TimerEngine.Phase.Idle) {
                    settingsRepository.setLastTimerDuration(duration.seconds)
                }
            }
            .launchIn(appScope)

        stopwatchController.state
            .map { st -> Pair(st.snapshot.phase, st.snapshot.elapsed.seconds) }
            .distinctUntilChanged()
            .onEach { (phase, _) ->
                when (phase) {
                    StopwatchEngine.Phase.Idle -> engineStateStore.clearStopwatch()
                    else -> engineStateStore.saveStopwatch(
                        stopwatchController.state.value.snapshot.elapsed,
                        stopwatchController.state.value.snapshot.laps,
                    )
                }
            }
            .launchIn(appScope)

        // Persist the speaking clock's last custom interval when it changes.
        speakingClockController.state
            .map { speakingClockController.lastCustomInterval }
            .distinctUntilChanged()
            .onEach { custom ->
                custom?.let { settingsRepository.setLastCustomInterval(it.seconds) }
            }
            .launchIn(appScope)
    }

    /** Bring back interrupted runs (as paused — see the controllers' docs). */
    private fun restoreSavedState() {
        appScope.launch {
            engineStateStore.loadTimer()?.let { saved ->
                timerController.restorePaused(saved.duration, saved.remaining)
            }
            engineStateStore.loadStopwatch()?.let { saved ->
                if (!saved.elapsed.isZero) {
                    stopwatchController.restorePaused(saved.elapsed, saved.laps)
                }
            }
        }
    }
}
