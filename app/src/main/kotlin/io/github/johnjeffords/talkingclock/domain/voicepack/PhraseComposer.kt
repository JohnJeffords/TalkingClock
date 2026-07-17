package io.github.johnjeffords.talkingclock.domain.voicepack

import io.github.johnjeffords.talkingclock.domain.timer.TimerCue
import io.github.johnjeffords.talkingclock.speech.Utterance
import java.time.Duration

/**
 * Turns an [Utterance] into the ordered token list a voice pack should
 * play — or null when the pack can't fully voice it, in which case the
 * WHOLE utterance falls back to TTS (docs/VOICE_PACKS.md: one robot word
 * inside a human sentence is worse than all-robot).
 *
 * Composition follows the spec's Asterisk-style rules: a number is the
 * exact clip ("num.47") when the pack recorded one, else tens + ones
 * ("num.40", "num.7"). Pure function of (manifest, utterance) — fully
 * unit-tested with no audio involved.
 */
object PhraseComposer {

    /** Token sequence for [utterance], or null → fall back to TTS. */
    fun compose(manifest: VoicePackManifest, utterance: Utterance): List<String>? {
        return when (utterance) {
            is Utterance.TimeAnnouncement -> composeTime(manifest, utterance)
            is Utterance.TimerCues -> composeTimerCues(manifest, utterance.cues)
            is Utterance.TimerStarted -> null // "Timer started" stays TTS: no pack grammar for it
            is Utterance.StopwatchElapsed -> composeDuration(manifest, utterance.elapsed)
            is Utterance.StopwatchLap -> composeLap(manifest, utterance)
            is Utterance.Raw -> null // no structure to stitch
        }
    }

    /** "Lap three: one minute, two seconds" = stopwatch.lap + number + duration. */
    private fun composeLap(
        manifest: VoicePackManifest,
        utterance: Utterance.StopwatchLap,
    ): List<String>? {
        val lapToken = tokenIfPresent(manifest, "stopwatch.lap") ?: return null
        val numberTokens = number(manifest, utterance.lapNumber) ?: return null
        val durationTokens = composeDuration(manifest, utterance.lapTime) ?: return null
        return listOf(lapToken) + numberTokens + durationTokens
    }

    // --- Time of day ---------------------------------------------------------

    /**
     * The spec's time composition: `num.<hour12> [num.<minute>] time.am/pm`,
     * with `time.oclock` on the hour. Seconds and the non-Digits speaking
     * styles aren't in the pack grammar — those fall back to TTS.
     */
    private fun composeTime(
        manifest: VoicePackManifest,
        utterance: Utterance.TimeAnnouncement,
    ): List<String>? {
        if (utterance.includeSeconds) return null
        val hour12 = ((utterance.time.hour + 11) % 12) + 1
        val minute = utterance.time.minute
        val meridiem = if (utterance.time.hour < 12) "time.am" else "time.pm"

        val hourTokens = number(manifest, hour12) ?: return null
        val minuteTokens = when (minute) {
            0 -> listOf(tokenIfPresent(manifest, "time.oclock") ?: return null)
            else -> number(manifest, minute) ?: return null
        }
        val meridiemToken = tokenIfPresent(manifest, meridiem) ?: return null
        return hourTokens + minuteTokens + meridiemToken
    }

    // --- Timer cues ----------------------------------------------------------

    private fun composeTimerCues(
        manifest: VoicePackManifest,
        cues: List<TimerCue>,
    ): List<String>? {
        val result = mutableListOf<String>()
        for (cue in cues) {
            val tokens = when (cue) {
                is TimerCue.Checkpoint -> composeRemaining(manifest, cue.remaining)
                // Halfway = the remaining time + the optional flavor clip;
                // without the flavor clip the whole cue falls back so the
                // pack and TTS never disagree about what halfway sounds like.
                is TimerCue.Halfway ->
                    composeRemaining(manifest, cue.remaining)?.plus(
                        tokenIfPresent(manifest, "timer.halfway") ?: return null,
                    )
                is TimerCue.Countdown -> number(manifest, cue.number)
                TimerCue.TimesUp ->
                    tokenIfPresent(manifest, "timer.times-up")?.let(::listOf)
            } ?: return null
            result += tokens
        }
        return result.takeIf { it.isNotEmpty() }
    }

    /** "N minutes remaining" / "N seconds remaining" — whole units only;
     *  mixed ("1 min 30 s remaining") falls back to TTS. */
    private fun composeRemaining(
        manifest: VoicePackManifest,
        remaining: Duration,
    ): List<String>? {
        val unitToken = when {
            remaining.seconds % 60 == 0L -> "timer.minutes-remaining"
            remaining < Duration.ofMinutes(1) -> "timer.seconds-remaining"
            else -> return null
        }
        val amount = if (remaining.seconds % 60 == 0L) {
            remaining.toMinutes().toInt()
        } else {
            remaining.seconds.toInt()
        }
        val numberTokens = number(manifest, amount) ?: return null
        val unit = tokenIfPresent(manifest, unitToken) ?: return null
        return numberTokens + unit
    }

    // --- Durations ("one minute, two seconds") --------------------------------

    private fun composeDuration(manifest: VoicePackManifest, d: Duration): List<String>? {
        if (d.toHours() > 0) return null // hour-long laps: TTS handles it
        val minutes = (d.toMinutes() % 60).toInt()
        val seconds = (d.seconds % 60).toInt()
        val result = mutableListOf<String>()
        if (minutes > 0) {
            result += number(manifest, minutes) ?: return null
            result += tokenIfPresent(manifest, if (minutes == 1) "time.minute" else "time.minutes")
                ?: return null
        }
        if (seconds > 0 || minutes == 0) {
            result += number(manifest, seconds) ?: return null
            result += tokenIfPresent(manifest, if (seconds == 1) "time.second" else "time.seconds")
                ?: return null
        }
        return result
    }

    // --- Numbers ---------------------------------------------------------------

    /** Exact clip wins; else tens + ones; null when atoms are missing. */
    fun number(manifest: VoicePackManifest, n: Int): List<String>? {
        if (n !in 0..59) return null
        tokenIfPresent(manifest, "num.$n")?.let { return listOf(it) }
        if (n < 20) return null // no exact clip and it's not composable
        val tens = tokenIfPresent(manifest, "num.${n / 10 * 10}") ?: return null
        val ones = tokenIfPresent(manifest, "num.${n % 10}") ?: return null
        return listOf(tens, ones)
    }

    private fun tokenIfPresent(manifest: VoicePackManifest, token: String): String? =
        token.takeIf { manifest.clips.containsKey(it) }
}
