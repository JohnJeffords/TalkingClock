package io.github.johnjeffords.talkingclock.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    @Test
    fun `rescheduling after a zone change preserves the local alarm time`() {
        val originalZone = TimeZone.getDefault()
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scheduler = AlarmScheduler(context)
            val alarm = Alarm(id = "zone-change", hour = 7, minute = 0)
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val shadow = shadowOf(alarmManager)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Phoenix"))
            scheduler.rescheduleAll(listOf(alarm), LocalDateTime.of(2026, 7, 18, 6, 0))
            val phoenixTrigger = shadow.scheduledAlarms.single().triggerAtMs

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            scheduler.rescheduleAll(listOf(alarm), LocalDateTime.of(2026, 7, 18, 6, 0))
            val newYorkTrigger = shadow.scheduledAlarms.single().triggerAtMs

            assertEquals(
                LocalTime.of(7, 0),
                Instant.ofEpochMilli(newYorkTrigger)
                    .atZone(ZoneId.of("America/New_York"))
                    .toLocalTime(),
            )
            assertEquals(3 * 60 * 60 * 1_000L, phoenixTrigger - newYorkTrigger)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
}
