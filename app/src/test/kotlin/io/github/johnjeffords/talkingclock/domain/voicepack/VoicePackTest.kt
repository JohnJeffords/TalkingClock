package io.github.johnjeffords.talkingclock.domain.voicepack

import io.github.johnjeffords.talkingclock.domain.timer.TimerCue
import io.github.johnjeffords.talkingclock.speech.Utterance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalTime

/**
 * Manifest parsing/validation, coverage, and the phrase composer — the
 * whole pure core of voice packs (docs/VOICE_PACKS.md). Robolectric runner
 * only because the manifest uses Android's built-in org.json.
 */
@RunWith(RobolectricTestRunner::class)
class VoicePackTest {

    /** A full pack: all number atoms + every feature token. */
    private fun fullManifest(): VoicePackManifest {
        val clips = buildMap {
            (0..20).forEach { put("num.$it", "num/$it.ogg") }
            listOf(30, 40, 50).forEach { put("num.$it", "num/$it.ogg") }
            listOf(
                "time.oclock", "time.am", "time.pm",
                "time.minute", "time.minutes", "time.second", "time.seconds",
                "timer.minutes-remaining", "timer.seconds-remaining",
                "timer.times-up", "timer.halfway",
                "stopwatch.lap",
            ).forEach { put(it, "clips/$it.ogg") }
        }
        return VoicePackManifest("Test pack", "en", "Tester", "CC0-1.0", 40, clips)
    }

    // --- Manifest parsing -----------------------------------------------------

    @Test
    fun `valid manifest parses`() {
        val json = """
            {"formatVersion":1,"name":"P","language":"en","license":"CC0-1.0",
             "gapMs":50,"clips":{"num.1":"num/1.ogg"}}
        """.trimIndent()
        val m = VoicePackManifest.parse(json).getOrThrow()
        assertEquals("P", m.name)
        assertEquals(50, m.gapMs)
        assertEquals("num/1.ogg", m.clips["num.1"])
    }

    @Test
    fun `manifest without a license is refused`() {
        val json = """{"formatVersion":1,"name":"P","clips":{"num.1":"a.ogg"}}"""
        val result = VoicePackManifest.parse(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("license"))
    }

    @Test
    fun `manifest with traversal paths is refused`() {
        val json = """
            {"formatVersion":1,"license":"CC0-1.0","clips":{"num.1":"../../evil.ogg"}}
        """.trimIndent()
        assertTrue(VoicePackManifest.parse(json).isFailure)
    }

    @Test
    fun `unsupported format version is refused`() {
        val json = """{"formatVersion":2,"license":"CC0-1.0","clips":{"a":"a.ogg"}}"""
        assertTrue(VoicePackManifest.parse(json).isFailure)
    }

    @Test
    fun `path safety rules`() {
        assertTrue(VoicePackManifest.isSafeRelativePath("num/1.ogg"))
        assertFalse(VoicePackManifest.isSafeRelativePath("/abs.ogg"))
        assertFalse(VoicePackManifest.isSafeRelativePath("..\\evil.ogg"))
        assertFalse(VoicePackManifest.isSafeRelativePath("a/../../b.ogg"))
        assertFalse(VoicePackManifest.isSafeRelativePath("C:evil.ogg"))
        assertFalse(VoicePackManifest.isSafeRelativePath(""))
    }

    // --- Coverage ---------------------------------------------------------------

    @Test
    fun `full pack covers everything`() {
        val coverage = coverageOf(fullManifest())
        assertTrue(coverage.clock)
        assertTrue(coverage.timer)
        assertTrue(coverage.stopwatch)
        assertEquals(emptyList<String>(), coverage.missing)
    }

    @Test
    fun `missing lap token breaks only stopwatch coverage`() {
        val manifest = fullManifest().let {
            it.copy(clips = it.clips - "stopwatch.lap")
        }
        val coverage = coverageOf(manifest)
        assertTrue(coverage.clock)
        assertTrue(coverage.timer)
        assertFalse(coverage.stopwatch)
        assertEquals(listOf("stopwatch.lap"), coverage.missing)
    }

    // --- Composition -------------------------------------------------------------

    @Test
    fun `time composes hour minute meridiem`() {
        val tokens = PhraseComposer.compose(
            fullManifest(),
            Utterance.TimeAnnouncement(LocalTime.of(14, 23), style = anyStyle()),
        )
        // 2:23 PM -> num.2 num.23... wait: 23 has no exact clip beyond 20?
        // fullManifest has num.0..20 + tens, so 23 composes as 20 + 3.
        assertEquals(listOf("num.2", "num.20", "num.3", "time.pm"), tokens)
    }

    @Test
    fun `exact number clip wins over composition`() {
        val manifest = fullManifest().let {
            it.copy(clips = it.clips + ("num.23" to "num/23.ogg"))
        }
        val tokens = PhraseComposer.compose(
            manifest,
            Utterance.TimeAnnouncement(LocalTime.of(14, 23), style = anyStyle()),
        )
        assertEquals(listOf("num.2", "num.23", "time.pm"), tokens)
    }

    @Test
    fun `on the hour uses oclock`() {
        val tokens = PhraseComposer.compose(
            fullManifest(),
            Utterance.TimeAnnouncement(LocalTime.of(10, 0), style = anyStyle()),
        )
        assertEquals(listOf("num.10", "time.oclock", "time.am"), tokens)
    }

    @Test
    fun `seconds requests fall back to TTS`() {
        assertNull(
            PhraseComposer.compose(
                fullManifest(),
                Utterance.TimeAnnouncement(
                    LocalTime.of(10, 0, 30),
                    style = anyStyle(),
                    includeSeconds = true,
                ),
            ),
        )
    }

    @Test
    fun `timer cues compose checkpoints and countdown`() {
        val tokens = PhraseComposer.compose(
            fullManifest(),
            Utterance.TimerCues(
                listOf(TimerCue.Checkpoint(Duration.ofMinutes(5))),
            ),
        )
        assertEquals(listOf("num.5", "timer.minutes-remaining"), tokens)

        val countdown = PhraseComposer.compose(
            fullManifest(),
            Utterance.TimerCues(listOf(TimerCue.Countdown(5))),
        )
        assertEquals(listOf("num.5"), countdown)

        val timesUp = PhraseComposer.compose(
            fullManifest(),
            Utterance.TimerCues(listOf(TimerCue.TimesUp)),
        )
        assertEquals(listOf("timer.times-up"), timesUp)
    }

    @Test
    fun `halfway needs the flavor clip or falls back entirely`() {
        val without = fullManifest().let { it.copy(clips = it.clips - "timer.halfway") }
        assertNull(
            PhraseComposer.compose(
                without,
                Utterance.TimerCues(listOf(TimerCue.Halfway(Duration.ofMinutes(5)))),
            ),
        )
        assertEquals(
            listOf("num.5", "timer.minutes-remaining", "timer.halfway"),
            PhraseComposer.compose(
                fullManifest(),
                Utterance.TimerCues(listOf(TimerCue.Halfway(Duration.ofMinutes(5)))),
            ),
        )
    }

    @Test
    fun `lap composes name number and duration`() {
        val tokens = PhraseComposer.compose(
            fullManifest(),
            Utterance.StopwatchLap(3, Duration.ofSeconds(62)),
        )
        assertEquals(
            listOf("stopwatch.lap", "num.3", "num.1", "time.minute", "num.2", "time.seconds"),
            tokens,
        )
    }

    @Test
    fun `missing atoms mean whole-utterance fallback`() {
        val manifest = fullManifest().let { it.copy(clips = it.clips - "num.20") }
        // 23 needs 20+3; without num.20 the whole time announcement falls back.
        assertNull(
            PhraseComposer.compose(
                manifest,
                Utterance.TimeAnnouncement(LocalTime.of(14, 23), style = anyStyle()),
            ),
        )
    }

    @Test
    fun `raw utterances never compose`() {
        assertNull(PhraseComposer.compose(fullManifest(), Utterance.Raw("hello")))
    }

    private fun anyStyle() =
        io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle.Conversational
}
