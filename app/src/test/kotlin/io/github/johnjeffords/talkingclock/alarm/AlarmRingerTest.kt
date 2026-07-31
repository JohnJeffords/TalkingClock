package io.github.johnjeffords.talkingclock.alarm

import androidx.test.core.app.ApplicationProvider
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.domain.speech.SpeakingStyle
import io.github.johnjeffords.talkingclock.speech.FakeAnnouncer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlarmRingerTest {

    private val alarm = Alarm(
        id = "handoff",
        hour = 7,
        minute = 0,
        announceTime = false,
        vibrate = false,
        handoffIntervalSeconds = 300,
    )

    @Test
    fun `handoff is armed on dismiss and not when ringing starts`() = runTest {
        var armCount = 0
        val ringer = buildRinger { armCount++ }

        ringer.startRinging(alarm)
        assertEquals(0, armCount)

        ringer.dismiss()
        assertEquals(1, armCount)
    }

    @Test
    fun `snooze does not arm the handoff`() = runTest {
        var armCount = 0
        val ringer = buildRinger { armCount++ }

        ringer.startRinging(alarm)
        ringer.snooze()

        assertEquals(0, armCount)
    }

    private fun TestScope.buildRinger(onArm: (Alarm) -> Unit) = AlarmRinger(
        context = ApplicationProvider.getApplicationContext(),
        announcer = FakeAnnouncer(),
        scope = backgroundScope,
        speakingStyle = { SpeakingStyle.Conversational },
        onSnoozed = { _, _ -> },
        onFinished = {},
        onArmSpeakingClock = onArm,
    )
}
