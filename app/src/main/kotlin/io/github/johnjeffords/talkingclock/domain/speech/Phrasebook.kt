package io.github.johnjeffords.talkingclock.domain.speech

import java.time.LocalTime

/**
 * Turns a time into the exact sentence the app speaks. Pure Kotlin — no
 * Android, no TTS — so every phrasing rule is unit-tested on the plain JVM
 * (PhrasebookTest). The TTS engine just reads whatever string this returns.
 *
 * Number words are spelled out ("ten twenty-four") rather than left as
 * digits for the word styles, so the phrasing is OURS and consistent across
 * TTS engines — different engines read bare digits differently ("one zero
 * two four"), and a talking clock can't be ambiguous. The [SpeakingStyle
 * .Digits] style is the deliberate exception: it hands "10:24 AM" to the
 * engine, which every engine reads as a time.
 *
 * English-only for now. Other languages need their own phrasing rules (word
 * order differs), which is why this is a class we can later select per
 * locale, not a pile of string templates in the UI.
 */
object Phrasebook {

    /**
     * The sentence announcing [time], e.g. "It's ten twenty-four" —
     * see [SpeakingStyle] for the four phrasings.
     */
    fun timeAnnouncement(time: LocalTime, style: SpeakingStyle): String {
        return when (style) {
            SpeakingStyle.Conversational -> "It's ${conversational(time, use24Hour = false)}"
            SpeakingStyle.TwentyFourHour -> "It's ${conversational(time, use24Hour = true)}"
            SpeakingStyle.Digits -> "It's ${digits(time)}"
            SpeakingStyle.Formal -> "It's ${formal(time)}"
        }
    }

    // --- The four phrasings -------------------------------------------------

    /**
     * "ten twenty-four", "two oh five PM"-less casual reading. In 12-hour
     * mode there's no AM/PM — conversational time ("it's ten twenty-four")
     * normally omits it, matching the design's preview text.
     * Special cases: on the hour -> "ten o'clock"; minutes 1-9 -> "ten oh five".
     */
    private fun conversational(time: LocalTime, use24Hour: Boolean): String {
        val hourNumber = if (use24Hour) {
            time.hour
        } else {
            ((time.hour + 11) % 12) + 1   // 0->12, 13->1, ...
        }
        val hourWords = numberWords(hourNumber)
        return when {
            time.minute == 0 && !use24Hour -> "$hourWords o'clock"
            // 24-hour on the hour: "fourteen hundred" (the natural 24h reading)
            time.minute == 0 -> "$hourWords hundred"
            time.minute in 1..9 -> "$hourWords oh ${numberWords(time.minute)}"
            else -> "$hourWords ${numberWords(time.minute)}"
        }
    }

    /** "10:24 AM" / "14:24" — digits, engine reads them as a time. */
    private fun digits(time: LocalTime): String {
        val hour12 = ((time.hour + 11) % 12) + 1
        val meridiem = if (time.hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(hour12, time.minute, meridiem)
    }

    /**
     * "twenty-four minutes past ten" / "one minute past ten" /
     * "ten o'clock" on the hour. Always relative to the 12-hour face.
     */
    private fun formal(time: LocalTime): String {
        val hour12 = ((time.hour + 11) % 12) + 1
        val hourWords = numberWords(hour12)
        return when (time.minute) {
            0 -> "$hourWords o'clock"
            1 -> "one minute past $hourWords"
            else -> "${numberWords(time.minute)} minutes past $hourWords"
        }
    }

    // --- Number-to-words (0..59 is all a clock needs) -----------------------

    private val ones = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty")

    /** English words for 0..59, e.g. 24 -> "twenty-four". */
    fun numberWords(n: Int): String {
        require(n in 0..59) { "A clock never speaks $n; only 0..59 supported" }
        return when {
            n < 20 -> ones[n]
            n % 10 == 0 -> tens[n / 10]
            else -> "${tens[n / 10]}-${ones[n % 10]}"
        }
    }
}
