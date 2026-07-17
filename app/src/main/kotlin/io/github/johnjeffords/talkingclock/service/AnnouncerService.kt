package io.github.johnjeffords.talkingclock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.johnjeffords.talkingclock.MainActivity
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.announce.SpeakingClockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.format.DateTimeFormatter

/**
 * The foreground service that keeps the speaking clock alive with the screen
 * off. It contains NO announcing logic — that's all in
 * [SpeakingClockController] — this class only fulfills the platform
 * obligations: hold the process in the foreground, show the mandatory
 * notification, keep its text current, and stop when the controller disarms.
 *
 * The notification (design frame 13) reads "Speaking clock · every 5 min ·
 * next at 10:28" with a Stop action, and TAPPING it stops the announcements
 * — the design's rule that turning it off must be one obvious gesture away.
 *
 * Known limitation, deliberately accepted for now: between announcements we
 * sleep with coroutine delays and hold no wakelock, so deep-Doze on some
 * OEMs may defer an announcement. If on-device testing shows real deferrals
 * we'll add a partial wakelock (and the WAKE_LOCK permission) then — not
 * before the need is proven (docs/CODE_STYLE.md ladder, rung 1).
 */
class AnnouncerService : Service() {

    private val controller: SpeakingClockController
        get() = (application as TalkingClockApp).speakingClockController

    /** Scope for observing controller state; dies with the service. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The Stop action / notification tap routes back here as an intent.
        if (intent?.action == ACTION_STOP) {
            controller.disarm() // controller state change stops the service below
            return START_NOT_STICKY
        }

        // Enter the foreground with the typed FGS declaration API 34 requires.
        val notification = buildNotification(controller.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Track the controller: refresh the notification text each cycle and
        // stop the service when the speaking clock turns off.
        controller.state
            .onEach { state ->
                if (!state.isArmed) {
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
            .launchIn(serviceScope)

        // If the system kills us, don't restart with stale state — the user
        // re-arms explicitly. (Honest failure beats a zombie announcer.)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Notification plumbing ---

    private fun buildNotification(state: SpeakingClockController.State): Notification {
        val interval = state.interval
        val nextAtText = state.nextAt?.format(DateTimeFormatter.ofPattern("H:mm"))
        val text = if (interval != null && nextAtText != null) {
            getString(R.string.notif_speaking_body, interval.label, nextAtText)
        } else {
            getString(R.string.notif_speaking_title)
        }

        // Tapping the notification body STOPS the speaking clock (design
        // frame 13's "tap to stop"), so the escape hatch is one tap from
        // anywhere. The Stop action button does the same for discoverability.
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AnnouncerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_speaking_title))
            .setContentText(text)
            .setContentIntent(stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // silent text refreshes, no repeat buzzes
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .addAction(0, getString(R.string.notif_action_open), openAppIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_speaking),
            // LOW importance: the notification is a status handle, not an
            // alert — it must never buzz or peek on its own.
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "speaking_clock"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.johnjeffords.talkingclock.STOP_SPEAKING"

        /** The controller's service-launch lambda: start or stop this service. */
        fun setRunning(context: Context, running: Boolean) {
            val intent = Intent(context, AnnouncerService::class.java)
            if (running) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
