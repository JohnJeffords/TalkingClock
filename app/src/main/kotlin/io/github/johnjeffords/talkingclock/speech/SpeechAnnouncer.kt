package io.github.johnjeffords.talkingclock.speech

/**
 * Routes each utterance to the selected voice pack when it can fully voice
 * it, otherwise to system TTS. The pack provider is a lambda so settings can
 * swap packs without leaving a stale player here.
 */
class SpeechAnnouncer(
    private val speaker: Speaker,
    private val onAnnounce: (Int) -> Unit = {},
    private val activePack: () -> PackVoice?,
) : Announcer {

    override fun announce(utterance: Utterance, priority: Int) {
        deliver(
            utterance,
            priority,
            activePack()?.play(utterance, priority) ?: PlayResult.Unsupported,
        )
    }

    internal fun deliver(utterance: Utterance, priority: Int, result: PlayResult) {
        when (result) {
            PlayResult.Played -> {
                onAnnounce(priority)
                // The pack is speaking; stop stale TTS from overlapping it.
                speaker.stop()
            }
            PlayResult.Unsupported -> {
                onAnnounce(priority)
                speaker.speak(utterance.toText(), priority)
            }
            // A priority loss stays dropped; TTS must not bypass it.
            PlayResult.DroppedForPriority -> Unit
        }
    }

    override fun stop() {
        activePack()?.stop()
        speaker.stop()
    }

    override fun stop(priority: Int) {
        activePack()?.stop(priority)
        speaker.stop(priority)
    }
}
