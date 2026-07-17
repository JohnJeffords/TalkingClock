package io.github.johnjeffords.talkingclock.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * The app's one door to speech. Screens and services ask a [Speaker] to say
 * things; they never touch the Android TTS engine directly. That indirection
 * is what lets us:
 *  - swap in a recorded voice pack later (M7) without touching any screen,
 *  - hand tests a [FakeSpeaker] that just records what would have been said
 *    (asserting on real audio is flaky; asserting on the utterance is not).
 */
interface Speaker {

    /** Where the engine is in its lifecycle. UIs react to this — e.g. show
     *  the "install a speech engine" card on [SpeakerState.NoEngine]. */
    val state: StateFlow<SpeakerState>

    /**
     * Speak [text] aloud, interrupting anything currently being spoken.
     * A talking clock must never queue stale announcements behind new ones —
     * saying the OLD time after the minute has rolled over is worse than
     * saying nothing — so replace-don't-queue is the only mode offered.
     *
     * Safe to call in any state; if the engine isn't [SpeakerState.Ready]
     * the request is simply dropped (never crashes, never queues).
     */
    fun speak(text: String)

    /** Stop mid-utterance (e.g. the user hit Stop). No-op when silent. */
    fun stop()

    /** Release the engine. Call when the owning scope is done with speech. */
    fun shutdown()
}

/** The speech engine's lifecycle, as UIs see it. */
enum class SpeakerState {
    /** Engine is starting up (TTS init is asynchronous). Controls that need
     *  speech can show a brief spinner. */
    Initializing,

    /** Engine is ready; [Speaker.speak] will produce audio. */
    Ready,

    /**
     * No TTS engine is installed — the normal out-of-box state on GrapheneOS
     * and CalyxOS (Google TTS is proprietary and absent there). The UI shows
     * guidance to install a FOSS engine (RHVoice / eSpeak NG). See
     * docs/DECISIONS.md D-011.
     */
    NoEngine,

    /** The engine exists but failed to initialize. Treated like NoEngine in
     *  the UI, with a retry. */
    Error,
}
