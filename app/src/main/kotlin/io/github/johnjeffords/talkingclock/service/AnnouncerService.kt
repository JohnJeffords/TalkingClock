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
import io.github.johnjeffords.talkingclock.announce.TimerController
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.format.DateTimeFormatter

/**
 * The foreground service that keeps announcements alive with the screen off.
 * It contains NO announcing logic — that's all in [SpeakingClockController]
 * and [TimerController] — this class only fulfills the platform obligations:
 * hold the process in the foreground, show the mandatory notification(s),
 * keep their text current, and stop when EVERYTHING is idle.
 *
 * Both features share this one service. The timer owns the foreground slot
 * when it's alive (it's the more urgent thing); the speaking clock rides in
 * a second notification when both run at once. Controllers only ever START
 * the service ([ensureRunning]); stopping is decided here, from observed
 * state — that's what prevents the timer's end from killing the service
 * out from under a still-armed speaking clock.
 *
 * Known limitation, deliberately accepted for now: between announcements we
 * sleep with coroutine delays and hold no wakelock, so deep-Doze on some
 * OEMs may defer an announcement. If on-device testing shows real deferrals
 * we'll add a partial wakelock (and the WAKE_LOCK permission) then — not
 * before the need is proven (docs/CODE_STYLE.md ladder, rung 1).
 */
class AnnouncerService : Service() {

    private val app get() = application as TalkingClockApp
    private val clockController: SpeakingClockController get() = app.speakingClockController
    private val timerController: TimerController get() = app.timerController

    /** Scope for observing controller state; dies with the service. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification actions route back here as intents.
        when (intent?.action) {
            ACTION_STOP_CLOCK -> {
                clockController.disarm(); maybeStop(); return START_NOT_STICKY
            }
            ACTION_STOP_TIMER -> {
                timerController.reset(); maybeStop(); return START_NOT_STICKY
            }
            ACTION_PAUSE_TIMER -> {
                timerController.pause(); return START_NOT_STICKY
            }
            ACTION_RESUME_TIMER -> {
                timerController.resume(); return START_NOT_STICKY
            }
        }

        // Enter the foreground with the typed FGS declaration API 34 requires.
        enterForeground(buildForegroundNotification())

        // Track both controllers. The notification-worthy fields change at
        // most once a second (whole seconds remaining), so distinctUntilChanged
        // keeps us from re-notifying at the timer's 5 Hz sampling rate.
        combine(clockController.state, timerController.state) { clock, timer ->
            DisplayState(
                clockArmed = clock.isArmed,
                clockLine = clock.interval?.let { interval ->
                    getString(
                        R.string.notif_speaking_body,
                        interval.label,
                        clock.nextAt?.format(TIME_FORMAT) ?: "",
                    )
                },
                timerPhase = timer.snapshot.phase,
                timerLine = timerLine(timer),
            )
        }
            .distinctUntilChanged()
            .onEach { display -> render(display) }
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

    // --- Rendering ---

    /** The few fields the notifications actually show. */
    private data class DisplayState(
        val clockArmed: Boolean,
        val clockLine: String?,
        val timerPhase: TimerEngine.Phase,
        val timerLine: String?,
    )

    private fun render(display: DisplayState) {
        val timerAlive = display.timerPhase != TimerEngine.Phase.Idle
        if (!timerAlive && !display.clockArmed) {
            stopSelf()
            return
        }

        val manager = getSystemService(NotificationManager::class.java)

        // Foreground slot (ID 1): the timer when alive, else the clock.
        manager.notify(FOREGROUND_ID, buildForegroundNotification())

        // Companion slot (ID 2): the clock, only while the timer holds ID 1.
        if (timerAlive && display.clockArmed) {
            manager.notify(COMPANION_ID, buildClockNotification())
        } else {
            manager.cancel(COMPANION_ID)
        }
    }

    private fun maybeStop() {
        val timerAlive = timerController.state.value.snapshot.phase != TimerEngine.Phase.Idle
        if (!timerAlive && !clockController.state.value.isArmed) stopSelf()
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    // --- Notification builders ---

    private fun buildForegroundNotification(): Notification {
        val timer = timerController.state.value
        return if (timer.snapshot.phase != TimerEngine.Phase.Idle) {
            buildTimerNotification(timer)
        } else {
            buildClockNotification()
        }
    }

    /** "Timer · 12:34 remaining" with Pause/Resume + Stop (design frame 13). */
    private fun buildTimerNotification(timer: TimerController.State): Notification {
        val paused = timer.snapshot.phase == TimerEngine.Phase.Paused
        val pauseOrResume = if (paused) {
            action(R.string.notif_action_resume, ACTION_RESUME_TIMER)
        } else {
            action(R.string.notif_action_pause, ACTION_PAUSE_TIMER)
        }
        return baseBuilder()
            .setContentTitle(getString(R.string.notif_timer_title))
            .setContentText(timerLine(timer))
            .setContentIntent(openAppIntent())
            .addAction(pauseOrResume)
            .addAction(action(R.string.notif_action_stop, ACTION_STOP_TIMER))
            .build()
    }

    /** "Speaking clock · every 5 min · next at 10:28 · tap to stop". */
    private fun buildClockNotification(): Notification {
        val clock = clockController.state.value
        val text = clock.interval?.let { interval ->
            getString(
                R.string.notif_speaking_body,
                interval.label,
                clock.nextAt?.format(TIME_FORMAT) ?: "",
            )
        } ?: getString(R.string.notif_speaking_title)

        // Tapping the clock notification body STOPS the speaking clock —
        // the design's one-obvious-gesture escape hatch.
        val stop = pending(ACTION_STOP_CLOCK)
        return baseBuilder()
            .setContentTitle(getString(R.string.notif_speaking_title))
            .setContentText(text)
            .setContentIntent(stop)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .addAction(0, getString(R.string.notif_action_open), openAppIntent())
            .build()
    }

    private fun timerLine(timer: TimerController.State): String? {
        val snap = timer.snapshot
        if (snap.phase == TimerEngine.Phase.Idle) return null
        return when (snap.phase) {
            TimerEngine.Phase.Finished ->
                if (snap.overtime.seconds > 0) {
                    getString(R.string.notif_timer_overtime, formatMinSec(snap.overtime))
                } else {
                    getString(R.string.notif_timer_done)
                }
            TimerEngine.Phase.Paused ->
                getString(R.string.notif_timer_paused, formatMinSec(snap.remaining))
            else ->
                getString(R.string.notif_timer_remaining, formatMinSec(snap.remaining))
        }
    }

    /** 754 s -> "12:34"; hours spill into minutes (a 2 h timer is "120:00"). */
    private fun formatMinSec(d: java.time.Duration): String =
        "%d:%02d".format(d.seconds / 60, d.seconds % 60)

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // silent text refreshes, no repeat buzzes
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

    private fun action(labelRes: Int, intentAction: String): NotificationCompat.Action =
        NotificationCompat.Action(0, getString(labelRes), pending(intentAction))

    private fun pending(intentAction: String): PendingIntent =
        PendingIntent.getService(
            this,
            intentAction.hashCode(), // distinct request code per action
            Intent(this, AnnouncerService::class.java).setAction(intentAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_speaking),
            // LOW importance: these notifications are status handles, not
            // alerts — they must never buzz or peek on their own.
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "speaking_clock"
        private const val FOREGROUND_ID = 1
        private const val COMPANION_ID = 2
        private const val PKG = "io.github.johnjeffords.talkingclock"
        private const val ACTION_STOP_CLOCK = "$PKG.STOP_CLOCK"
        private const val ACTION_STOP_TIMER = "$PKG.STOP_TIMER"
        private const val ACTION_PAUSE_TIMER = "$PKG.PAUSE_TIMER"
        private const val ACTION_RESUME_TIMER = "$PKG.RESUME_TIMER"

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

        /** Controllers poke this when something starts; stopping is decided
         *  inside the service from observed state (see class doc). */
        fun ensureRunning(context: Context) {
            context.startForegroundService(Intent(context, AnnouncerService::class.java))
        }
    }
}
