package io.github.johnjeffords.talkingclock.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.johnjeffords.talkingclock.TalkingClockApp
import io.github.johnjeffords.talkingclock.alarm.AlarmScheduler
import io.github.johnjeffords.talkingclock.data.AlarmRepository
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.domain.alarm.nextTriggerTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * State holder for the alarm list and editor. Every mutation re-syncs
 * AlarmManager immediately — the repository is the truth, the scheduler
 * mirrors it, and they must never disagree.
 */
class AlarmViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    /** The alarm list plus the "Next alarm in …" header line's data. */
    data class UiState(
        val alarms: List<Alarm> = emptyList(),
        /** Time until the soonest enabled alarm, or null if none. */
        val nextAlarmIn: Duration? = null,
    )

    val uiState: StateFlow<UiState> =
        repository.alarms.map { alarms ->
            val now = LocalDateTime.now()
            val soonest = alarms
                .filter { it.enabled }
                .minOfOrNull { java.time.Duration.between(now, nextTriggerTime(it, now)) }
            UiState(alarms = alarms, nextAlarmIn = soonest)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState(),
        )

    /** A fresh alarm for the editor (not yet saved). */
    fun newAlarm(): Alarm = Alarm(
        id = UUID.randomUUID().toString(),
        hour = 7,
        minute = 0,
    )

    fun save(alarm: Alarm) {
        viewModelScope.launch {
            repository.upsert(alarm)
            if (alarm.enabled) scheduler.schedule(alarm) else scheduler.cancel(alarm.id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            scheduler.cancel(id)
        }
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(alarm.id, enabled)
            if (enabled) scheduler.schedule(alarm.copy(enabled = true)) else scheduler.cancel(alarm.id)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TalkingClockApp
                AlarmViewModel(app.alarmRepository, app.alarmScheduler)
            }
        }
    }
}
