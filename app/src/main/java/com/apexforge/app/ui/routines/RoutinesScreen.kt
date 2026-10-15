package com.apexforge.app.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.repo.RoutineRepository
import com.apexforge.app.data.repo.RoutineWithItems
import com.apexforge.app.data.repo.StartRequest
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.workout.rememberWorkoutStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Bit for a weekday: Monday = 1 ... Sunday = 64. */
fun dayBit(index: Int): Int = 1 shl index

fun scheduleText(mask: Int): String =
    if (mask == 0) "Not scheduled" else DAY_LABELS.filterIndexed { i, _ -> mask and dayBit(i) != 0 }.joinToString(" · ")

data class RoutinesUi(val loading: Boolean = true, val routines: List<RoutineWithItems> = emptyList())

@HiltViewModel
class RoutinesViewModel @Inject constructor(private val repository: RoutineRepository) : ViewModel() {
    val ui: StateFlow<RoutinesUi> = repository.routines
        .map { RoutinesUi(false, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUi())

    fun duplicate(id: String) { viewModelScope.launch { repository.duplicate(id) } }
    fun delete(id: String) { viewModelScope.launch { repository.delete(id) } }
}

@Composable
fun RoutinesScreen(
    onOpenWorkout: (String) -> Unit,
    onEditRoutine: (String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val starter = rememberWorkoutStarter(onOpenWorkout)
    starter.Dialog()
    var toDelete by remember { mutableStateOf<RoutineWithItems?>(null) }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("\"${target.routine.name}\" will be removed. Your workout history is not affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target.routine.id); toDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            "Workouts",
            actions = { ApexIconButton(Icons.Rounded.History, "Workout history", onOpenHistory) },
        )
        when {
            ui.loading -> LoadingBox()
            else -> {
                val mine = ui.routines.filter { it.routine.templateKey == null }
                val templates = ui.routines.filter { it.routine.templateKey != null }
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ApexButton(
                                "Empty workout", { starter.start(StartRequest.Empty) },
                                Modifier.weight(1f), icon = Icons.Rounded.PlayArrow,
                            )
                            ApexButton(
                                "New routine", { onEditRoutine("new") },
                                Modifier.weight(1f), style = ButtonStyle.Secondary, icon = Icons.Rounded.Add,
                            )
                        }
                    }
                    item { SectionHeader("My routines") }
                    if (mine.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Rounded.FitnessCenter, "No routines yet",
                                "Create your own, or duplicate and edit a template below.",
                            )
                        }
                    } else {
                        items(mine, key = { it.routine.id }) { r ->
                            RoutineCard(r, { starter.start(StartRequest.FromRoutine(r.routine.id)) }, { onEditRoutine(r.routine.id) },
                                { viewModel.duplicate(r.routine.id) }, { toDelete = r })
                        }
                    }
                    if (templates.isNotEmpty()) {
                        item { SectionHeader("Templates") }
                        items(templates, key = { it.routine.id }) { r ->
                            RoutineCard(r, { starter.start(StartRequest.FromRoutine(r.routine.id)) }, { onEditRoutine(r.routine.id) },
                                { viewModel.duplicate(r.routine.id) }, { toDelete = r })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineWithItems,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ApexCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(routine.routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${routine.items.size} exercises · ~${routine.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (routine.routine.scheduledDays != 0) {
                    Text(scheduleText(routine.routine.scheduledDays), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                ApexIconButton(Icons.Rounded.MoreVert, "More options for ${routine.routine.name}", { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        val names = routine.items.mapNotNull { it.exercise?.name }
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                names.take(4).joinToString(" · ") + if (names.size > 4) " +${names.size - 4}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        ApexButton("Start", onStart, icon = Icons.Rounded.PlayArrow, compact = true, enabled = routine.items.isNotEmpty())
    }
}
