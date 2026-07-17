package io.github.johnjeffords.talkingclock.alarm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.ui.alarm.AlarmRingingScreen
import io.github.johnjeffords.talkingclock.ui.theme.TalkingClockTheme
import io.github.johnjeffords.talkingclock.ui.theme.ThemeChoice

/**
 * The full-screen ringing UI (design frame 25), launched over the
 * lockscreen by the alarm notification's full-screen intent. It's a
 * separate Activity from MainActivity precisely so it can pop over
 * whatever the user was doing (or over nothing, on a locked phone).
 *
 * It draws whatever [io.github.johnjeffords.talkingclock.alarm.AlarmRinger]
 * says is ringing — and finishes itself the moment nothing is (snoozed,
 * dismissed, or answered from another surface).
 */
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lockscreen with the screen forced on — the entire
        // point of a ringing screen. (The manifest attributes cover this on
        // some OEMs; the runtime calls are the reliable path on API 27+.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val app = application as TalkingClockApp
        setContent {
            // Dark always: this screen exists for 7 AM in a dark bedroom.
            TalkingClockTheme(ThemeChoice.Dark) {
                val ringing by app.alarmRinger.ringing.collectAsStateWithLifecycle()
                val alarm = ringing
                if (alarm == null) {
                    // Answered (possibly elsewhere): nothing to show.
                    AlarmReceiver.cancelNotification(this)
                    finish()
                } else {
                    AlarmRingingScreen(
                        alarm = alarm,
                        onSnooze = {
                            app.alarmRinger.snooze()
                            AlarmReceiver.cancelNotification(this)
                            finish()
                        },
                        onDismiss = {
                            app.alarmRinger.dismiss()
                            AlarmReceiver.cancelNotification(this)
                            finish()
                        },
                    )
                }
            }
        }
    }
}
