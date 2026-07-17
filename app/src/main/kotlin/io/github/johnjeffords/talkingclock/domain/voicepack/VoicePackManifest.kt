package io.github.johnjeffords.talkingclock.domain.voicepack

import org.json.JSONObject

/**
 * The parsed `pack.json` of a voice pack — the manifest defined in
 * docs/VOICE_PACKS.md. Parsing is strict about what safety requires (a
 * declared open license, sane clip paths) and forgiving about the rest
 * (unknown fields are ignored so future pack formats stay compatible).
 *
 * @property name display name shown in the voice picker.
 * @property language BCP-47-ish language code ("en").
 * @property license SPDX id — REQUIRED; packs without one are refused,
 *   keeping the whole ecosystem redistributable (docs/VOICE_PACKS.md).
 * @property gapMs silence inserted between stitched clips (0–200).
 * @property clips token → relative clip path inside the pack.
 */
data class VoicePackManifest(
    val name: String,
    val language: String,
    val author: String,
    val license: String,
    val gapMs: Int,
    val clips: Map<String, String>,
) {
    companion object {
        const val FORMAT_VERSION = 1

        /**
         * Parse and validate a pack.json. Returns a failure with a
         * human-readable reason rather than throwing — the import screen
         * shows the reason to the pack's author.
         */
        fun parse(json: String): Result<VoicePackManifest> = runCatching {
            val root = JSONObject(json)

            val version = root.optInt("formatVersion", -1)
            require(version == FORMAT_VERSION) {
                "Unsupported formatVersion $version (this app understands $FORMAT_VERSION)"
            }

            val license = root.optString("license").trim()
            require(license.isNotEmpty()) {
                "The manifest must declare an open 'license' (an SPDX id like CC-BY-4.0)"
            }

            val clipsJson = root.optJSONObject("clips")
                ?: throw IllegalArgumentException("The manifest has no 'clips' object")
            val clips = buildMap {
                clipsJson.keys().forEach { token ->
                    val path = clipsJson.getString(token)
                    require(isSafeRelativePath(path)) {
                        "Clip path '$path' is not a safe relative path"
                    }
                    put(token, path)
                }
            }
            require(clips.isNotEmpty()) { "The manifest lists no clips" }

            VoicePackManifest(
                name = root.optString("name").ifBlank { "Unnamed pack" },
                language = root.optString("language").ifBlank { "en" },
                author = root.optString("author"),
                license = license,
                gapMs = root.optInt("gapMs", 40).coerceIn(0, 200),
                clips = clips,
            )
        }

        /**
         * A clip path must stay INSIDE the pack: no absolute paths, no "..".
         * (Defends against zip-slip — a hostile pack trying to write or read
         * outside its own directory.)
         */
        fun isSafeRelativePath(path: String): Boolean =
            path.isNotBlank() &&
                !path.startsWith("/") &&
                !path.startsWith("\\") &&
                !path.contains(':') &&
                path.split('/', '\\').none { it == ".." }
    }
}

/** Which features a pack can fully voice (docs/VOICE_PACKS.md coverage). */
data class PackCoverage(
    val clock: Boolean,
    val timer: Boolean,
    val stopwatch: Boolean,
    /** Tokens missing for the features that aren't covered. */
    val missing: List<String>,
)

/**
 * Compute a pack's coverage report ("Can speak: clock ✓ timer ✓
 * stopwatch ✗ — missing: stopwatch.lap"). Number words are covered when
 * the composable atoms 0–20 and the tens are all present — exact clips
 * like num.47 are optional extras that just win over composition.
 */
fun coverageOf(manifest: VoicePackManifest): PackCoverage {
    val tokens = manifest.clips.keys

    val numberAtoms = (0..20).map { "num.$it" } + listOf("num.30", "num.40", "num.50")
    val clockTokens = listOf("time.oclock", "time.am", "time.pm")
    val timerTokens = listOf(
        "timer.minutes-remaining", "timer.seconds-remaining", "timer.times-up",
    )
    val stopwatchTokens = listOf(
        "stopwatch.lap", "time.minute", "time.minutes", "time.second", "time.seconds",
    )

    fun missingOf(required: List<String>) = required.filterNot { it in tokens }

    val missingNumbers = missingOf(numberAtoms)
    val missingClock = missingOf(clockTokens) + missingNumbers
    val missingTimer = missingOf(timerTokens) + missingNumbers
    val missingStopwatch = missingOf(stopwatchTokens) + missingNumbers

    return PackCoverage(
        clock = missingClock.isEmpty(),
        timer = missingTimer.isEmpty(),
        stopwatch = missingStopwatch.isEmpty(),
        missing = (missingClock + missingTimer + missingStopwatch).distinct(),
    )
}
