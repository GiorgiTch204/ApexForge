package com.apexforge.app.ui.workout

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.StartRequest
import com.apexforge.app.data.repo.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StarterViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    val active: StateFlow<WorkoutSessionEntity?> = repository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var starting = false

    fun start(request: StartRequest, onStarted: (String) -> Unit) {
        if (starting) return // ignore double taps
        starting = true
        viewModelScope.launch {
            try {
                val rest = prefs.prefs.first().defaultRestSec
                onStarted(repository.startSession(request, rest))
            } finally {
                starting = false
            }
        }
    }

    fun discardAndStart(activeId: String, request: StartRequest, onStarted: (String) -> Unit) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                repository.discard(activeId)
                val rest = prefs.prefs.first().defaultRestSec
                onStarted(repository.startSession(request, rest))
            } finally {
                starting = false
            }
        }
    }
}

/**
 * Entry point for every "start workout" button. If a workout is already running it asks whether to
 * resume it or replace it, so a session can never be silently duplicated or lost.
 */
class WorkoutStarter internal constructor(
    private val viewModel: StarterViewModel,
    private val open: (String) -> Unit,
    private val pending: MutableState<StartRequest?>,
) {
    fun start(request: StartRequest) {
        if (viewModel.active.value == null) viewModel.start(request, open) else pending.value = request
    }

    @Composable
    fun Dialog() {
        val request = pending.value ?: return
        val active = viewModel.active.value
        if (active == null) {
            pending.value = null
            return
        }
        AlertDialog(
            onDismissRequest = { pending.value = null },
            title = { Text("Workout in progress") },
            text = { Text("\"${active.name}\" is still running. Resume it, or discard it and start the new workout?") },
            confirmButton = {
                TextButton(onClick = {
                    pending.value = null
                    open(active.id)
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pending.value = null
                    viewModel.discardAndStart(active.id, request, open)
                }) { Text("Discard and start new") }
            },
        )
    }
}

@Composable
fun rememberWorkoutStarter(open: (String) -> Unit): WorkoutStarter {
    val viewModel = hiltViewModel<StarterViewModel>()
    val pending = remember { mutableStateOf<StartRequest?>(null) }
    return WorkoutStarter(viewModel, open, pending)
}
