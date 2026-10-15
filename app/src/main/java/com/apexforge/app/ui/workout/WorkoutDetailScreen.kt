package com.apexforge.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.local.WorkoutSetEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.StartRequest
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.data.repo.WorkoutRepository
import com.apexforge.app.domain.Calc
import com.apexforge.app.domain.PRHit
import com.apexforge.app.domain.SessionSummary
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.toKg
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.NumberInput
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.StatTile
import com.apexforge.app.ui.formatDateTime
import com.apexforge.app.ui.formatMinutes
import com.apexforge.app.ui.formatVolume
import com.apexforge.app.ui.formatWeight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUi(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val summary: SessionSummary? = null,
    val groups: List<Pair<String, List<WorkoutSetEntity>>> = emptyList(),
    val exerciseIds: Map<String, String> = emptyMap(),
    val prs: List<PRHit> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: WorkoutRepository,
    stats: StatsRepository,
    prefs: PreferencesRepository,
) : ViewModel() {
    val sessionId: String = checkNotNull(savedState["sessionId"])
    val fresh: Boolean = savedState["fresh"] ?: false

    val ui: StateFlow<DetailUi> = combine(
        repository.observeSession(sessionId),
        repository.observeSets(sessionId),
        stats.snapshot,
        prefs.prefs,
    ) { session, sets, snap, p ->
        val completed = sets.filter { it.completed }
        val groups = completed.groupBy { it.exerciseId }.entries
            .sortedBy { (_, l) -> l.minOf { it.exerciseOrder } }
            .map { (id, l) -> id to l.sortedBy { it.setNumber } }
        DetailUi(
            loading = false,
            session = session,
            summary = snap.sessions.firstOrNull { it.id == sessionId },
            groups = groups,
            exerciseIds = groups.associate { (id, _) -> id to snap.exerciseName(id) },
            prs = snap.prHits.filter { it.sessionId == sessionId },
            units = p.units,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi())

    fun saveSet(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            repository.setWeight(set.id, weightKg)
            repository.setReps(set.id, reps.coerceAtLeast(1))
        }
    }

    fun deleteSet(setId: Long) { viewModelScope.launch { repository.deleteSet(setId) } }
    fun deleteSession(onDone: () -> Unit) { viewModelScope.launch { repository.discard(sessionId); onDone() } }
}

@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val starter = rememberWorkoutStarter(onOpenWorkout)
    starter.Dialog()
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkoutSetEntity?>(null) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("This removes the workout and its sets from your history and recalculates your stats.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteSession(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    editing?.let { set ->
        var weight by remember(set.id) { mutableStateOf(ui.units.fromKg(set.weightKg).let { Math.round(it * 10) / 10.0 }) }
        var reps by remember(set.id) { mutableStateOf(set.reps.toDouble()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit set ${set.setNumber}") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(ui.units.weightUnit, style = MaterialTheme.typography.labelMedium)
                        NumberInput(weight, { weight = it }, Modifier.fillMaxWidth().height(52.dp), decimal = true, contentDescription = "Weight")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Reps", style = MaterialTheme.typography.labelMedium)
                        NumberInput(reps, { reps = it }, Modifier.fillMaxWidth().height(52.dp), contentDescription = "Reps")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveSet(set, ui.units.toKg(weight), reps.toInt()); editing = null }, enabled = reps >= 1) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.deleteSet(set.id); editing = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = if (viewModel.fresh) "Workout complete" else ui.session?.name ?: "Workout",
            subtitle = ui.session?.let { formatDateTime(it.endedAt ?: it.startedAt) },
            onBack = onBack,
            actions = {
                if (ui.session != null) ApexIconButton(Icons.Rounded.Delete, "Delete workout", { confirmDelete = true }, tint = MaterialTheme.colorScheme.error)
            },
        )
        val session = ui.session
        when {
            ui.loading -> LoadingBox()
            session == null -> EmptyState(Icons.Rounded.SearchOff, "Workout not found", "It may have been deleted.", actionLabel = "Go back", onAction = onBack)
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (ui.prs.isNotEmpty()) {
                    ApexCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer, borderColor = MaterialTheme.colorScheme.primary) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            androidx.compose.material3.Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "${ui.prs.size} personal record${if (ui.prs.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ui.prs.take(6).forEach { hit ->
                            Text(
                                "${ui.exerciseIds[hit.exerciseId] ?: "Exercise"}: ${hit.type.label} ${formatWeight(hit.weightKg, ui.units)} ${ui.units.weightUnit} × ${hit.reps}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                val summary = ui.summary
                if (summary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Duration", formatMinutes(summary.durationSec), Modifier.weight(1f))
                        StatTile("Volume", formatVolume(summary.volumeKg, ui.units), Modifier.weight(1f), unit = ui.units.weightUnit)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Sets", summary.sets.toString(), Modifier.weight(1f))
                        StatTile("Reps", summary.reps.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                }
                ui.groups.forEach { (exerciseId, sets) ->
                    ApexCard(Modifier.fillMaxWidth(), onClick = { onOpenExercise(exerciseId) }) {
                        Text(ui.exerciseIds[exerciseId] ?: "Exercise", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        sets.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Set ${s.setNumber}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (s.weightKg > 0) "${formatWeight(s.weightKg, ui.units)} ${ui.units.weightUnit} × ${s.reps}" else "${s.reps} reps",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                s.rpe?.let { Text("  RPE $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                TextButton(onClick = { editing = s }) { Text("Edit") }
                            }
                            if (s.note.isNotBlank()) {
                                Text(s.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (ui.groups.isEmpty()) {
                    Text("No completed sets in this workout.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                ApexButton(
                    "Repeat this workout", { starter.start(StartRequest.FromSession(session.id)) },
                    Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = Icons.Rounded.Repeat,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
