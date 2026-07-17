package io.github.johnjeffords.talkingclock.domain.announce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

/**
 * Tests the interval model and — most importantly — the wall-clock boundary
 * alignment, which is the product rule that makes "every 5 min" speak at
 * :00/:05/:10 rather than at odd offsets from the arming tap.
 */
class SpeakIntervalTest {

    private fun at(h: Int, m: Int, s: Int = 0): LocalDateTime =
        LocalDateTime.of(2000, 1, 1, h, m, s)

    // --- Labels ---

    @Test
    fun `labels render seconds minutes and hours`() {
        assertEquals("15 s", SpeakInterval(15).label)
        assertEquals("5 min", SpeakInterval(300).label)
        assertEquals("1 h", SpeakInterval(3600).label)
        assertEquals("90 s", SpeakInterval(90).label)   // odd custom stays in seconds
    }

    @Test
    fun `spoken labels read naturally`() {
        assertEquals("every 5 minutes", SpeakInterval(300).spokenLabel)
        assertEquals("every minute", SpeakInterval(60).spokenLabel)
        assertEquals("every hour", SpeakInterval(3600).spokenLabel)
        assertEquals("every 15 seconds", SpeakInterval(15).spokenLabel)
    }

    @Test
    fun `intervals outside the guardrails are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SpeakInterval(14) }
        assertThrows(IllegalArgumentException::class.java) { SpeakInterval(25 * 3600) }
    }

    @Test
    fun `preset list matches the design menu`() {
        assertEquals(
            listOf(15, 20, 60, 300, 600, 1200, 1800, 3600),
            SpeakInterval.PRESETS.map { it.seconds },
        )
    }

    // --- Boundary alignment ---

    @Test
    fun `five minute interval fires on the next five minute mark`() {
        // 10:07:30 -> 10:10:00, not 10:12:30.
        assertEquals(
            at(10, 10),
            nextAnnouncementTime(at(10, 7, 30), SpeakInterval(300)),
        )
    }

    @Test
    fun `already on a boundary fires on the NEXT one`() {
        // Exactly 10:10:00 -> 10:15:00 (strictly after now; the 10:10
        // announcement is the one that just fired).
        assertEquals(
            at(10, 15),
            nextAnnouncementTime(at(10, 10, 0), SpeakInterval(300)),
        )
    }

    @Test
    fun `fifteen second interval fires on quarter-minute marks`() {
        assertEquals(
            at(10, 0, 45),
            nextAnnouncementTime(at(10, 0, 31), SpeakInterval(15)),
        )
        assertEquals(
            at(10, 1, 0),
            nextAnnouncementTime(at(10, 0, 46), SpeakInterval(15)),
        )
    }

    @Test
    fun `hourly interval fires on the hour`() {
        assertEquals(
            at(11, 0),
            nextAnnouncementTime(at(10, 0, 1), SpeakInterval(3600)),
        )
    }

    @Test
    fun `boundary past midnight rolls to tomorrow`() {
        // 23:58 with a 5-min interval -> 00:00 next day.
        val next = nextAnnouncementTime(at(23, 58), SpeakInterval(300))
        assertEquals(LocalDateTime.of(2000, 1, 2, 0, 0), next)
    }

    @Test
    fun `custom interval aligns to its own multiples since midnight`() {
        // 7-minute custom: multiples are :00, :07, :14 ... so 10:05 -> 10:12?
        // No — multiples since MIDNIGHT: 600 min elapsed at 10:00; next
        // multiple of 7 after 605 min (10:05) is 609 min = 10:09.
        assertEquals(
            at(10, 9),
            nextAnnouncementTime(at(10, 5), SpeakInterval(7 * 60)),
        )
    }
}
