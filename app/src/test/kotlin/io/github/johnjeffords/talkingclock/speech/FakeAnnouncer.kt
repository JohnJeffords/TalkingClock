package io.github.johnjeffords.talkingclock.speech

/**
 * An [Announcer] for tests: records what would have been said instead of
 * making sound. [spoken] holds each utterance's rendered TEXT (via
 * [Utterance.toText]) so transcript assertions read naturally — asserting
 * "It's ten oh one" is clearer than matching structured objects — while
 * [utterances] keeps the structure for tests that care about it.
 */
class FakeAnnouncer : Announcer {

    /** Rendered text of every announcement, in order. */
    val spoken = mutableListOf<String>()

    /** The structured utterances, same order as [spoken]. */
    val utterances = mutableListOf<Utterance>()

    /** The priority each announcement arrived with, same order. */
    val spokenPriorities = mutableListOf<Int>()

    /** How many times [stop] was called. */
    var stopCount = 0
        private set

    override fun announce(utterance: Utterance, priority: Int) {
        utterances += utterance
        spoken += utterance.toText()
        spokenPriorities += priority
    }

    override fun stop() {
        stopCount++
    }
}
