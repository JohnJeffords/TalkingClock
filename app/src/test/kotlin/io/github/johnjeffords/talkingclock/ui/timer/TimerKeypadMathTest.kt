package io.github.johnjeffords.talkingclock.ui.timer

import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.ui.timer.TimerViewModel.Companion.digitsFor
import io.github.johnjeffords.talkingclock.ui.timer.TimerViewModel.Companion.durationFor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * The keypad's digit↔duration math. Google-Clock semantics: digits fill
 * from the right (H MM SS), and minutes beyond 59 are taken literally —
 * typing 90:00 genuinely means ninety minutes.
 */
class TimerKeypadMathTest {

    @Test
    fun `typed digits read as H MM SS from the right`() {
        assertEquals(Duration.ofSeconds(5), durationFor("5"))
        assertEquals(Duration.ofSeconds(90), durationFor("130"))       // 1:30
        assertEquals(Duration.ofMinutes(15), durationFor("1500"))      // 15:00
        assertEquals(Duration.ofHours(2), durationFor("20000"))        // 2:00:00
        assertEquals(Duration.ZERO, durationFor(""))
    }

    @Test
    fun `oversized minutes are taken literally like Google Clock`() {
        // "9000" = 90 minutes 00 seconds, not an error.
        assertEquals(Duration.ofMinutes(90), durationFor("9000"))
    }

    @Test
    fun `every duration the keypad can express is speakable`() {
        // Regression: the keypad happily produced 60 h+, but the start
        // announcement threw while rendering it — after the engine had
        // already started, so the process went down mid-run.
        assertEquals(Duration.ofHours(60), durationFor("600000"))
        assertEquals(
            "Timer started: sixty hours",
            Phrasebook.timerStarted(durationFor("600000")),
        )
        // The keypad's maximum must survive the same path.
        Phrasebook.timerStarted(durationFor("995959"))
    }

    @Test
    fun `digitsFor is the inverse for displayable durations`() {
        assertEquals("1500", digitsFor(Duration.ofMinutes(15)))
        assertEquals("130", digitsFor(Duration.ofSeconds(90)))
        assertEquals("10000", digitsFor(Duration.ofHours(1)))
        assertEquals("5", digitsFor(Duration.ofSeconds(5)))
    }

    @Test
    fun `round trip preserves the duration`() {
        listOf(
            Duration.ofSeconds(15),
            Duration.ofMinutes(7),
            Duration.ofMinutes(90),
            Duration.ofHours(3).plusMinutes(2).plusSeconds(1),
        ).forEach { d ->
            assertEquals(d, durationFor(digitsFor(d)))
        }
    }
}
