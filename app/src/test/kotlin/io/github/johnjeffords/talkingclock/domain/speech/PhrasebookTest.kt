package io.github.johnjeffords.talkingclock.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalTime

/**
 * Tests every phrasing rule in [Phrasebook]. The design's style picker shows
 * 10:24 as the canonical preview time, so those exact sentences are asserted
 * first, then the edge cases (on the hour, "oh five" minutes, noon/midnight).
 */
class PhrasebookTest {

    private val tenTwentyFour = LocalTime.of(10, 24)

    // --- The design's preview sentences, verbatim ---

    @Test
    fun `conversational matches the design preview`() {
        assertEquals(
            "It's ten twenty-four",
            Phrasebook.timeAnnouncement(tenTwentyFour, SpeakingStyle.Conversational),
        )
    }

    @Test
    fun `digits matches the design preview`() {
        assertEquals(
            "It's 10:24 AM",
            Phrasebook.timeAnnouncement(tenTwentyFour, SpeakingStyle.Digits),
        )
    }

    @Test
    fun `formal matches the design preview`() {
        assertEquals(
            "It's twenty-four minutes past ten",
            Phrasebook.timeAnnouncement(tenTwentyFour, SpeakingStyle.Formal),
        )
    }

    @Test
    fun `24-hour style speaks the 24-hour reading`() {
        assertEquals(
            "It's fourteen twenty-four",
            Phrasebook.timeAnnouncement(LocalTime.of(14, 24), SpeakingStyle.TwentyFourHour),
        )
    }

    // --- On-the-hour special cases ---

    @Test
    fun `conversational on the hour says o'clock`() {
        assertEquals(
            "It's ten o'clock",
            Phrasebook.timeAnnouncement(LocalTime.of(10, 0), SpeakingStyle.Conversational),
        )
    }

    @Test
    fun `24-hour on the hour says hundred`() {
        assertEquals(
            "It's fourteen hundred",
            Phrasebook.timeAnnouncement(LocalTime.of(14, 0), SpeakingStyle.TwentyFourHour),
        )
    }

    @Test
    fun `formal on the hour says o'clock`() {
        assertEquals(
            "It's ten o'clock",
            Phrasebook.timeAnnouncement(LocalTime.of(10, 0), SpeakingStyle.Formal),
        )
    }

    // --- Single-digit minutes ("oh five", "one minute past") ---

    @Test
    fun `conversational single-digit minute says oh`() {
        assertEquals(
            "It's ten oh five",
            Phrasebook.timeAnnouncement(LocalTime.of(10, 5), SpeakingStyle.Conversational),
        )
    }

    @Test
    fun `formal one minute past is singular`() {
        assertEquals(
            "It's one minute past ten",
            Phrasebook.timeAnnouncement(LocalTime.of(10, 1), SpeakingStyle.Formal),
        )
    }

    // --- Noon and midnight on the 12-hour face ---

    @Test
    fun `midnight conversational reads twelve`() {
        assertEquals(
            "It's twelve o'clock",
            Phrasebook.timeAnnouncement(LocalTime.of(0, 0), SpeakingStyle.Conversational),
        )
    }

    @Test
    fun `midnight digits reads 12 AM and noon 12 PM`() {
        assertEquals(
            "It's 12:00 AM",
            Phrasebook.timeAnnouncement(LocalTime.of(0, 0), SpeakingStyle.Digits),
        )
        assertEquals(
            "It's 12:00 PM",
            Phrasebook.timeAnnouncement(LocalTime.of(12, 0), SpeakingStyle.Digits),
        )
    }

    @Test
    fun `afternoon conversational uses the 12-hour face`() {
        assertEquals(
            "It's two forty-five",
            Phrasebook.timeAnnouncement(LocalTime.of(14, 45), SpeakingStyle.Conversational),
        )
    }

    // --- Seconds suffix (used by sub-minute speaking-clock intervals) ---

    @Test
    fun `includeSeconds appends the seconds phrase`() {
        assertEquals(
            "It's ten twenty-four and thirty seconds",
            Phrasebook.timeAnnouncement(
                LocalTime.of(10, 24, 30),
                SpeakingStyle.Conversational,
                includeSeconds = true,
            ),
        )
    }

    @Test
    fun `includeSeconds is singular for one second`() {
        assertEquals(
            "It's ten twenty-four and one second",
            Phrasebook.timeAnnouncement(
                LocalTime.of(10, 24, 1),
                SpeakingStyle.Conversational,
                includeSeconds = true,
            ),
        )
    }

    @Test
    fun `includeSeconds stays silent at zero seconds`() {
        assertEquals(
            "It's ten twenty-four",
            Phrasebook.timeAnnouncement(
                LocalTime.of(10, 24, 0),
                SpeakingStyle.Conversational,
                includeSeconds = true,
            ),
        )
    }

    // --- Timer phrases ---

    @Test
    fun `timer remaining phrases read naturally`() {
        assertEquals("Five minutes remaining", Phrasebook.timerRemaining(java.time.Duration.ofMinutes(5)))
        assertEquals("Thirty seconds remaining", Phrasebook.timerRemaining(java.time.Duration.ofSeconds(30)))
        assertEquals("One minute remaining", Phrasebook.timerRemaining(java.time.Duration.ofMinutes(1)))
        assertEquals(
            "One minute, thirty seconds remaining",
            Phrasebook.timerRemaining(java.time.Duration.ofSeconds(90)),
        )
    }

    @Test
    fun `halfway says the real time first`() {
        assertEquals(
            "Seven minutes, thirty seconds remaining. Halfway there",
            Phrasebook.timerHalfway(java.time.Duration.ofSeconds(7 * 60 + 30)),
        )
    }

    @Test
    fun `timer started announces the duration`() {
        assertEquals(
            "Timer started: fifteen minutes",
            Phrasebook.timerStarted(java.time.Duration.ofMinutes(15)),
        )
        assertEquals(
            "Timer started: one hour, thirty minutes",
            Phrasebook.timerStarted(java.time.Duration.ofMinutes(90)),
        )
        assertEquals(
            "Timer started: 60 hours",
            Phrasebook.timerStarted(java.time.Duration.ofHours(60)),
        )
    }

    // --- Stopwatch phrases ---

    @Test
    fun `lap announcements name the lap and its time`() {
        assertEquals(
            "Lap three: one minute, two seconds",
            Phrasebook.stopwatchLap(3, java.time.Duration.ofSeconds(62)),
        )
    }

    @Test
    fun `huge lap numbers fall back to digits`() {
        assertEquals(
            "Lap 100: thirty seconds",
            Phrasebook.stopwatchLap(100, java.time.Duration.ofSeconds(30)),
        )
    }

    @Test
    fun `elapsed announcement is the bare duration`() {
        assertEquals(
            "Five minutes",
            Phrasebook.stopwatchElapsed(java.time.Duration.ofMinutes(5)),
        )
    }

    // --- Number words ---

    @Test
    fun `number words cover the clock range`() {
        assertEquals("zero", Phrasebook.numberWords(0))
        assertEquals("thirteen", Phrasebook.numberWords(13))
        assertEquals("twenty", Phrasebook.numberWords(20))
        assertEquals("twenty-four", Phrasebook.numberWords(24))
        assertEquals("fifty-nine", Phrasebook.numberWords(59))
    }

    @Test
    fun `number words reject out-of-range values`() {
        assertThrows(IllegalArgumentException::class.java) { Phrasebook.numberWords(60) }
        assertThrows(IllegalArgumentException::class.java) { Phrasebook.numberWords(-1) }
    }
}
