package io.github.johnjeffords.talkingclock.domain.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDateTime

/**
 * Emits the current time once per second, aligned to the real second
 * boundary (…:00.000, …:01.000, …), for the ticking clock display.
 *
 * Why aligned, and why this matters for battery/framerate: a naive
 * `while (true) { emit(now); delay(1000) }` drifts — each loop the delay
 * starts a few milliseconds late, so over time the clock updates at, say,
 * :00.4, :01.4, :02.4… and eventually skips or double-shows a second. Worse,
 * people notice a clock that ticks slightly after the real second. Instead we
 * compute the exact milliseconds remaining until the next whole second and
 * sleep only that long, so every emission lands right on the boundary and any
 * drift is corrected on the very next tick. It's also exactly one emission
 * per second — no busy-looping (see docs/ARCHITECTURE.md → Timekeeping).
 *
 * The [clock] is injected so tests can drive it with a fixed/fake clock.
 */
class SecondTicker(private val clock: Clock) {

    fun ticks(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(millisUntilNextSecond(clock.millis()))
        }
    }

    companion object {
        /**
         * Milliseconds from [epochMillis] until the next whole second.
         * Pure arithmetic, so it's unit-tested directly (SecondTickerTest):
         * at …000 it returns a full 1000 (wait a whole second), and at …600
         * it returns 400 (wait the remainder). Never returns 0, which would
         * spin the loop.
         */
        fun millisUntilNextSecond(epochMillis: Long): Long {
            val remainder = epochMillis % 1000L
            return 1000L - remainder
        }
    }
}
