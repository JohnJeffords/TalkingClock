package io.github.johnjeffords.talkingclock.ui.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.domain.time.ClockReadout
import io.github.johnjeffords.talkingclock.domain.time.SecondTicker
import io.github.johnjeffords.talkingclock.domain.time.TimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

/**
 * Everything the Clock screen needs to draw one frame, precomputed. The UI
 * renders this and nothing else.
 *
 * @property readout the formatted wall-clock display (null before first tick).
 * @property showDate whether the date line is drawn (a setting).
 * @property armedInterval the speaking-clock interval, or null when off.
 * @property nextIn time remaining until the next announcement ("Next in
 *   3:12"), or null when off.
 * @property autoOffIn time remaining until automatic shutoff, or null when off.
 */
data class ClockUiState(
    val readout: ClockReadout? = null,
    val showDate: Boolean = true,
    val armedInterval: SpeakInterval? = null,
    val nextIn: Duration? = null,
    val autoOffIn: Duration? = null,
)

/**
 * The Clock screen's state holder. It merges three inputs — the per-second
 * tick, the user's display-format choice, and the speaking clock's armed
 * state — into one [ClockUiState], re-emitted every second. The per-second
 * re-emission is also what makes the "Next in m:ss" countdown tick: the
 * controller's state only changes per announcement, but combining it with
 * the ticker recomputes the remaining time each second.
 *
 * (What a ViewModel is, for anyone new: it holds screen state and survives
 * configuration changes like screen rotation. The UI just draws `uiState`;
 * it never computes times itself. That split keeps the logic testable
 * without a screen.)
 */
class ClockViewModel(
    clock: Clock,
    locale: Locale,
    private val speakingClock: SpeakingClockController,
    settingsFlow: Flow<SettingsRepository.Settings>,
) : ViewModel() {

    /** The device's own 12/24-hour preference, reported by the Activity —
     *  used only when the time-format SETTING says "follow system". */
    private val systemUses24Hour = MutableStateFlow(false)

    private val ticker = SecondTicker(clock)

    /**
     * The screen state, refreshed every second while the screen is visible.
     * `WhileSubscribed(5000)` stops the ticking coroutine 5 s after the
     * screen goes away and restarts on return — a backgrounded app must not
     * burn CPU updating a display nobody sees. (The speaking clock itself is
     * NOT affected: its loop lives in the controller, not here.)
     */
    val uiState: StateFlow<ClockUiState> =
        combine(
            ticker.ticks(),
            settingsFlow,
            systemUses24Hour,
            speakingClock.state,
        ) { now, settings, system24h, armed ->
            val use24Hour = when (settings.timeFormat) {
                SettingsRepository.TimeFormat.System -> system24h
                SettingsRepository.TimeFormat.TwelveHour -> false
                SettingsRepository.TimeFormat.TwentyFourHour -> true
            }
            ClockUiState(
                readout = TimeFormatter.format(now, use24Hour, settings.showSeconds, locale),
                showDate = settings.showDate,
                armedInterval = armed.interval,
                nextIn = armed.nextAt?.let { remainingUntil(now, it) },
                autoOffIn = armed.autoOffAt?.let { remainingUntil(now, it) },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = ClockUiState(),
        )

    /** The custom interval most recently used, for the menu's "last custom" slot. */
    val lastCustomInterval: SpeakInterval? get() = speakingClock.lastCustomInterval

    /** Arm the speaking clock ([interval]) or turn it off (null). */
    fun selectInterval(interval: SpeakInterval?) {
        if (interval == null) speakingClock.disarm() else speakingClock.arm(interval)
    }

    /** Called by the route with the device's own 12/24-hour preference —
     *  the value used when the time-format setting is "follow system". */
    fun setSystemUses24Hour(use24Hour: Boolean) {
        systemUses24Hour.value = use24Hour
    }

    /** Clamp to zero so a just-passed moment never renders as negative. */
    private fun remainingUntil(now: LocalDateTime, target: LocalDateTime): Duration {
        val d = Duration.between(now, target)
        return if (d.isNegative) Duration.ZERO else d
    }

    companion object {
        /** Builds the real ViewModel with the system clock and app singletons. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TalkingClockApp
                ClockViewModel(
                    clock = app.wallClock,
                    locale = Locale.getDefault(),
                    speakingClock = app.speakingClockController,
                    settingsFlow = app.settingsRepository.settings,
                )
            }
        }
    }
}
