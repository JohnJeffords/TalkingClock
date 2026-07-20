package io.github.johnjeffords.talkingclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runtime-only receiver for user/system wall-clock changes. */
internal class WallClockChangeReceiver(
    private val onWallClockChanged: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            onWallClockChanged()
        }
    }
}
