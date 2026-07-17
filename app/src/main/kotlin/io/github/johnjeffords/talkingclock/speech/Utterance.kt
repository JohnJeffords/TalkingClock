package io.github.johnjeffords.talkingclock.speech

import io.github.johnjeffords.talkingclock.domain.speech.Phrasebook
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.domain.timer.TimerCue
import java.time.Duration
import java.time.LocalTime

/**
 * Everything the app ever says, as STRUCTURED data rather than a string.
 *
 * Why this exists: the TTS engine only needs the final sentence, but a
 * recorded voice pack (M7) needs to know WHAT is being said — "the time
 * 10:24" — to stitch the right clips ("num.10", "num.24"). So announcements
 * travel as [Utterance]s; the voice-pack player consumes the structure, and
 * [toText] renders the exact Phrasebook sentence for the TTS fallback. One
 * source of phrasing, two renderers.
 */
sealed interface Utterance {

    /** The clock announcing a time (tap-to-speak or an interval boundary). */
    data class TimeAnnouncement(
        val time: LocalTime,
        val style: SpeakingStyle,
        val includeSeconds: Boolean = false,
    ) : Utterance

    /** One tick's worth of timer cues (usually one; several after a stall). */
    data class TimerCues(val cues: List<TimerCue>) : Utterance

    /** "Timer started: fifteen minutes." */
    data class TimerStarted(val duration: Duration) : Utterance

    /** The stopwatch's ambient elapsed announcement ("Five minutes"). */
    data class StopwatchElapsed(val elapsed: Duration) : Utterance

    /** "Lap three: one minute, two seconds." */
    data class StopwatchLap(val lapNumber: Int, val lapTime: Duration) : Utterance

    /** Raw text with no structure (previews, test button). TTS-only: a
     *  voice pack can't stitch what it can't decompose. */
    data class Raw(val text: String) : Utterance

    /** The sentence the TTS engine speaks — the single source of phrasing. */
    fun toText(): String = when (this) {
        is TimeAnnouncement -> Phrasebook.timeAnnouncement(time, style, includeSeconds)
        is TimerCues -> cues.joinToString(". ") { cue ->
            when (cue) {
                is TimerCue.Checkpoint -> Phrasebook.timerRemaining(cue.remaining)
                is TimerCue.Halfway -> Phrasebook.timerHalfway(cue.remaining)
                is TimerCue.Countdown -> Phrasebook.numberWords(cue.number)
                TimerCue.TimesUp -> Phrasebook.TIMES_UP
            }
        }
        is TimerStarted -> Phrasebook.timerStarted(duration)
        is StopwatchElapsed -> Phrasebook.stopwatchElapsed(elapsed)
        is StopwatchLap -> Phrasebook.stopwatchLap(lapNumber, lapTime)
        is Raw -> text
    }
}

/**
 * What the controllers talk to instead of a raw [Speaker]: an announcer
 * takes structured [Utterance]s so the active voice pack (if any) can
 * render them from recorded clips, falling back to TTS for anything the
 * pack can't say (whole utterance at a time — mixing one robot word into a
 * human sentence is worse than all-robot; docs/VOICE_PACKS.md).
 */
interface Announcer {
    /** Say [utterance] under the same priority rules as [Speaker.speak]. */
    fun announce(utterance: Utterance, priority: Int = Speaker.PRIORITY_CLOCK)

    /** Stop mid-utterance (both pack playback and TTS). */
    fun stop()
}
