package io.github.johnjeffords.talkingclock.speech

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the `<queries>` manifest entry that lets the app SEE and bind to
 * the system text-to-speech engine on Android 11+ (API 30+).
 *
 * Why this test exists: without that entry, package-visibility filtering
 * hides third-party TTS engines (SherpaTTS, RHVoice, …), so TextToSpeech
 * can't bind one, its init reports failure, and the app wrongly shows
 * "no speech engine found" — which once broke the whole MVP on a real
 * phone that DID have an engine installed. A careless manifest edit could
 * silently reintroduce that, and no emulator smoke test would catch it
 * (the CI images have no engine installed anyway). This cheap source-level
 * check does.
 *
 * (Unit tests run with the `app` module dir as the working directory, so
 * the manifest resolves at this relative path.)
 */
class TtsSpeakerManifestTest {

    private val manifest: String by lazy {
        File("src/main/AndroidManifest.xml").readText()
    }

    @Test
    fun `manifest declares the TTS_SERVICE queries entry`() {
        assertTrue(
            "AndroidManifest.xml must keep a <queries> block — otherwise the " +
                "app can't see other apps at all on Android 11+.",
            manifest.contains("<queries>"),
        )
        assertTrue(
            "AndroidManifest.xml must query android.intent.action.TTS_SERVICE, " +
                "or system text-to-speech silently breaks on Android 11+.",
            manifest.contains("android.intent.action.TTS_SERVICE"),
        )
    }
}
