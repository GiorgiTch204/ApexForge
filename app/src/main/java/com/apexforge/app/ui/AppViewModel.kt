package com.apexforge.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.WorkoutRepository
import com.apexforge.app.data.seed.ExerciseSeeder
import com.apexforge.app.data.seed.TemplateSeeder
import com.apexforge.app.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Real start-up progress: [completed] of [total] initialisation steps have finished. */
data class InitState(val completed: Int, val total: Int) {
    val ready: Boolean get() = completed >= total
    val progress: Float get() = if (total == 0) 1f else (completed.toFloat() / total).coerceIn(0f, 1f)
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefsRepo: PreferencesRepository,
    private val exerciseSeeder: ExerciseSeeder,
    private val templateSeeder: TemplateSeeder,
    private val reminders: ReminderScheduler,
    workoutRepo: WorkoutRepository,
) : ViewModel() {

    /** Null until DataStore has delivered the first value. */
    val prefs: StateFlow<UserPrefs?> = prefsRepo.prefs
        .map<UserPrefs, UserPrefs?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val seedStep = MutableStateFlow(0)

    val init: StateFlow<InitState> = combine(prefs, seedStep) { loaded, step ->
        InitState(completed = (if (loaded != null) 1 else 0) + step, total = 3)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, InitState(0, 3))

    val activeSession: StateFlow<WorkoutSessionEntity?> = workoutRepo.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _introFinished = MutableStateFlow(false)
    val introFinished: StateFlow<Boolean> = _introFinished.asStateFlow()

    init {
        viewModelScope.launch {
            // A failed step must never trap the user on the intro, so each step is isolated.
            runCatching { prefsRepo.ensureCreatedAt(System.currentTimeMillis()) }
                .onFailure { Log.e(TAG, "created-at failed", it) }
            runCatching { exerciseSeeder.seedIfNeeded() }
                .onFailure { Log.e(TAG, "exercise seeding failed", it) }
            seedStep.value = 1
            runCatching { templateSeeder.seedIfNeeded() }
                .onFailure { Log.e(TAG, "template seeding failed", it) }
            seedStep.value = 2
            runCatching { reminders.sync(prefsRepo.prefs.first(), replace = false) }
                .onFailure { Log.e(TAG, "reminder sync failed", it) }
        }
    }

    fun onIntroFinished() {
        _introFinished.value = true
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
