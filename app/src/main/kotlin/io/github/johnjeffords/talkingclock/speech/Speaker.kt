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
     * Speak [text] aloud. Never queues: a talking clock must never say a
     * STALE time behind a new one, so an utterance either replaces what's
     * playing or is dropped — decided by [priority]:
     *
     *  - equal or higher priority than what's playing → replace it;
     *  - lower priority → dropped entirely.
     *
     * That one rule implements the design's collision law ("if both want to
     * speak at once, timer wins, stopwatch drops that line"): the timer
     * speaks at [PRIORITY_TIMER], the clock at [PRIORITY_CLOCK], the
     * stopwatch at [PRIORITY_STOPWATCH].
     *
     * Safe to call in any state; if the engine isn't [SpeakerState.Ready]
     * the request is simply dropped (never crashes, never queues).
     */
    fun speak(text: String, priority: Int = PRIORITY_CLOCK)

    /** Stop mid-utterance (e.g. the user hit Stop). No-op when silent. */
    fun stop()

    /** Release the engine. Call when the owning scope is done with speech. */
    fun shutdown()

    companion object {
        /** Stopwatch lines lose every collision (design rule). */
        const val PRIORITY_STOPWATCH = 0

        /** Regular speaking-clock announcements and tap-to-speak. */
        const val PRIORITY_CLOCK = 1

        /** Timer cues — checkpoints, the countdown, "Time's up" — win. */
        const val PRIORITY_TIMER = 2
    }
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
