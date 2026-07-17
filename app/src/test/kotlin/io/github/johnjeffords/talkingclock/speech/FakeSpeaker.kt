package io.github.johnjeffords.talkingclock.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [Speaker] for tests: instead of making sound, it records every sentence
 * it was asked to say. Tests then assert on [spoken] — "the timer announced
 * 'Five minutes remaining'" — which is deterministic, unlike real audio.
 * This fake is the reason screens and engines talk to the [Speaker]
 * interface rather than to Android TTS directly.
 */
class FakeSpeaker(
    initialState: SpeakerState = SpeakerState.Ready,
) : Speaker {

    private val stateFlow = MutableStateFlow(initialState)
    override val state: StateFlow<SpeakerState> = stateFlow

    /** Every text passed to [speak], in order. */
    val spoken = mutableListOf<String>()

    /** How many times [stop] was called. */
    var stopCount = 0
        private set

    override fun speak(text: String) {
        // Mirror the real contract: drop unless Ready.
        if (stateFlow.value == SpeakerState.Ready) spoken += text
    }

    override fun stop() {
        stopCount++
    }

    override fun shutdown() = Unit

    /** Lets a test move the fake through lifecycle states. */
    fun setState(state: SpeakerState) {
        stateFlow.value = state
    }
}
