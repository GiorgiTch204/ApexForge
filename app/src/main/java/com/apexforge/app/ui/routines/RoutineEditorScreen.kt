package com.apexforge.app.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
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
import com.apexforge.app.data.local.RoutineEntity
import com.apexforge.app.data.local.RoutineExerciseEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.ExerciseRepository
import com.apexforge.app.data.repo.RoutineRepository
import com.apexforge.app.domain.RoutineValidator
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.toKg
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.NumberInput
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.Stepper
import com.apexforge.app.ui.library.ExercisePickerSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EditItem(
    val key: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val name: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val weightKg: Double = 0.0,
    val restSec: Int = 90,
    val notes: String = "",
)

data class EditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val description: String = "",
    val days: Int = 0,
    val items: List<EditItem> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val error: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    private val routineId: String = checkNotNull(savedState["id"])
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state
    private var existing: RoutineEntity? = null
    private var defaultRest = 90

    init {
        viewModelScope.launch {
            val p = prefs.prefs.first()
            defaultRest = p.defaultRestSec
            if (routineId == "new") {
                _state.value = EditorState(loading = false, isNew = true, units = p.units)
                return@launch
            }
            val routine = routines.getRoutine(routineId)
            if (routine == null) {
                _state.value = EditorState(loading = false, isNew = true, units = p.units, error = "That routine no longer exists.")
                return@launch
            }
            existing = routine
            val names = exercises.exercises.first().associate { it.id to it.name }
            val items = routines.getItems(routineId).sortedBy { it.position }.map {
                EditItem(exerciseId = it.exerciseId, name = names[it.exerciseId] ?: "Unknown exercise", sets = it.sets,
                    reps = it.reps, weightKg = it.weightKg, restSec = it.restSec, notes = it.notes)
            }
            _state.value = EditorState(false, false, routine.name, routine.description, routine.scheduledDays, items, p.units)
        }
    }

    fun update(transform: (EditorState) -> EditorState) = _state.update { transform(it).copy(error = null) }

    fun updateItem(key: String, transform: (EditItem) -> EditItem) =
        update { s -> s.copy(items = s.items.map { if (it.key == key) transform(it) else it }) }

    fun addExercises(ids: List<String>) {
        viewModelScope.launch {
            val names = exercises.exercises.first().associate { it.id to it.name }
            update { s ->
                s.copy(items = s.items + ids.mapNotNull { id ->
                    names[id]?.let { EditItem(exerciseId = id, name = it, restSec = defaultRest) }
                })
            }
        }
    }

    fun move(key: String, delta: Int) = update { s ->
        val i = s.items.indexOfFirst { it.key == key }
        val j = i + delta
        if (i < 0 || j !in s.items.indices) s else {
            val list = s.items.toMutableList()
            list[i] = list[j].also { list[j] = list[i] }
            s.copy(items = list)
        }
    }

    fun duplicate(key: String) = update { s ->
        val i = s.items.indexOfFirst { it.key == key }
        if (i < 0) s else s.copy(items = s.items.toMutableList().apply { add(i + 1, s.items[i].copy(key = UUID.randomUUID().toString())) })
    }

    fun remove(key: String) = update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        val error = RoutineValidator.validate(s.name, s.items.map { RoutineValidator.Item(it.sets, it.reps, it.restSec) })
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val base = existing
            val routine = RoutineEntity(
                id = base?.id ?: UUID.randomUUID().toString(),
                name = s.name.trim(),
                description = s.description.trim().take(200),
                templateKey = base?.templateKey,
                scheduledDays = s.days,
                createdAt = base?.createdAt ?: now,
                updatedAt = now,
                lastUsedAt = base?.lastUsedAt,
            )
            val items = s.items.mapIndexed { index, it ->
                RoutineExerciseEntity(routineId = routine.id, exerciseId = it.exerciseId, position = index, sets = it.sets,
                    reps = it.reps, weightKg = it.weightKg, restSec = it.restSec, notes = it.notes.take(200))
            }
            routines.save(routine, items)
            onSaved()
        }
    }
}

@Composable
fun RoutineEditorScreen(onBack: () -> Unit, viewModel: RoutineEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    if (picker) {
        ExercisePickerSheet(onDismiss = { picker = false }, onConfirm = { viewModel.addExercises(it); picker = false })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this routine haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader(if (state.isNew) "New routine" else "Edit routine", onBack = { confirmDiscard = true })
        if (state.loading) { LoadingBox(); return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ApexTextField(state.name, { v -> viewModel.update { it.copy(name = v.take(60)) } }, "Routine name")
            Spacer(Modifier.height(10.dp))
            ApexTextField(state.description, { v -> viewModel.update { it.copy(description = v.take(200)) } }, "Description (optional)")
            Spacer(Modifier.height(16.dp))
            Text("Scheduled days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LABELS.forEachIndexed { i, label ->
                    val on = state.days and dayBit(i) != 0
                    ApexChip(label.take(2), on, { viewModel.update { it.copy(days = it.days xor dayBit(i)) } }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Exercises", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            state.items.forEachIndexed { index, item ->
                ItemCard(item, index, state.items.size, state.units, viewModel)
                Spacer(Modifier.height(10.dp))
            }
            ApexButton("Add exercises", { picker = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = Icons.Rounded.Add)
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            ApexButton("Save routine", { viewModel.save(onBack) }, Modifier.fillMaxWidth(), enabled = !state.saving)
        }
    }
}

@Composable
private fun ItemCard(item: EditItem, index: Int, count: Int, units: UnitSystem, vm: RoutineEditorViewModel) {
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}. ${item.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ApexIconButton(Icons.Rounded.ArrowUpward, "Move ${item.name} up", { vm.move(item.key, -1) }, enabled = index > 0)
            ApexIconButton(Icons.Rounded.ArrowDownward, "Move ${item.name} down", { vm.move(item.key, 1) }, enabled = index < count - 1)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper("Sets", item.sets.toString(),
                { vm.updateItem(item.key) { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } },
                { vm.updateItem(item.key) { it.copy(sets = (it.sets + 1).coerceAtMost(20)) } })
            Stepper("Reps", item.reps.toString(),
                { vm.updateItem(item.key) { it.copy(reps = (it.reps - 1).coerceAtLeast(1)) } },
                { vm.updateItem(item.key) { it.copy(reps = (it.reps + 1).coerceAtMost(100)) } })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Weight (${units.weightUnit})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberInput(
                    value = units.fromKg(item.weightKg).let { Math.round(it * 10) / 10.0 },
                    onValueChange = { v -> vm.updateItem(item.key) { it.copy(weightKg = units.toKg(v)) } },
                    decimal = true,
                    modifier = Modifier.width(110.dp).height(48.dp),
                    contentDescription = "Weight for ${item.name}",
                )
            }
            Stepper("Rest (s)", item.restSec.toString(),
                { vm.updateItem(item.key) { it.copy(restSec = (it.restSec - 15).coerceAtLeast(0)) } },
                { vm.updateItem(item.key) { it.copy(restSec = (it.restSec + 15).coerceAtMost(600)) } })
        }
        Spacer(Modifier.height(8.dp))
        ApexTextField(item.notes, { v -> vm.updateItem(item.key) { it.copy(notes = v.take(200)) } }, "Notes")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ApexIconButton(Icons.Rounded.ContentCopy, "Duplicate ${item.name}", { vm.duplicate(item.key) })
            ApexIconButton(Icons.Rounded.Delete, "Remove ${item.name}", { vm.remove(item.key) }, tint = MaterialTheme.colorScheme.error)
        }
    }
}
