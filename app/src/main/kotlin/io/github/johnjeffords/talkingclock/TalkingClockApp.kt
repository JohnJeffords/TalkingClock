package io.github.johnjeffords.talkingclock

import android.app.Application
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.TtsSpeaker

/**
 * The Application object — created once when the app process starts, before
 * any Activity. It owns the app's shared dependencies, wired together by
 * hand (manual dependency injection: this app is small enough that a DI
 * framework like Hilt would add build time, APK size, and indirection
 * without paying for itself — see docs/ARCHITECTURE.md).
 */
class TalkingClockApp : Application() {

    /**
     * The one app-wide speech engine. Created EAGERLY at process start
     * rather than lazily on first use: TTS initialization is asynchronous
     * and takes a moment, so warming it here means the user's very first
     * tap-to-speak actually speaks instead of merely starting the engine.
     * The OS reclaims it with the process; no explicit shutdown needed for
     * an app-lifetime singleton.
     */
    lateinit var speaker: Speaker
        private set

    override fun onCreate() {
        super.onCreate()
        speaker = TtsSpeaker.create(this)
    }
}
