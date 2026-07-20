package io.github.johnjeffords.talkingclock.speech

import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the [TtsSpeaker] lifecycle state machine and its audio-focus
 * behavior, using Robolectric so [android.speech.tts.TextToSpeech] exists on
 * the JVM. The engine's async init callback is driven directly via
 * [TtsSpeaker.onInitResult] — that's exactly why that function is `internal`
 * rather than buried in the constructor lambda.
 *
 * Audio focus is asserted by counting calls to the injected focus lambdas —
 * we care that focus is requested when speech starts, not which AudioManager
 * API was invoked.
 */
@RunWith(RobolectricTestRunner::class)
class TtsSpeakerTest {

    private var focusRequests = 0
    private var focusAbandons = 0

    private fun buildSpeaker(): TtsSpeaker = TtsSpeaker(
        context = ApplicationProvider.getApplicationContext(),
        requestFocus = { focusRequests++ },
        abandonFocus = { focusAbandons++ },
    )

    @Test
    fun `successful init reaches Ready`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        assertEquals(SpeakerState.Ready, speaker.state.value)
    }

    @Test
    fun `failed init with no engines reports NoEngine`() {
        // Robolectric's environment has no TTS engines installed, which is
        // exactly the GrapheneOS/CalyxOS out-of-box condition we care about.
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.ERROR)
        assertEquals(SpeakerState.NoEngine, speaker.state.value)
    }

    @Test
    fun `speak before ready is dropped and takes no audio focus`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.ERROR) // engine failed
        speaker.speak("It's ten twenty-four")
        assertEquals(0, focusRequests)
    }

    @Test
    fun `speak when ready requests audio focus once`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        speaker.speak("It's ten twenty-four")
        assertEquals(1, focusRequests)
    }

    @Test
    fun `lower priority speech cannot replace an accepted utterance`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)

        speaker.speak("Timer complete", Speaker.PRIORITY_TIMER)
        speaker.speak("It's ten twenty-four", Speaker.PRIORITY_CLOCK)

        assertEquals(1, focusRequests)
    }

    @Test
    fun `stop abandons audio focus`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        speaker.speak("It's ten twenty-four")
        speaker.stop()
        assertEquals(1, focusAbandons)
    }

    @Test
    fun `stopping another feature does not stop current speech`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        speaker.speak("Timer complete", Speaker.PRIORITY_TIMER)

        speaker.stop(Speaker.PRIORITY_CLOCK)

        assertEquals(0, focusAbandons)
    }
}
