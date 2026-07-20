package io.github.johnjeffords.talkingclock.ui

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundFeaturePermissionCoordinatorTest {

    @Test
    fun `first denied background start on Android 13 needs an explanation`() {
        assertTrue(
            needsNotificationPermissionAsk(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                alreadyAsked = false,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `granted already-asked and pre-13 starts never interrupt`() {
        assertFalse(needsNotificationPermissionAsk(33, alreadyAsked = false, permissionGranted = true))
        assertFalse(needsNotificationPermissionAsk(33, alreadyAsked = true, permissionGranted = false))
        assertFalse(needsNotificationPermissionAsk(32, alreadyAsked = false, permissionGranted = false))
    }
}
