package io.github.johnjeffords.talkingclock.ui.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.domain.time.ClockReadout
import io.github.johnjeffords.talkingclock.domain.time.SecondTicker
import io.github.johnjeffords.talkingclock.domain.time.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.util.Locale

/**
 * The Clock screen's state holder. It combines two inputs — the per-second
 * tick and the user's display-format choice — into the [ClockReadout] the
 * screen draws, and re-emits once per second.
 *
 * (What a ViewModel is, for anyone new: it holds screen state and survives
 * configuration changes like screen rotation. The UI just reads `readout`
 * and draws it; the UI never computes the time itself. This split is what
 * lets us test the time logic without a screen.)
 *
 * `clock` and `locale` are constructor-injected so tests can pass a fixed
 * clock and a known locale; the app uses the real system clock via [Factory].
 */
class ClockViewModel(
    clock: Clock,
    locale: Locale,
) : ViewModel() {

    /** The user's 12/24-hour and show-seconds choices. Settings wiring (M6)
     *  will feed real values in; for now the Activity sets 12/24h from the
     *  system and seconds default on. */
    private val format = MutableStateFlow(ClockFormat(use24Hour = false, showSeconds = true))

    private val ticker = SecondTicker(clock)

    /**
     * The current formatted time, refreshed every second. Starts null until
     * the first tick arrives (the screen shows nothing for that first instant
     * rather than a stale/placeholder time).
     *
     * `WhileSubscribed(5000)` stops the ticking coroutine 5s after the screen
     * goes away and restarts it when the screen returns — so a backgrounded
     * app isn't burning a coroutine updating a clock nobody is looking at.
     */
    val readout: StateFlow<ClockReadout?> =
        combine(ticker.ticks(), format) { now, fmt ->
            TimeFormatter.format(now, fmt.use24Hour, fmt.showSeconds, locale)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = null,
        )

    /** Called by the Activity to match the device's 12/24-hour system setting. */
    fun setUse24Hour(use24Hour: Boolean) {
        format.value = format.value.copy(use24Hour = use24Hour)
    }

    companion object {
        /** Builds the real ViewModel with the system clock and locale. */
        val Factory = viewModelFactory {
            initializer { ClockViewModel(Clock.systemDefaultZone(), Locale.getDefault()) }
        }
    }
}

/** The display-format inputs the Clock screen cares about. */
data class ClockFormat(
    val use24Hour: Boolean,
    val showSeconds: Boolean,
)
