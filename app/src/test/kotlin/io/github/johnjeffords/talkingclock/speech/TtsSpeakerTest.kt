package io.github.johnjeffords.talkingclock.speech

import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * Tests the [TtsSpeaker] lifecycle state machine, its audio-focus behavior,
 * and its recovery from the user switching the system speech engine — using
 * Robolectric so [android.speech.tts.TextToSpeech] exists on the JVM. The
 * engine's async init callback and the engine-changed notification are driven
 * directly via [TtsSpeaker.onInitResult] / [TtsSpeaker.onDefaultEngineChanged];
 * that's exactly why those functions are `internal` rather than buried in a
 * callback object.
 *
 * Audio focus is asserted by counting calls to the injected focus lambdas —
 * we care that focus is requested when speech starts, not which AudioManager
 * API was invoked. The engine switch is simulated by changing what
 * [systemDefaultEngine] reports, which is what the real speaker reads out of
 * the system settings.
 */
@RunWith(RobolectricTestRunner::class)
class TtsSpeakerTest {

    private var focusRequests = 0
    private var focusAbandons = 0

    /** Stands in for Android's "default speech engine" setting. */
    private var systemDefaultEngine: String? = "com.k2fsa.sherpa.onnx.tts.engine"

    private fun buildSpeaker(): TtsSpeaker = TtsSpeaker(
        context = ApplicationProvider.getApplicationContext(),
        requestFocus = { focusRequests++ },
        abandonFocus = { focusAbandons++ },
        defaultEngineName = { systemDefaultEngine },
    )

    /**
     * The engine instance the speaker most recently bound to. Robolectric
     * hands us the real [TextToSpeech] object the speaker constructed, so the
     * tests can assert about the actual binding rather than about our own
     * bookkeeping.
     */
    private fun boundEngine(): TextToSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance()

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
    fun `stop abandons audio focus`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        speaker.speak("It's ten twenty-four")
        speaker.stop()
        assertEquals(1, focusAbandons)
    }

    /**
     * The regression. A [TextToSpeech] instance is welded for life to the
     * engine that was default when it was built, and Android never says the
     * default moved. Before this was handled, switching the system engine
     * (the owner switched away from SherpaTTS) left the speaker latched in
     * [SpeakerState.Ready] while every utterance went to a dead binding: the
     * UI insisted all was well and the clock silently stopped talking until
     * the app was force-restarted.
     */
    @Test
    fun `switching the system engine rebinds instead of staying latched on Ready`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        assertEquals(SpeakerState.Ready, speaker.state.value)
        val engineBeforeSwitch = boundEngine()

        // The user picks a different engine in Android's Text-to-speech
        // settings; the setting changes and our observer fires.
        systemDefaultEngine = "com.reecedunn.espeak"
        speaker.onDefaultEngineChanged()

        val engineAfterSwitch = boundEngine()
        assertNotSame(
            "A new engine binding must be built — the old instance can only " +
                "ever reach the engine the user just switched away from.",
            engineBeforeSwitch,
            engineAfterSwitch,
        )
        assertTrue(
            "The old binding holds a service connection to the other app and " +
                "must be shut down, not leaked.",
            shadowOf(engineBeforeSwitch).isShutdown,
        )
        assertEquals(
            "State must drop out of Ready while the new engine initializes — " +
                "claiming Ready here is what made the failure invisible.",
            SpeakerState.Initializing,
            speaker.state.value,
        )

        // Once the new engine reports in, speech resumes with no app restart…
        speaker.onInitResult(TextToSpeech.SUCCESS)
        assertEquals(SpeakerState.Ready, speaker.state.value)
        speaker.speak("It's ten twenty-four")

        // …and it comes out of the NEW engine, not the abandoned one.
        assertEquals("It's ten twenty-four", shadowOf(engineAfterSwitch).lastSpokenText)
        assertNull(shadowOf(engineBeforeSwitch).lastSpokenText)
    }

    /**
     * The same setting is rewritten whenever the user opens the engine picker
     * and taps what was already selected. Rebuilding then would throw away a
     * working binding — and cut off whatever was mid-announcement — for
     * nothing.
     */
    @Test
    fun `re-selecting the engine already in use keeps the current binding`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)
        val engine = boundEngine()

        speaker.onDefaultEngineChanged() // same name as before

        assertSame(engine, boundEngine())
        assertEquals(SpeakerState.Ready, speaker.state.value)
    }

    /**
     * Speech requested during the rebind window is dropped, never queued: by
     * the time the new engine is ready the time it named would be stale, and
     * a clock that announces a stale time is worse than one that skips a line
     * (docs/ARCHITECTURE.md → Speech pipeline).
     */
    @Test
    fun `speech during the rebind window is dropped and takes no audio focus`() {
        val speaker = buildSpeaker()
        speaker.onInitResult(TextToSpeech.SUCCESS)

        systemDefaultEngine = "com.reecedunn.espeak"
        speaker.onDefaultEngineChanged()
        speaker.speak("It's ten twenty-four")

        assertEquals(0, focusRequests)
        assertNull(shadowOf(boundEngine()).lastSpokenText)
    }
}
