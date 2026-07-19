package io.github.johnjeffords.talkingclock

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.johnjeffords.talkingclock.data.EngineStateStore
import io.github.johnjeffords.talkingclock.data.SettingsRepository
import io.github.johnjeffords.talkingclock.data.settingsDataStore
import io.github.johnjeffords.talkingclock.domain.stopwatch.StopwatchEngine
import io.github.johnjeffords.talkingclock.domain.timer.TimerEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TalkingClockAppTest {

    @Test
    fun `startup applies settings and restores engines before persistence observes Idle`() = runTest {
        val app = TalkingClockApp()
        Shadows.shadowOf(app).callAttach(ApplicationProvider.getApplicationContext())
        val stateStore = EngineStateStore(app.settingsDataStore)
        val settings = SettingsRepository(app.settingsDataStore)
        val lead = Duration.ofMillis(1_500)
        settings.setSpeechLeadMillis(lead.toMillis().toInt())
        stateStore.saveTimer(Duration.ofMinutes(10), Duration.ofMinutes(4))
        stateStore.saveStopwatch(Duration.ofSeconds(42), emptyList())
        app.appScope = backgroundScope

        app.onCreate()
        app.initializationJob.join()

        assertEquals(TimerEngine.Phase.Paused, app.timerController.state.value.snapshot.phase)
        assertEquals(Duration.ofMinutes(4), app.timerController.state.value.snapshot.remaining)
        assertEquals(StopwatchEngine.Phase.Paused, app.stopwatchController.state.value.snapshot.phase)
        assertEquals(Duration.ofSeconds(42), app.stopwatchController.state.value.snapshot.elapsed)
        assertEquals(lead, app.timerController.speechLead)
        assertEquals(lead, app.stopwatchController.speechLead)
        assertEquals(lead, latchedLead(app.timerController))
        assertEquals(lead, latchedLead(app.stopwatchController))
        assertNotNull(stateStore.loadTimer())
        assertNotNull(stateStore.loadStopwatch())

        app.speaker.shutdown()
    }

    private fun latchedLead(controller: Any): Duration =
        controller.javaClass.getDeclaredField("runLead").run {
            isAccessible = true
            get(controller) as Duration
        }
}
