package com.apexforge.app.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.local.LoggedSet
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.local.WorkoutSetEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.ExerciseRepository
import com.apexforge.app.data.repo.WorkoutRepository
import com.apexforge.app.domain.RestTimer
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.toKg
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.NumberInput
import com.apexforge.app.ui.formatClock
import com.apexforge.app.ui.formatWeight
import com.apexforge.app.ui.library.ExercisePickerSheet
import com.apexforge.app.ui.rememberElapsedSeconds
import com.apexforge.app.ui.theme.LocalHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseBlock(val exerciseId: String, val name: String, val sets: List<WorkoutSetEntity>, val previous: List<LoggedSet>)

data class ActiveUi(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val blocks: List<ExerciseBlock> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
) {
    val completedSets: Int get() = blocks.sumOf { b -> b.sets.count { it.completed } }
    val totalSets: Int get() = blocks.sumOf { it.sets.size }
}

data class TimerUi(val remainingSec: Int, val totalSec: Int, val paused: Boolean, val finished: Boolean)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: WorkoutRepository,
    exercises: ExerciseRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    val sessionId: String = checkNotNull(savedState["sessionId"])

    private val previousCache = MutableStateFlow<Map<String, List<LoggedSet>>>(emptyMap())

    val ui: StateFlow<ActiveUi> = combine(
        repository.observeSession(sessionId),
        repository.observeSets(sessionId),
        exercises.exercises,
        previousCache,
        prefs.prefs,
    ) { session, sets, all, previous, p ->
        val names = all.associate { it.id to it.name }
        val blocks = sets.groupBy { it.exerciseId }.entries
            .sortedBy { (_, list) -> list.minOf { it.exerciseOrder } }
            .map { (id, list) -> ExerciseBlock(id, names[id] ?: "Exercise", list.sortedBy { it.setNumber }, previous[id].orEmpty()) }
        ActiveUi(false, session, blocks, p.units)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveUi())

    init {
        // Load "previous" values once per exercise that appears in the session.
        viewModelScope.launch {
            repository.observeSets(sessionId)
                .map { sets -> sets.map { it.exerciseId }.distinct() }
                .distinctUntilChanged()
                .collect { ids ->
                    val missing = ids.filter { it !in previousCache.value }
                    if (missing.isNotEmpty()) {
                        val loaded = missing.associateWith { repository.previousSets(it, sessionId) }
                        previousCache.update { it + loaded }
                    }
                }
        }
    }

    // ---- Rest timer (monotonic clock, survives rotation, restarted if the process dies) ----
    private val timer = RestTimer { SystemClock.elapsedRealtime() }
    private val _timer = MutableStateFlow<TimerUi?>(null)
    val timerUi: StateFlow<TimerUi?> = _timer
    private var tickJob: Job? = null

    private fun publishTimer() {
        if (!timer.isActive) { _timer.value = null; return }
        val remaining = timer.remainingMs()
        _timer.value = TimerUi(
            remainingSec = ((remaining + 999) / 1000).toInt(),
            totalSec = (timer.totalMs / 1000).toInt(),
            paused = timer.isPaused,
            finished = timer.isFinished,
        )
    }

    fun startTimer(seconds: Int) {
        if (seconds <= 0) return
        timer.start(seconds)
        publishTimer()
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (timer.isActive) {
                publishTimer()
                if (timer.isFinished) break
                delay(250)
            }
        }
    }

    fun timerAdjust(deltaSec: Int) { timer.adjust(deltaSec); publishTimer(); restartTick() }
    fun timerTogglePause() { if (timer.isPaused) timer.resume() else timer.pause(); publishTimer(); restartTick() }
    fun timerReset() { timer.reset(); publishTimer(); restartTick() }
    fun timerStop() { tickJob?.cancel(); timer.stop(); _timer.value = null }

    private fun restartTick() {
        if (tickJob?.isActive == true || !timer.isActive || timer.isFinished) return
        tickJob = viewModelScope.launch {
            while (timer.isActive) {
                publishTimer()
                if (timer.isFinished) break
                delay(250)
            }
        }
    }

    // ---- Editing ----
    fun setWeight(setId: Long, displayValue: Double, units: UnitSystem) {
        viewModelScope.launch { repository.setWeight(setId, units.toKg(displayValue)) }
    }

    fun setReps(setId: Long, reps: Double) { viewModelScope.launch { repository.setReps(setId, reps.toInt()) } }

    /** Returns via [onRejected] when a set with no reps is ticked. */
    fun toggleComplete(set: WorkoutSetEntity, onRejected: () -> Unit) {
        viewModelScope.launch {
            val ok = repository.toggleComplete(set.id)
            if (!ok) { onRejected(); return@launch }
            if (!set.completed && prefs.prefs.first().autoRestTimer) startTimer(set.restSec)
            else if (set.completed) timerStop()
        }
    }

    fun addSet(exerciseId: String) { viewModelScope.launch { repository.addSet(sessionId, exerciseId) } }
    fun deleteSet(setId: Long) { viewModelScope.launch { repository.deleteSet(setId) } }
    fun removeExercise(exerciseId: String) { viewModelScope.launch { repository.removeExercise(sessionId, exerciseId) } }
    fun usePrevious(exerciseId: String) { viewModelScope.launch { repository.usePrevious(sessionId, exerciseId) } }
    fun rpeAndNote(setId: Long, rpe: Int?, note: String) { viewModelScope.launch { repository.setRpeAndNote(setId, rpe, note) } }
    fun rename(name: String) { viewModelScope.launch { repository.renameSession(sessionId, name) } }

    fun addExercises(ids: List<String>) {
        viewModelScope.launch { repository.addExercises(sessionId, ids, prefs.prefs.first().defaultRestSec) }
    }

    fun finish(onSaved: () -> Unit, onNothingLogged: () -> Unit) {
        viewModelScope.launch {
            if (repository.finish(sessionId)) { timerStop(); onSaved() } else onNothingLogged()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch { timerStop(); repository.discard(sessionId); onDone() }
    }
}

@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val timer by viewModel.timerUi.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current

    var picker by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var cancelDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var noSetsMessage by remember { mutableStateOf<String?>(null) }
    var editSet by remember { mutableStateOf<WorkoutSetEntity?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // A session that no longer exists (discarded or finished elsewhere) must not leave a blank screen.
    val session = ui.session
    LaunchedEffect(ui.loading, session) { if (!ui.loading && session == null) onDiscarded() }

    if (picker) ExercisePickerSheet({ picker = false }, { viewModel.addExercises(it); picker = false })

    if (finishDialog) {
        val unfinished = ui.totalSets - ui.completedSets
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text("Finish workout?") },
            text = {
                Text(
                    if (unfinished > 0) "$unfinished unfinished set${if (unfinished == 1) "" else "s"} will be discarded. Only completed sets are saved."
                    else "Save this workout to your history.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    finishDialog = false
                    viewModel.finish({ onFinished(viewModel.sessionId) }, { noSetsMessage = "Complete at least one set before finishing, or cancel the workout." })
                }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { finishDialog = false }) { Text("Keep going") } },
        )
    }
    if (cancelDialog) {
        AlertDialog(
            onDismissRequest = { cancelDialog = false },
            title = { Text("Cancel workout?") },
            text = { Text("Everything logged in this workout will be deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { cancelDialog = false; viewModel.discard(onDiscarded) }) { Text("Delete workout") } },
            dismissButton = { TextButton(onClick = { cancelDialog = false }) { Text("Keep it") } },
        )
    }
    noSetsMessage?.let {
        AlertDialog(
            onDismissRequest = { noSetsMessage = null },
            title = { Text("Nothing to save") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { noSetsMessage = null }) { Text("OK") } },
        )
    }
    if (renameDialog && session != null) {
        var text by remember { mutableStateOf(session.name) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Rename workout") },
            text = { ApexTextField(text, { text = it.take(60) }, "Name") },
            confirmButton = { TextButton(onClick = { viewModel.rename(text); renameDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } },
        )
    }
    editSet?.let { target ->
        SetDetailsDialog(
            set = target,
            onDismiss = { editSet = null },
            onSave = { rpe, note -> viewModel.rpeAndNote(target.id, rpe, note); editSet = null },
            onDelete = { viewModel.deleteSet(target.id); editSet = null },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        if (ui.loading || session == null) { LoadingBox(); return@Column }
        val elapsed by rememberElapsedSeconds(session.startedAt)
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ApexIconButton(Icons.Rounded.Close, "Leave workout screen (workout keeps running)", onBack)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(session.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    "${formatClock(elapsed)} · ${ui.completedSets}/${ui.totalSets} sets",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                ApexIconButton(Icons.Rounded.MoreVert, "Workout options", { menuOpen = true })
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameDialog = true })
                    DropdownMenuItem(text = { Text("Cancel workout") }, onClick = { menuOpen = false; cancelDialog = true })
                }
            }
            ApexButton("Finish", { finishDialog = true }, compact = true)
        }

        if (ui.blocks.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    Icons.Rounded.Add, "Add your first exercise",
                    "Pick exercises to start logging sets.",
                    actionLabel = "Add exercises", onAction = { picker = true },
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.blocks, key = { it.exerciseId }) { block ->
                    ExerciseBlockCard(
                        block = block,
                        units = ui.units,
                        onWeight = { id, v -> viewModel.setWeight(id, v, ui.units) },
                        onReps = viewModel::setReps,
                        onToggle = { set ->
                            haptics.confirm()
                            viewModel.toggleComplete(set) { noSetsMessage = "Enter the reps for this set before marking it done." }
                        },
                        onSetDetails = { editSet = it },
                        onAddSet = { viewModel.addSet(block.exerciseId) },
                        onUsePrevious = { viewModel.usePrevious(block.exerciseId) },
                        onRemove = { viewModel.removeExercise(block.exerciseId) },
                    )
                }
                item {
                    ApexButton("Add exercise", { picker = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = Icons.Rounded.Add)
                }
            }
        }

        timer?.let { t ->
            RestTimerBar(t, viewModel::timerAdjust, viewModel::timerTogglePause, viewModel::timerReset, viewModel::timerStop)
        }
    }
}

@Composable
private fun RestTimerBar(
    t: TimerUi,
    onAdjust: (Int) -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (t.finished) "Rest over, next set" else "Rest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(formatClock(t.remainingSec.toLong()), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                ApexIconButton(if (t.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (t.paused) "Resume timer" else "Pause timer", onPause, enabled = !t.finished)
                ApexIconButton(Icons.Rounded.Refresh, "Restart timer", onReset)
                ApexIconButton(Icons.Rounded.Close, "Dismiss timer", onStop)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApexChip("−15s", false, { onAdjust(-15) })
                ApexChip("+15s", false, { onAdjust(15) })
                ApexChip("+30s", false, { onAdjust(30) })
            }
        }
    }
}

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlock,
    units: UnitSystem,
    onWeight: (Long, Double) -> Unit,
    onReps: (Long, Double) -> Unit,
    onToggle: (WorkoutSetEntity) -> Unit,
    onSetDetails: (WorkoutSetEntity) -> Unit,
    onAddSet: () -> Unit,
    onUsePrevious: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ApexCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(block.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                ApexIconButton(Icons.Rounded.MoreVert, "Options for ${block.name}", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    if (block.previous.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Use last session's values") }, onClick = { menu = false; onUsePrevious() })
                    }
                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("PREVIOUS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(units.weightUnit.uppercase(), Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("REPS", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(54.dp))
        }
        block.sets.forEach { set ->
            val prev = block.previous.getOrNull(set.setNumber - 1)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSetDetails(set) }, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(set.setNumber.toString(), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    prev?.let { "${formatWeight(it.weightKg, units)} × ${it.reps}" } ?: "-",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberInput(
                    value = units.fromKg(set.weightKg).let { Math.round(it * 10) / 10.0 },
                    onValueChange = { onWeight(set.id, it) },
                    decimal = true,
                    modifier = Modifier.width(72.dp).height(44.dp),
                    contentDescription = "Weight, set ${set.setNumber} of ${block.name}",
                )
                Spacer(Modifier.width(6.dp))
                NumberInput(
                    value = set.reps.toDouble(),
                    onValueChange = { onReps(set.id, it) },
                    modifier = Modifier.width(64.dp).height(44.dp),
                    contentDescription = "Reps, set ${set.setNumber} of ${block.name}",
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = { onToggle(set) },
                    shape = MaterialTheme.shapes.small,
                    color = if (set.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = if (set.completed) "Mark set ${set.setNumber} not done" else "Mark set ${set.setNumber} done",
                            tint = if (set.completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ApexButton("Add set", onAddSet, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, icon = Icons.Rounded.Add, compact = true)
    }
}

@Composable
private fun SetDetailsDialog(
    set: WorkoutSetEntity,
    onDismiss: () -> Unit,
    onSave: (Int?, String) -> Unit,
    onDelete: () -> Unit,
) {
    var rpe by remember { mutableStateOf(set.rpe) }
    var note by remember { mutableStateOf(set.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set ${set.setNumber}") },
        text = {
            Column {
                Text("Effort (RPE, optional)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 7, 8, 9, 10).forEach { v ->
                        ApexChip(v.toString(), rpe == v, { rpe = if (rpe == v) null else v })
                    }
                }
                Spacer(Modifier.height(12.dp))
                ApexTextField(note, { note = it.take(200) }, "Note")
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete) { Text("Delete this set", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rpe, note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
