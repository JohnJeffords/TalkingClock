package io.github.johnjeffords.talkingclock

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.johnjeffords.talkingclock.alarm.BootReceiver
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WallClockChangeReceiverTest {

    @Test
    fun `time and timezone broadcasts trigger realignment`() {
        var changes = 0
        val receiver = WallClockChangeReceiver { changes++ }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_DATE_CHANGED))

        assertEquals(2, changes)
    }

    @Test
    fun `alarm rescheduler is manifest registered for wall clock changes`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                PackageManager.ResolveInfoFlags.of(0),
            )

            assertTrue(
                "$action did not resolve to the alarm rescheduler",
                receivers.any { it.activityInfo.name == BootReceiver::class.java.name },
            )
        }
    }
}
