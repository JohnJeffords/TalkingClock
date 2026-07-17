package io.github.johnjeffords.talkingclock.speech

import io.github.johnjeffords.talkingclock.voicepack.VoicePackPlayer

/**
 * The production [Announcer]: routes each utterance to the active voice
 * pack when one is selected AND it can fully voice the utterance; otherwise
 * the whole utterance goes to system TTS via [speaker]. The pack provider
 * is a lambda so the settings collector can swap packs live without anyone
 * holding a stale reference.
 */
class SpeechAnnouncer(
    private val speaker: Speaker,
    private val activePack: () -> VoicePackPlayer?,
) : Announcer {

    override fun announce(utterance: Utterance, priority: Int) {
        val pack = activePack()
        if (pack != null && pack.tryPlay(utterance, priority)) {
            // The pack is speaking; make sure TTS isn't ALSO talking over it
            // from an earlier lower-priority utterance.
            speaker.stop()
            return
        }
        speaker.speak(utterance.toText(), priority)
    }

    override fun stop() {
        activePack()?.stop()
        speaker.stop()
    }
}
