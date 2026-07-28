package io.github.johnjeffords.talkingclock.speech

/**
 * The production [Announcer]: routes each utterance to the active voice
 * pack when one is selected AND it can fully voice the utterance; otherwise
 * the whole utterance goes to system TTS via [speaker]. The pack provider
 * is a lambda so the settings collector can swap packs live without anyone
 * holding a stale reference.
 *
 * "Can't voice it" and "lost a priority collision" are different answers and
 * are routed differently — see [VoicePackPlayer.PlayResult]. Treating the
 * second as the first is what let a dropped stopwatch line reach TTS and
 * talk over the timer cue that had just outranked it.
 */
class SpeechAnnouncer(
    private val speaker: Speaker,
    private val activePack: () -> PackVoice?,
) : Announcer {

    override fun announce(utterance: Utterance, priority: Int) {
        when (activePack()?.play(utterance, priority)) {
            PlayResult.Played ->
                // The pack is speaking; make sure TTS isn't ALSO talking over
                // it from an earlier lower-priority utterance.
                speaker.stop()

            // Dropped means DROPPED. Rerouting it to TTS would bypass the
            // very arbitration it just lost (ARCHITECTURE.md).
            PlayResult.DroppedForPriority -> Unit

            // No pack, or the pack can't voice this one: TTS takes the whole
            // utterance and arbitrates priority on its own side.
            PlayResult.Unsupported, null ->
                speaker.speak(utterance.toText(), priority)
        }
    }

    override fun stop() {
        activePack()?.stop()
        speaker.stop()
    }
}
