package io.github.johnjeffords.talkingclock.speech

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real [Speaker], backed by Android's built-in [TextToSpeech] service.
 *
 * Four platform quirks shape this class (the exact spots a newcomer gets
 * lost — see docs/ARCHITECTURE.md → Speech pipeline):
 *
 * 1. **TTS init is asynchronous and can fail.** Constructing [TextToSpeech]
 *    returns immediately; a callback later reports success or failure — and
 *    on de-Googled phones (GrapheneOS/CalyxOS) there is often NO engine at
 *    all. All of that is folded into the observable [state].
 *
 * 2. **A [TextToSpeech] instance is welded to ONE engine app, for life.**
 *    It binds to whichever engine was the system default when it was
 *    constructed, and Android never tells it when that changes. Switch the
 *    default engine in system settings (SherpaTTS → RHVoice, say) and this
 *    object keeps talking to the old engine's service — which the OS may
 *    have already torn down. Nothing throws; speech just stops. So we watch
 *    the default-engine setting ourselves and rebuild the binding when it
 *    moves (see [onDefaultEngineChanged]), and treat a failed
 *    [TextToSpeech.speak] as "the engine died under us" (see [speak]).
 *
 * 3. **Polite audio: focus ducking.** Before speaking we ask the system for
 *    *transient may-duck* audio focus — music keeps playing but drops in
 *    volume under the announcement, then returns to full volume when we
 *    release focus. The release happens in the utterance-progress callback
 *    (when speech actually ends), not when we *request* speech.
 *
 * 4. **Replace, don't queue.** [TextToSpeech.QUEUE_FLUSH] drops anything
 *    still being spoken. A clock announcing stale times is worse than one
 *    that skips (see [Speaker.speak]).
 *
 * The OS interactions are isolated behind tiny function values
 * ([requestFocus]/[abandonFocus]/[defaultEngineName]) so unit tests can count
 * focus acquire/release and simulate an engine switch without a real
 * AudioManager or a real second TTS engine installed.
 *
 * @param defaultEngineName reads the package name of the system's current
 *   default speech engine, or null when there isn't one. Tests substitute a
 *   plain lambda to simulate the user changing engines.
 */
class TtsSpeaker(
    context: Context,
    private val requestFocus: () -> Unit,
    private val abandonFocus: () -> Unit,
    private val defaultEngineName: () -> String?,
) : Speaker {

    // Held for the life of the process: rebuilding the engine needs a Context
    // long after the constructor has returned. Application context only —
    // holding an Activity here would leak the whole screen.
    private val appContext = context.applicationContext

    private val stateFlow = MutableStateFlow(SpeakerState.Initializing)
    override val state: StateFlow<SpeakerState> = stateFlow.asStateFlow()

    // The live engine binding. Nullable rather than `lateinit` because we now
    // tear it down and build a new one when the default engine changes — and
    // because of the window described in [openEngine].
    private var tts: TextToSpeech? = null

    /**
     * Which engine package [tts] was opened against. Comparing this to the
     * current default is how we notice the user switched engines; keeping our
     * own copy (rather than asking the engine) is what makes the check work
     * even in the moment when [tts] is null.
     */
    private var openedAgainstEngine: String? = null

    // The user's Voice settings. Rate and pitch live on the TextToSpeech
    // INSTANCE, so a rebuilt engine starts at that engine's own defaults —
    // we have to remember the settings here and re-teach them, or switching
    // engines would silently reset the user's speech speed.
    private var speechRate = 1.0f
    private var speechPitch = 1.0f

    /**
     * Fires when any app writes the "default speech engine" system setting —
     * i.e. exactly when the user picks a different engine in Android's
     * Text-to-speech settings. This is the only notification Android gives
     * us, and it arrives even while our app is in the background with only
     * the announcer service running, which is when a talking clock most needs
     * to notice (docs/DESIGN.md: never silently stop announcing).
     */
    private val defaultEngineObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onDefaultEngineChanged()
        }

    init {
        openEngine()
        appContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.TTS_DEFAULT_SYNTH),
            false,
            defaultEngineObserver,
        )
    }

    /**
     * Bind a fresh [TextToSpeech] to whatever engine is default right now and
     * start its async init. [state] goes back to [SpeakerState.Initializing]
     * for the duration, so the UI tells the truth while we rebind.
     */
    private fun openEngine() {
        // Clear `tts` FIRST. When the device has NO engine at all, the
        // TextToSpeech constructor reports failure SYNCHRONOUSLY — the
        // callback below runs before the constructor has even returned, so
        // onInitResult must not find a stale (already shut down) instance
        // sitting in this field. Found by the emulator smoke test on a
        // stripped no-TTS image, and it is also why onInitResult treats
        // "no instance yet" as the NoEngine case.
        tts = null
        openedAgainstEngine = defaultEngineName()
        stateFlow.value = SpeakerState.Initializing

        val engine = TextToSpeech(appContext) { status -> onInitResult(status) }
        // Fires as each utterance actually starts/finishes/errors — this is
        // where audio focus is released (so other audio un-ducks exactly when
        // the speech ends, not when it was requested) and where the priority
        // gate resets so any next line may speak.
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onUtteranceEnded()

            @Deprecated("Platform still requires overriding this variant")
            override fun onError(utteranceId: String?) = onUtteranceEnded()
            override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceEnded()
        })
        engine.setSpeechRate(speechRate)
        engine.setPitch(speechPitch)
        tts = engine
    }

    /**
     * Drop the current engine binding and open a new one. Shutting the old
     * instance down matters: it holds a bound service connection to the other
     * app, and leaking one per engine switch would keep dead engines alive
     * for the life of our process.
     */
    private fun reopenEngine() {
        tts?.stop()
        tts?.shutdown()
        // Anything that was mid-utterance is gone with the old engine, so the
        // priority gate and audio focus have to be released by hand — the
        // progress listener will never call back now.
        onUtteranceEnded()
        openEngine()
    }

    /**
     * The default speech engine setting changed. Internal (not private) so
     * the unit test can drive an engine switch directly, the same way it
     * drives [onInitResult].
     */
    internal fun onDefaultEngineChanged() {
        // The setting can be rewritten with the value it already had (the
        // user re-picks the current engine, or another app touches it).
        // Rebuilding then would drop a perfectly good binding — and with it
        // whatever announcement was being spoken.
        if (defaultEngineName() == openedAgainstEngine) return
        reopenEngine()
    }

    /**
     * Maps the engine's init callback onto our [SpeakerState]. Package-private
     * (not `private`) so the unit test can drive the state machine directly
     * without faking the whole engine.
     *
     * May run BEFORE [tts] is assigned: with no engine installed, the
     * TextToSpeech constructor reports failure synchronously from inside
     * construction. In that window we can't (and don't need to) ask the
     * engine anything — a synchronous failure means there was no engine to
     * bind, so it IS the NoEngine case.
     */
    internal fun onInitResult(status: Int) {
        stateFlow.value = if (status == TextToSpeech.SUCCESS) {
            SpeakerState.Ready
        } else {
            // ERROR covers both "engine broke" and "no engine responded".
            // If the device has no engines at all, report the more useful
            // NoEngine so the UI can show install guidance.
            val engines = tts?.engines ?: emptyList()
            if (engines.isEmpty()) SpeakerState.NoEngine else SpeakerState.Error
        }
    }

    /** Priority of the utterance currently playing (see [Speaker.speak]). */
    private var speakingPriority = Int.MIN_VALUE

    override fun speak(text: String, priority: Int) {
        // Drop (never queue, never crash) unless the engine is ready.
        if (stateFlow.value != SpeakerState.Ready) return
        val engine = tts ?: return

        // The collision rule: a lower-priority line never interrupts a
        // higher-priority one — it's dropped, not delayed (a late
        // announcement is a wrong announcement).
        if (engine.isSpeaking && priority < speakingPriority) return

        speakingPriority = priority
        requestFocus()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            // The engine app went away underneath us — uninstalled, updated,
            // or replaced — and its service connection is dead. Android sends
            // no callback for that, so a rejected speak IS the notification.
            // Rebuild against the current default so the NEXT announcement
            // speaks. This one is deliberately NOT retried: init is async, and
            // a re-spoken time that lands hundreds of milliseconds late is the
            // stale announcement the whole design forbids.
            reopenEngine()
        }
    }

    override fun stop() {
        tts?.stop()
        speakingPriority = Int.MIN_VALUE
        abandonFocus()
    }

    override fun shutdown() {
        // Stop watching the setting BEFORE tearing the engine down, or a
        // late-arriving engine change would rebuild a binding for a speaker
        // nobody will ever use again.
        appContext.contentResolver.unregisterContentObserver(defaultEngineObserver)
        tts?.stop()
        tts?.shutdown()
        tts = null
        abandonFocus()
    }

    /** An utterance finished (or died): release focus, open the gate. */
    private fun onUtteranceEnded() {
        speakingPriority = Int.MIN_VALUE
        abandonFocus()
    }

    /** Speech speed multiplier (1.0 = engine default). Settings-driven. */
    fun setRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    /** Voice pitch multiplier (1.0 = engine default). Settings-driven. */
    fun setPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(speechPitch)
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
            // The speaker outlives every screen, so everything it captures
            // must be the application context — capturing an Activity here
            // would keep that whole screen in memory for the process's life.
            val appContext = context.applicationContext
            val audioManager =
                appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
                context = appContext,
                requestFocus = { audioManager.requestAudioFocus(focusRequest) },
                abandonFocus = { audioManager.abandonAudioFocusRequest(focusRequest) },
                // The one place Android exposes "which engine is default" as
                // a plain value we can also WATCH for changes (the observer
                // above listens on this same setting's Uri). Reading a public
                // system setting — no permission, no INTERNET.
                defaultEngineName = {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        Settings.Secure.TTS_DEFAULT_SYNTH,
                    )
                },
            )
        }
    }
}
