package io.github.johnjeffords.talkingclock.ui.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.johnjeffords.talkingclock.domain.alarm.Alarm
import io.github.johnjeffords.talkingclock.ui.rememberHapticAction

/**
 * The Alarm tab: the list, with the editor layered over it while an alarm
 * is being added/edited. Editing is tab-local state (not a nav route): the
 * editor is modal to this tab and Back-from-editor just closes it.
 */
@Composable
fun AlarmRoute() {
    val viewModel: AlarmViewModel = viewModel(factory = AlarmViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The alarm being edited (null = showing the list). Saveable so a
    // rotation mid-edit keeps the editor open on the same alarm id.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var newDraft by rememberSaveable { mutableStateOf(false) }

    // remember(newDraft) pins the fresh draft's UUID across recompositions —
    // regenerating it each frame would silently change the alarm's identity.
    val freshDraft = remember(newDraft) { if (newDraft) viewModel.newAlarm() else null }
    val editing: Alarm? = freshDraft ?: uiState.alarms.find { it.id == editingId }

    if (editing != null) {
        AlarmEditScreen(
            initial = editing,
            onSave = rememberHapticAction { alarm ->
                viewModel.save(alarm)
                editingId = null
                newDraft = false
            },
            onDelete = if (newDraft) {
                null // an unsaved draft has nothing to delete
            } else {
                rememberHapticAction {
                    viewModel.delete(editing.id)
                    editingId = null
                }
            },
        )
    } else {
        AlarmListScreen(
            uiState = uiState,
            onAdd = rememberHapticAction { newDraft = true },
            onEdit = rememberHapticAction<Alarm> { editingId = it.id },
            onSetEnabled = rememberHapticAction(viewModel::setEnabled),
        )
    }
}
