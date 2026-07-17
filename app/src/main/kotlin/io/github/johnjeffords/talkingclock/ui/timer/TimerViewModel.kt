package io.github.johnjeffords.talkingclock.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.announce.TimerController
import io.github.johnjeffords.talkingclock.domain.timer.AnnouncementSchedule
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import io.github.johnjeffords.talkingclock.domain.timer.nextMark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration

/**
 * Everything the Timer screen draws, precomputed — see docs on each field.
 * The keypad models Google Clock's entry: digits shift in from the right
 * ("1", "5", "0", "0" → 15:00), and minutes/seconds beyond 59 are taken
 * literally (typing 90:00 means ninety minutes).
 */
data class TimerUiState(
    val phase: TimerEngine.Phase = TimerEngine.Phase.Idle,
    /** Remaining time while running/paused (clamped at zero for display). */
    val remaining: Duration = Duration.ZERO,
    /** How far past zero, once finished ("+0:37" in red). */
    val overtime: Duration = Duration.ZERO,
    /** Fraction of the run still to go, 1.0 → 0.0 (drives the ring). */
    val progress: Float = 1f,
    /** Raw keypad digits, rightmost-first semantics ("1500" = 15:00). */
    val typedDigits: String = "",
    /** [typedDigits] interpreted as a duration. */
    val typedDuration: Duration = Duration.ZERO,
    /** Start is enabled only for a positive typed duration. */
    val canStart: Boolean = false,
    val schedule: AnnouncementSchedule = AnnouncementSchedule.GAME,
    /** The next spoken mark, for the "Next: …" indicator (null = countdown/none). */
    val nextMarkAt: Duration? = null,
)

/**
 * State holder for the Timer screen: merges the controller's live state
 * with the local keypad entry. The keypad is UI-side state (the controller
 * doesn't care what's half-typed); everything about the RUNNING timer comes
 * from [TimerController], which keeps ticking whether or not this screen —
 * or the whole app — is visible.
 */
class TimerViewModel(
    private val controller: TimerController,
) : ViewModel() {

    // Keypad digits being typed. Pre-filled from the last-used duration so
    // the everyday case ("same 15 minutes as yesterday") is just: Start.
    private val typedDigits =
        MutableStateFlow(digitsFor(controller.state.value.lastDuration))

    val uiState: StateFlow<TimerUiState> =
        combine(controller.state, typedDigits) { ctrl, digits ->
            val snap = ctrl.snapshot
            val typedDuration = durationFor(digits)
            TimerUiState(
                phase = snap.phase,
                remaining = if (snap.remaining.isNegative) Duration.ZERO else snap.remaining,
                overtime = snap.overtime,
                progress = if (snap.duration.isZero) {
                    1f
                } else {
                    (snap.remaining.toMillis().coerceAtLeast(0).toFloat() /
                        snap.duration.toMillis())
                },
                typedDigits = digits,
                typedDuration = typedDuration,
                canStart = !typedDuration.isZero,
                schedule = ctrl.schedule,
                nextMarkAt = if (snap.phase == TimerEngine.Phase.Running) {
                    nextMark(ctrl.schedule, snap.duration, snap.remaining)
                } else {
                    null
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = TimerUiState(),
        )

    // --- Keypad -------------------------------------------------------------

    /** A digit key was tapped; ignore input past 6 digits (H MM SS is full). */
    fun typeDigit(digit: Char) {
        require(digit.isDigit()) { "Keypad only types digits, got '$digit'" }
        val current = typedDigits.value
        // A leading zero is meaningless — "0" then "5" should read 5, not 05.
        if (current.isEmpty() && digit == '0') return
        if (current.length >= 6) return
        typedDigits.value = current + digit
    }

    /** Backspace: drop the most recent digit. */
    fun deleteDigit() {
        typedDigits.value = typedDigits.value.dropLast(1)
    }

    /** The "00" convenience key. */
    fun typeDoubleZero() {
        typeDigit('0'); typeDigit('0')
    }

    // --- Timer actions (thin passthroughs to the controller) ----------------

    fun start() {
        val duration = durationFor(typedDigits.value)
        if (!duration.isZero) controller.start(duration)
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()

    fun reset() {
        controller.reset()
        // Refill the keypad with what just ran — the design's "last-used
        // value stays for next time".
        typedDigits.value = digitsFor(controller.state.value.lastDuration)
    }

    fun selectSchedule(schedule: AnnouncementSchedule) = controller.selectSchedule(schedule)

    companion object {
        /** "1500" → 15 min. Pads to HMMSS and reads hours/minutes/seconds. */
        fun durationFor(digits: String): Duration {
            if (digits.isEmpty()) return Duration.ZERO
            val padded = digits.padStart(6, '0')
            val hours = padded.substring(0, 2).toLong()
            val minutes = padded.substring(2, 4).toLong()
            val seconds = padded.substring(4, 6).toLong()
            return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds)
        }

        /** 15 min → "1500": the inverse, for pre-filling the keypad. */
        fun digitsFor(duration: Duration): String {
            val h = duration.toHours()
            val m = duration.toMinutes() % 60
            val s = duration.seconds % 60
            return "%02d%02d%02d".format(h, m, s).trimStart('0')
        }

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TalkingClockApp
                TimerViewModel(app.timerController)
            }
        }
    }
}
