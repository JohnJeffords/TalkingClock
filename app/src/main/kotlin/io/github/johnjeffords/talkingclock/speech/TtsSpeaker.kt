package io.github.johnjeffords.talkingclock.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real [Speaker], backed by Android's built-in [TextToSpeech] service.
 *
 * Three platform quirks shape this class (the exact spots a newcomer gets
 * lost — see docs/ARCHITECTURE.md → Speech pipeline):
 *
 * 1. **TTS init is asynchronous and can fail.** Constructing [TextToSpeech]
 *    returns immediately; a callback later reports success or failure — and
 *    on de-Googled phones (GrapheneOS/CalyxOS) there is often NO engine at
 *    all. All of that is folded into the observable [state].
 *
 * 2. **Polite audio: focus ducking.** Before speaking we ask the system for
 *    *transient may-duck* audio focus — music keeps playing but drops in
 *    volume under the announcement, then returns to full volume when we
 *    release focus. The release happens in the utterance-progress callback
 *    (when speech actually ends), not when we *request* speech.
 *
 * 3. **Replace, don't queue.** [TextToSpeech.QUEUE_FLUSH] drops anything
 *    still being spoken. A clock announcing stale times is worse than one
 *    that skips (see [Speaker.speak]).
 *
 * The audio-focus interaction with the OS is isolated behind tiny function
 * values ([requestFocus]/[abandonFocus]) so unit tests can count focus
 * acquire/release without a real AudioManager.
 */
class TtsSpeaker(
    context: Context,
    private val requestFocus: () -> Unit,
    private val abandonFocus: () -> Unit,
) : Speaker {

    private val stateFlow = MutableStateFlow(SpeakerState.Initializing)
    override val state: StateFlow<SpeakerState> = stateFlow.asStateFlow()

    // Constructing TextToSpeech kicks off the async init; onInitResult runs
    // when the engine reports back.
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        onInitResult(status)
    }

    init {
        // Fires as each utterance actually starts/finishes/errors — this is
        // where audio focus is released, so other audio un-ducks exactly when
        // the speech ends rather than when it was requested.
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = abandonFocus()

            @Deprecated("Platform still requires overriding this variant")
            override fun onError(utteranceId: String?) = abandonFocus()
            override fun onError(utteranceId: String?, errorCode: Int) = abandonFocus()
        })
    }

    /**
     * Maps the engine's init callback onto our [SpeakerState]. Package-private
     * (not `private`) so the unit test can drive the state machine directly
     * without faking the whole engine.
     */
    internal fun onInitResult(status: Int) {
        stateFlow.value = if (status == TextToSpeech.SUCCESS) {
            SpeakerState.Ready
        } else {
            // ERROR covers both "engine broke" and "no engine responded".
            // If the device has no engines at all, report the more useful
            // NoEngine so the UI can show install guidance.
            if (tts.engines.isEmpty()) SpeakerState.NoEngine else SpeakerState.Error
        }
    }

    override fun speak(text: String) {
        // Drop (never queue, never crash) unless the engine is ready.
        if (stateFlow.value != SpeakerState.Ready) return

        requestFocus()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        tts.stop()
        abandonFocus()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
        abandonFocus()
    }

    companion object {
        // One logical utterance stream; the id just links progress callbacks
        // to our requests.
        private const val UTTERANCE_ID = "talking-clock-utterance"

        /**
         * Builds a [TtsSpeaker] wired to real audio focus: announcements duck
         * other audio (music dips, doesn't stop) and release focus when done.
         */
        fun create(context: Context): TtsSpeaker {
            val audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Speech audio, transient, allowed to duck others.
            val focusRequest = AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .build()

            return TtsSpeaker(
                context = context,
                requestFocus = { audioManager.requestAudioFocus(focusRequest) },
                abandonFocus = { audioManager.abandonAudioFocusRequest(focusRequest) },
            )
        }
    }
}
