package io.github.johnjeffords.talkingclock.announce

import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.domain.announce.nextAnnouncementTime
import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.speech.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * The speaking clock itself: while armed, announces the time aloud at every
 * wall-clock boundary of the chosen interval, until disarmed or auto-off.
 *
 * There is exactly ONE of these, app-wide (created in TalkingClockApp), and
 * it is the single source of truth for "is the speaking clock on?" — the
 * Clock screen, the foreground service, and the notification all just
 * observe [state].
 *
 * The controller deliberately knows nothing about Android services. Keeping
 * the process alive with the screen off requires a foreground service, but
 * that's a *platform obligation*, not announcing logic — so the controller
 * only pokes [ensureServiceRunning] when armed (a lambda; the real one
 * starts AnnouncerService, tests pass a no-op). Stopping is the SERVICE's
 * decision: it watches every controller and stops itself when all are idle
 * — if the controllers stopped it directly, the timer finishing would kill
 * the service out from under a still-armed speaking clock (or vice versa).
 * This split is also what lets the entire announce/auto-off lifecycle run
 * under a virtual-time test with a fake speaker (SpeakingClockControllerTest).
 *
 * Timing approach: sleep in short slices (≤ [MAX_SLICE] at a time),
 * recomputing everything from the injected [clock] on each wake. The
 * per-slice recompute makes the loop self-correcting: an NTP adjustment or
 * timezone change is picked up within a minute without any broadcast
 * receivers, and drift never accumulates.
 */
class SpeakingClockController(
    private val clock: Clock,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val ensureServiceRunning: () -> Unit,
) {

    /** Announcing state, as observed by the UI and the notification. */
    data class State(
        /** The armed interval, or null when the speaking clock is off. */
        val interval: SpeakInterval? = null,
        /** Wall-clock moment of the next announcement (null when off). */
        val nextAt: LocalDateTime? = null,
        /** When the speaking clock switches itself off (null when off). */
        val autoOffAt: LocalDateTime? = null,
    ) {
        val isArmed: Boolean get() = interval != null
    }

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    /**
     * The most recent custom (non-preset) interval, occupying the
     * "last custom" slot in the interval menu. Persisted via settings;
     * restored at app start by the settings wiring in TalkingClockApp.
     */
    var lastCustomInterval: SpeakInterval? = null

    // --- Settings, pushed in by the app-level settings collector ------------
    // (Kept as plain mutable properties rather than constructor params so the
    // controller stays constructible before settings load, and reacts live.)

    /** How announcements are phrased. */
    @Volatile
    var speakingStyle: SpeakingStyle = SpeakingStyle.Conversational

    /** Auto-off used when [arm] isn't given an explicit duration. */
    @Volatile
    var defaultAutoOff: Duration = DEFAULT_AUTO_OFF

    /**
     * Quiet hours check, injected by the app wiring. While quiet, boundary
     * announcements are SKIPPED but scheduling continues — the clock stays
     * armed and resumes speaking the moment the window ends. (The Clock
     * screen still shows the armed state, so nothing stops "silently";
     * quiet hours are themselves a user-visible setting.)
     */
    @Volatile
    var isQuietNow: () -> Boolean = { false }

    private var announceJob: Job? = null

    /**
     * Arm the speaking clock at [interval] (replacing any current interval),
     * with automatic shutoff after [autoOff] (defaults to the user's
     * configured [defaultAutoOff]) — the design's "Auto-off in NN min" pill.
     * Auto-off is a deliberate safety: a clock armed at 15 s and forgotten
     * shouldn't chatter for a week.
     */
    fun arm(interval: SpeakInterval, autoOff: Duration? = null) {
        if (interval !in SpeakInterval.PRESETS) lastCustomInterval = interval

        val now = LocalDateTime.now(clock)
        stateFlow.value = State(
            interval = interval,
            nextAt = nextAnnouncementTime(now, interval),
            autoOffAt = now.plus(autoOff ?: defaultAutoOff),
        )
        ensureServiceRunning()

        // One announce loop at a time: replace, never stack.
        announceJob?.cancel()
        announceJob = scope.launch { announceLoop() }
    }

    /** Turn the speaking clock off (user action, auto-off, or fatal error).
     *  The service notices the idle state itself and stops when nothing
     *  else (e.g. a running timer) still needs it. */
    fun disarm() {
        announceJob?.cancel()
        announceJob = null
        speaker.stop()
        stateFlow.value = State()
    }

    /**
     * The armed life: sleep to the next boundary, announce, repeat — until
     * auto-off. All times re-read from [clock] on every wake (see class doc).
     */
    private suspend fun announceLoop() {
        while (true) {
            val st = stateFlow.value
            val interval = st.interval ?: return
            val now = LocalDateTime.now(clock)

            // Auto-off reached: shut down visibly (state clears, service
            // stops, notification disappears — a stop must never be silent).
            val autoOffAt = st.autoOffAt
            if (autoOffAt != null && !now.isBefore(autoOffAt)) {
                disarm()
                return
            }

            val nextAt = st.nextAt ?: return
            val untilNext = Duration.between(now, nextAt)

            if (untilNext > Duration.ZERO) {
                // Not there yet: sleep at most a slice, then re-check.
                delay(minOf(untilNext, MAX_SLICE).toMillis())
                continue
            }

            // Boundary reached — announce THIS moment's time (unless quiet
            // hours are active, in which case this boundary is skipped and
            // scheduling simply continues). Sub-minute intervals include
            // seconds, otherwise four identical announcements per minute
            // would be useless. Clock priority: yields to timer cues if
            // both land on the same instant.
            if (!isQuietNow()) {
                speaker.speak(
                    Phrasebook.timeAnnouncement(
                        time = now.toLocalTime(),
                        style = speakingStyle,
                        includeSeconds = interval.seconds < 60,
                    ),
                    Speaker.PRIORITY_CLOCK,
                )
            }
            stateFlow.value = st.copy(nextAt = nextAnnouncementTime(now, interval))
        }
    }

    companion object {
        /** Re-check cadence while sleeping toward a boundary (see class doc). */
        private val MAX_SLICE: Duration = Duration.ofSeconds(30)

        /** Default auto-off: an hour of announcements, then quiet. */
        val DEFAULT_AUTO_OFF: Duration = Duration.ofMinutes(60)
    }
}
