package io.github.johnjeffords.talkingclock.speech

/**
 * What happened to an utterance offered to a voice pack.
 *
 * Three genuinely different outcomes, and the announcer must treat each
 * differently. Collapsing them into a boolean is what let a stopwatch line
 * that had LOST a priority collision get re-routed to system TTS, where it
 * then talked over the very timer cue that outranked it.
 */
enum class PlayResult {
    /** The pack is voicing it now. */
    Played,

    /** The pack can't voice it (missing tokens, or clips still loading), so
     *  the caller should fall back to TTS for the WHOLE utterance. */
    Unsupported,

    /** It lost a priority collision with what's already playing, so it is
     *  dropped — and must not be rerouted anywhere (ARCHITECTURE.md). */
    DroppedForPriority,
}

/**
 * The voice-pack side of the announcer's routing decision.
 *
 * An interface rather than the concrete player so the arbitration between
 * pack and TTS can be tested on the plain JVM — the real implementation
 * (VoicePackPlayer) needs Android's SoundPool, which is exactly why this
 * path had no test while it was wrong.
 */
interface PackVoice {

    /** Try to voice [utterance] at [priority]; see [PlayResult]. */
    fun play(utterance: Utterance, priority: Int): PlayResult

    /** Stop mid-utterance. */
    fun stop()

    /** Stop only when [priority] owns the utterance currently playing. */
    fun stop(priority: Int)
}
