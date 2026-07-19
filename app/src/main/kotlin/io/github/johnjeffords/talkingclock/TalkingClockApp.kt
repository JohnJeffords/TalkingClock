package io.github.johnjeffords.talkingclock

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.johnjeffords.talkingclock.alarm.AlarmRinger
import io.github.johnjeffords.talkingclock.alarm.AlarmScheduler
import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import io.github.johnjeffords.talkingclock.announce.StopwatchController
import io.github.johnjeffords.talkingclock.announce.TimerController
import io.github.johnjeffords.talkingclock.data.AlarmRepository
import io.github.johnjeffords.talkingclock.data.EngineStateStore
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.data.settingsDataStore
import io.github.johnjeffords.talkingclock.domain.announce.QuietWindow
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.domain.time.SystemWallClock
import io.github.johnjeffords.talkingclock.service.AnnouncerService
import io.github.johnjeffords.talkingclock.speech.Announcer
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.SpeechAnnouncer
import io.github.johnjeffords.talkingclock.speech.TtsSpeaker
import io.github.johnjeffords.talkingclock.voicepack.VoicePackPlayer
import io.github.johnjeffords.talkingclock.voicepack.VoicePackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Clock
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

    // --- Alarms (M7.5; permission notes in the manifest + D-020) ---
    lateinit var alarmRepository: AlarmRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var alarmRinger: AlarmRinger
        private set

    /** The latest settings snapshot, readable synchronously by wiring code. */
    @Volatile
    var currentSettings: SettingsRepository.Settings = SettingsRepository.Settings()
        private set

    /** Wall clock shared by display and speech; its system zone is dynamic. */
    internal var wallClock: Clock = SystemWallClock

    /**
     * Process-lifetime scope for controllers and settings plumbing. Keeping it
     * on Main serializes tick-loop engine reads with UI/controller mutations.
     */
    internal var appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Completion handle for the ordered process-start initialization. */
    internal lateinit var initializationJob: Job
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(settingsDataStore)
        engineStateStore = EngineStateStore(settingsDataStore)
        ttsSpeaker = TtsSpeaker.create(this)
        speaker = ttsSpeaker
        voicePackStore = VoicePackStore(this)
        announcer = SpeechAnnouncer(speaker) { activePackPlayer }

        speakingClockController = SpeakingClockController(
            clock = wallClock,
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

        alarmRepository = AlarmRepository(settingsDataStore)
        alarmScheduler = AlarmScheduler(this)
        alarmRinger = AlarmRinger(
            context = this,
            announcer = announcer,
            scope = appScope,
            speakingStyle = { currentSettings.speakingStyle },
            onSnoozed = { alarm, ringAgainAt ->
                // Snooze re-books this alarm's slot at the exact snooze
                // moment; a repeating alarm's next real occurrence gets
                // re-booked when the snoozed ring is answered.
                alarmScheduler.scheduleAt(alarm, ringAgainAt)
            },
            onFinished = { alarm ->
                if (alarm.isOneShot) {
                    // One-shots disable themselves after ringing (frame 23).
                    appScope.launch { alarmRepository.setEnabled(alarm.id, false) }
                } else {
                    // Repeating: re-book the next real occurrence. Necessary
                    // because a snooze reuses the alarm's single booking slot
                    // — this puts the regular schedule back.
                    alarmScheduler.schedule(alarm)
                }
            },
            onArmSpeakingClock = { alarm ->
                // The signature feature: when the alarm is dismissed, the
                // speaking clock starts and runs while the user gets ready
                // (design frame 24's amber card). Auto-off ends it.
                alarm.handoffIntervalSeconds?.let { seconds ->
                    speakingClockController.arm(
                        SpeakInterval(seconds),
                        autoOff = Duration.ofMinutes(alarm.handoffMinutes.toLong()),
                    )
                }
            },
        )

        ContextCompat.registerReceiver(
            this,
            WallClockChangeReceiver(::handleWallClockChanged),
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        wireQuietHours()
        initializationJob = appScope.launch {
            // Settings (especially speech lead) must be applied before a
            // restored run latches them, and restore must finish before an
            // initial Idle emission is allowed to clear the saved state.
            applySettings(settingsRepository.settings.first())
            restoreSavedState()
            wireEnginePersistence()
            wireSettingsIntoConsumers()
        }
        // Re-sync AlarmManager with the stored alarms at every process start
        // (cheap, idempotent, and covers app-update process restarts that
        // BootReceiver doesn't).
        appScope.launch { alarmScheduler.rescheduleAll(alarmRepository.alarms.first()) }
    }

    /** Re-align all local wall-clock work after a time or zone change. */
    private fun handleWallClockChanged() {
        speakingClockController.realign()
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
            .onEach(::applySettings)
            .launchIn(appScope)
    }

    private suspend fun applySettings(settings: SettingsRepository.Settings) {
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
        stopwatchController.setSpeakElapsed(settings.stopwatchSpeakElapsed)
        stopwatchController.setSpeakLaps(settings.stopwatchSpeakLaps)
        // Same latency-compensation lead feeds both counting tools.
        val speechLead = Duration.ofMillis(settings.speechLeadMillis.toLong())
        timerController.speechLead = speechLead
        stopwatchController.speechLead = speechLead
        ttsSpeaker.setRate(settings.ttsRate)
        ttsSpeaker.setPitch(settings.ttsPitch)
        switchVoicePackIfNeeded(settings.voicePackId)
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
    private suspend fun restoreSavedState() {
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
