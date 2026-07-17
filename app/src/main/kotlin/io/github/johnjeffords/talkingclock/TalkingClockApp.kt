package io.github.johnjeffords.talkingclock

import android.app.Application

/**
 * The Application object — created once when the app process starts, before
 * any Activity. It's currently empty, but it's the place the app's shared
 * dependencies (settings store, the speech engine, the timer engine) will be
 * wired together by hand in a small "AppContainer" as those features land.
 *
 * We use manual dependency injection (a plain container) rather than a DI
 * framework like Hilt: this app is small enough that a framework would add
 * build time, APK size, and indirection without paying for itself
 * (see docs/ARCHITECTURE.md).
 */
class TalkingClockApp : Application()
