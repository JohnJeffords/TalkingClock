package io.github.johnjeffords.talkingclock.speech

import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SpeechAnnouncerTest {

    private class FakePack(private val answers: MutableList<PlayResult>) : PackVoice {
        val played = mutableListOf<String>()
        var stopCount = 0
            private set
        private var activePriority: Int? = null

        override fun play(utterance: Utterance, priority: Int): PlayResult {
            val result = answers.removeAt(0)
            if (result == PlayResult.Played) {
                played += utterance.toText()
                activePriority = priority
            }
            return result
        }

        override fun stop() {
            stopCount++
            activePriority = null
        }

        override fun stop(priority: Int) {
            if (activePriority == priority) stop()
        }
    }

    private val stopwatchCue = Utterance.StopwatchElapsed(Duration.ofSeconds(10))

    @Test
    fun `a pack-voiced utterance silences TTS and reports playback`() {
        val speaker = FakeSpeaker()
        val pack = FakePack(mutableListOf(PlayResult.Played))
        var reportedPriority: Int? = null
        val announcer = SpeechAnnouncer(
            speaker = speaker,
            onAnnounce = { reportedPriority = it },
            activePack = { pack },
        )

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(listOf("Ten seconds"), pack.played)
        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(1, speaker.stopCount)
        assertEquals(Speaker.PRIORITY_STOPWATCH, reportedPriority)
    }

    @Test
    fun `an unsupported pack utterance falls back to TTS and reports playback`() {
        val speaker = FakeSpeaker()
        val pack = FakePack(mutableListOf(PlayResult.Unsupported))
        var reportedPriority: Int? = null
        val announcer = SpeechAnnouncer(
            speaker = speaker,
            onAnnounce = { reportedPriority = it },
            activePack = { pack },
        )

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(emptyList<String>(), pack.played)
        assertEquals(listOf("Ten seconds"), speaker.spoken)
        assertEquals(Speaker.PRIORITY_STOPWATCH, reportedPriority)
    }

    @Test
    fun `an utterance dropped for priority stays silent and reports nothing`() {
        val speaker = FakeSpeaker()
        val pack = FakePack(mutableListOf(PlayResult.DroppedForPriority))
        var reportedPriority: Int? = null
        val announcer = SpeechAnnouncer(
            speaker = speaker,
            onAnnounce = { reportedPriority = it },
            activePack = { pack },
        )

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(emptyList<String>(), pack.played)
        assertEquals(emptyList<String>(), speaker.spoken)
        assertEquals(0, speaker.stopCount)
        assertEquals(null, reportedPriority)
    }

    @Test
    fun `with no pack selected everything goes to TTS`() {
        val speaker = FakeSpeaker()
        val announcer = SpeechAnnouncer(speaker) { null }

        announcer.announce(stopwatchCue, Speaker.PRIORITY_STOPWATCH)

        assertEquals(listOf("Ten seconds"), speaker.spoken)
    }

    @Test
    fun `disarming clock does not stop timer speech`() = runTest {
        val speaker = OwnershipSpeaker()
        val announcer = SpeechAnnouncer(speaker) { null }
        val clock = SpeakingClockController(
            clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC),
            announcer = announcer,
            scope = backgroundScope,
            ensureServiceRunning = {},
        )
        clock.arm(SpeakInterval(60))
        announcer.announce(Utterance.Raw("One minute remaining"), Speaker.PRIORITY_TIMER)

        clock.disarm()

        assertEquals(Speaker.PRIORITY_TIMER, speaker.activePriority)
    }

    private class OwnershipSpeaker : Speaker {
        override val state: StateFlow<SpeakerState> = MutableStateFlow(SpeakerState.Ready)
        var activePriority: Int? = null
            private set

        override fun speak(text: String, priority: Int) {
            if (priority >= (activePriority ?: Int.MIN_VALUE)) activePriority = priority
        }

        override fun stop() {
            activePriority = null
        }

        override fun stop(priority: Int) {
            if (activePriority == priority) activePriority = null
        }

        override fun shutdown() = Unit
    }
}
