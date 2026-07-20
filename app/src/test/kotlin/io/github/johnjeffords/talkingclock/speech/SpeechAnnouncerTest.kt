package io.github.johnjeffords.talkingclock.speech

import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import io.github.johnjeffords.talkingclock.domain.announce.SpeakInterval
import io.github.johnjeffords.talkingclock.voicepack.VoicePackPlayer.PlayResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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

    @Test
    fun `an announcement reports its priority for optional haptic feedback`() {
        var reportedPriority: Int? = null
        val reportingAnnouncer = SpeechAnnouncer(
            speaker = speaker,
            onAnnounce = { reportedPriority = it },
            activePack = { null },
        )

        reportingAnnouncer.announce(utterance, Speaker.PRIORITY_TIMER)

        assertEquals(Speaker.PRIORITY_TIMER, reportedPriority)
    }

    @Test
    fun `a priority-dropped pack utterance does not trigger haptic feedback`() {
        var reportedPriority: Int? = null
        val reportingAnnouncer = SpeechAnnouncer(
            speaker = speaker,
            onAnnounce = { reportedPriority = it },
            activePack = { null },
        )

        reportingAnnouncer.deliver(utterance, Speaker.PRIORITY_CLOCK, PlayResult.Dropped)

        assertEquals(null, reportedPriority)
    }

    @Test
    fun `disarming clock through shared announcer does not stop timer speech`() = runTest {
        val ownershipSpeaker = OwnershipSpeaker()
        val sharedAnnouncer = SpeechAnnouncer(ownershipSpeaker) { null }
        val clock = SpeakingClockController(
            clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC),
            announcer = sharedAnnouncer,
            scope = backgroundScope,
            ensureServiceRunning = {},
        )
        clock.arm(SpeakInterval(60))
        sharedAnnouncer.announce(Utterance.Raw("One minute remaining"), Speaker.PRIORITY_TIMER)

        clock.disarm()

        assertEquals(Speaker.PRIORITY_TIMER, ownershipSpeaker.activePriority)
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
