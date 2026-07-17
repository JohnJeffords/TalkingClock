package io.github.johnjeffords.talkingclock.domain.speech

/**
 * How the time is phrased when spoken aloud. These four styles come straight
 * from the design's "Speaking style" picker (frame 20), which previews each
 * one with 10:24:
 *
 *  - [Conversational] — "It's ten twenty-four" (the friendly default)
 *  - [Digits]         — "It's 10:24 AM" (we hand the TTS engine digits and
 *                        let it read them; crisp and unambiguous)
 *  - [Formal]         — "It's twenty-four minutes past ten"
 *  - [TwentyFourHour] — "It's fourteen twenty-four" (always 24-hour speech,
 *                        regardless of the display setting)
 */
enum class SpeakingStyle {
    Conversational,
    Digits,
    Formal,
    TwentyFourHour,
}
