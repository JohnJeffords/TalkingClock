package io.github.johnjeffords.talkingclock.alarm

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.speech.Announcer
import io.github.johnjeffords.talkingclock.speech.Speaker
import io.github.johnjeffords.talkingclock.speech.Utterance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What actually happens while an alarm rings: the system default alarm
 * sound loops (RingtoneManager — the device's own tone, so we bundle no
 * audio), the time is spoken every few seconds if the alarm asks for it,
 * and vibration pulses if enabled. Snooze re-fires in 9 minutes.
 *
 * The signature getting-ready handoff: if the alarm arms the speaking clock,
 * it starts THE MOMENT THE ALARM RINGS — not on dismiss. The whole point of
 * this app is to automate what a user wouldn't think to do; making them
 * dismiss first before the clock helps them would just be the manual
 * "start speaking clock" button with extra steps. Snoozing quiets the clock
 * (it re-arms on the next ring); dismissing leaves it running so it keeps
 * announcing the time through getting-ready, until its own auto-off.
 *
 * App-scoped singleton; the ringing Activity observes [ringing] and calls
 * [snooze]/[dismiss]. All side effects (reschedule, one-shot disable, and
 * arming/quieting the speaking clock) are injected as lambdas so this class
 * stays testable and the wiring stays in TalkingClockApp.
 */
class AlarmRinger(
    private val context: Context,
    private val announcer: Announcer,
    private val scope: CoroutineScope,
    private val speakingStyle: () -> SpeakingStyle,
    private val onSnoozed: (Alarm, LocalDateTime) -> Unit,
    private val onFinished: (Alarm) -> Unit,
    private val onArmSpeakingClock: (Alarm) -> Unit,
    private val onQuietSpeakingClock: () -> Unit,
) {

    /** The alarm currently ringing, or null. The ringing screen observes this. */
    private val ringingFlow = MutableStateFlow<Alarm?>(null)
    val ringing: StateFlow<Alarm?> = ringingFlow.asStateFlow()

    private var ringtone: Ringtone? = null
    private var announceJob: Job? = null

    /** Begin ringing [alarm] (called by AlarmReceiver via the app). */
    fun startRinging(alarm: Alarm) {
        stopEffects() // replace any already-ringing alarm; never stack two
        ringingFlow.value = alarm

        // The device's own alarm tone on the ALARM stream — respects the
        // user's alarm volume, costs zero APK bytes.
        ringtone = RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }

        if (alarm.vibrate) startVibration()

        // Arm the speaking clock right now if this alarm hands off to it —
        // it announces the time through the ringing and keeps going after
        // dismiss (see the class doc). Runs independently of this ringer.
        if (alarm.handoffIntervalSeconds != null) onArmSpeakingClock(alarm)

        if (alarm.announceTime) {
            // "It's seven o'clock" every few seconds between tone loops —
            // TIMER priority so nothing talks over an alarm announcement.
            announceJob = scope.launch {
                while (true) {
                    announcer.announce(
                        Utterance.TimeAnnouncement(LocalTime.now(), speakingStyle()),
                        Speaker.PRIORITY_TIMER,
                    )
                    delay(ANNOUNCE_EVERY_MS)
                }
            }
        }
    }

    /** Quiet for 9 minutes (the classic), then ring again. Snoozing also
     *  silences the speaking clock — "not yet" means quiet everything; it
     *  re-arms when the alarm rings again. */
    fun snooze() {
        val alarm = ringingFlow.value ?: return
        stopEffects()
        if (alarm.handoffIntervalSeconds != null) onQuietSpeakingClock()
        ringingFlow.value = null
        onSnoozed(alarm, LocalDateTime.now().plusMinutes(SNOOZE_MINUTES))
    }

    /** Stop ringing. The speaking clock (already armed since the ring, if
     *  configured) keeps running — that's the getting-ready hook. */
    fun dismiss() {
        val alarm = ringingFlow.value ?: return
        stopEffects()
        ringingFlow.value = null
        onFinished(alarm)
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // Buzz-pause loop until cancelled (repeat index 0 = loop the pattern).
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0),
        )
    }

    private fun stopEffects() {
        announceJob?.cancel()
        announceJob = null
        announcer.stop()
        ringtone?.stop()
        ringtone = null
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }

    companion object {
        private const val ANNOUNCE_EVERY_MS = 8_000L
        const val SNOOZE_MINUTES = 9L
    }
}
