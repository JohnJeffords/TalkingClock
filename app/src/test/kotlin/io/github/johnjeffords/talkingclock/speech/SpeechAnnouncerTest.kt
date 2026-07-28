package io.github.johnjeffords.talkingclock.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * Arbitration between the voice pack and system TTS — the routing that
 * decides whether an utterance is voiced by clips, spoken by TTS, or
 * dropped outright. Previously untestable (the real pack needs SoundPool),
 * which is why the priority-drop bug lived here undetected.
 */
class SpeechAnnouncerTest {

    /** A pack whose answer each call is scripted by the test. */
    private class FakePack(private val answers: MutableList<PlayResult>) : PackVoice {
        val played = mutableListOf<String>()
        var stopCount = 0
            private set

        override fun play(utterance: Utterance, priority: Int): PlayResult {
            val result = answers.removeAt(0)
            if (result == PlayResult.Played) played += utterance.toText()
            return result
        }

        override fun stop() {
            stopCount++
        }
    }

    private val stopwatchCue = Utterance.StopwatchElapsed(Duration.ofSeconds(10))

    @Test
    fun `a pack-voiced utterance silences TTS and never reaches it`() {
        val speaker = FakeSpeaker()
        val pack = FakePack(mutableListOf(PlayResult.Played))
        val announcer = SpeechAnnouncer(speaker) { pack }

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(listOf("Ten seconds"), pack.played)
        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(1, speaker.stopCount) // TTS hushed so it can't overlap
    }

    @Test
    fun `an utterance the pack can't voice falls back to TTS`() {
        val speaker = FakeSpeaker()
        val pack = FakePack(mutableListOf(PlayResult.Unsupported))
        val announcer = SpeechAnnouncer(speaker) { pack }

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(emptyList<String>(), pack.played)
        assertEquals(listOf("Ten seconds"), speaker.spoken)
    }

    @Test
    fun `an utterance dropped for priority reaches neither the pack nor TTS`() {
        // The regression: a stopwatch line losing to a playing timer cue was
        // rerouted to TTS, where it spoke over the cue that had outranked it.
        val speaker = FakeSpeaker()
        val pack = FakePack(mutableListOf(PlayResult.DroppedForPriority))
        val announcer = SpeechAnnouncer(speaker) { pack }

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(emptyList<String>(), pack.played)
        assertEquals(emptyList<String>(), speaker.spoken) // stays silent
        assertEquals(0, speaker.stopCount) // and doesn't cut the winner off
    }

    @Test
    fun `with no pack selected everything goes to TTS`() {
        val speaker = FakeSpeaker()
        val announcer = SpeechAnnouncer(speaker) { null }

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(listOf("Ten seconds"), speaker.spoken)
    }
}
