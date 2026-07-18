package io.github.johnjeffords.talkingclock.speech

import io.github.johnjeffords.talkingclock.voicepack.VoicePackPlayer.PlayResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechAnnouncerTest {

    private val speaker = FakeSpeaker()
    private val announcer = SpeechAnnouncer(speaker) { null }
    private val utterance = Utterance.Raw("lower priority")

    @Test
    fun `a priority-dropped pack utterance does not fall back to TTS`() {
        announcer.deliver(utterance, Speaker.PRIORITY_CLOCK, PlayResult.Dropped)

        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(0, speaker.stopCount)
    }

    @Test
    fun `an unsupported pack utterance falls back to TTS`() {
        announcer.deliver(utterance, Speaker.PRIORITY_CLOCK, PlayResult.Unsupported)

        assertEquals(listOf("lower priority"), speaker.spoken)
    }

    @Test
    fun `a playing pack stops stale TTS without speaking a fallback`() {
        announcer.deliver(utterance, Speaker.PRIORITY_CLOCK, PlayResult.Played)

        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(1, speaker.stopCount)
    }
}
