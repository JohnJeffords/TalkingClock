package io.github.johnjeffords.talkingclock

import android.app.Application
import android.os.SystemClock
import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import io.github.johnjeffords.talkingclock.announce.StopwatchController
import io.github.johnjeffords.talkingclock.announce.TimerController
import io.github.johnjeffords.talkingclock.service.AnnouncerService
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * The Application object — created once when the app process starts, before
 * any Activity. It owns the app's shared dependencies, wired together by
 * hand (manual dependency injection: this app is small enough that a DI
 * framework like Hilt would add build time, APK size, and indirection
 * without paying for itself — see docs/ARCHITECTURE.md).
 */
class TalkingClockApp : Application() {

    /**
     * The one app-wide speech engine. Created EAGERLY at process start
     * rather than lazily on first use: TTS initialization is asynchronous
     * and takes a moment, so warming it here means the user's very first
     * tap-to-speak actually speaks instead of merely starting the engine.
     * The OS reclaims it with the process; no explicit shutdown needed for
     * an app-lifetime singleton.
     */
    lateinit var speaker: Speaker
        private set

    /**
     * The one speaking clock (see SpeakingClockController's class doc for
     * why it's a process-wide singleton). Its announce loop runs on a
     * process-lifetime scope; AnnouncerService keeps the process alive
     * while armed.
     */
    lateinit var speakingClockController: SpeakingClockController
        private set

    /** The one talking timer (one active timer at a time, by design). */
    lateinit var timerController: TimerController
        private set

    /** The one stopwatch. */
    lateinit var stopwatchController: StopwatchController
        private set

    /** Process-lifetime scope for the announce loops. SupervisorJob so a
     *  crashed child never kills unrelated app-scope work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        speaker = TtsSpeaker.create(this)
        speakingClockController = SpeakingClockController(
            clock = Clock.systemDefaultZone(),
            speaker = speaker,
            scope = appScope,
            ensureServiceRunning = { AnnouncerService.ensureRunning(this) },
        )
        timerController = TimerController(
            // Monotonic time for the countdown — never the wall clock
            // (docs/ARCHITECTURE.md → Timekeeping rules).
            monotonicMs = SystemClock::elapsedRealtime,
            speaker = speaker,
            scope = appScope,
            ensureServiceRunning = { AnnouncerService.ensureRunning(this) },
        )
        stopwatchController = StopwatchController(
            monotonicMs = SystemClock::elapsedRealtime,
            speaker = speaker,
            scope = appScope,
            ensureServiceRunning = { AnnouncerService.ensureRunning(this) },
        )
    }
}
