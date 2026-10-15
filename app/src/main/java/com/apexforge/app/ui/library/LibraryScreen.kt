package com.apexforge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.repo.ExerciseRepository
import com.apexforge.app.domain.Category
import com.apexforge.app.domain.Equipment
import com.apexforge.app.domain.Exercise
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.ExerciseArt
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryFilters(
    val query: String = "",
    val category: Category? = null,
    val equipment: Equipment? = null,
    val favoritesOnly: Boolean = false,
)

/** Pure filter used by the library and the picker so both behave identically. */
fun filterExercises(all: List<Exercise>, f: LibraryFilters): List<Exercise> {
    val q = f.query.trim().lowercase()
    return all.asSequence()
        .filter { f.category == null || it.category == f.category }
        .filter { f.equipment == null || it.equipment == f.equipment }
        .filter { !f.favoritesOnly || it.isFavorite }
        .filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.primaryMuscle.lowercase().contains(q) ||
                it.secondaryMuscles.any { m -> m.lowercase().contains(q) }
        }
        .sortedBy { it.name }
        .toList()
}

data class LibraryUi(val loading: Boolean = true, val items: List<Exercise> = emptyList(), val total: Int = 0)

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repository: ExerciseRepository) : ViewModel() {
    private val _filters = MutableStateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters

    val ui: StateFlow<LibraryUi> = combine(repository.exercises, _filters) { all, f ->
        LibraryUi(false, filterExercises(all, f), all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    fun update(transform: (LibraryFilters) -> LibraryFilters) = _filters.update(transform)
    fun toggleFavorite(id: String) { viewModelScope.launch { repository.toggleFavorite(id) } }
}

@Composable
fun FilterChips(filters: LibraryFilters, onChange: ((LibraryFilters) -> LibraryFilters) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ApexChip("Favorites", filters.favoritesOnly, { onChange { it.copy(favoritesOnly = !it.favoritesOnly) } },
                leadingIcon = Icons.Rounded.Favorite)
        }
        item { ApexChip("All", filters.category == null, { onChange { it.copy(category = null) } }) }
        items(Category.entries) { c ->
            ApexChip(c.label, filters.category == c, { onChange { it.copy(category = if (it.category == c) null else c) } })
        }
    }
    Spacer(Modifier.size(8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ApexChip("Any equipment", filters.equipment == null, { onChange { it.copy(equipment = null) } }) }
        items(Equipment.entries) { e ->
            ApexChip(e.label, filters.equipment == e, { onChange { it.copy(equipment = if (it.equipment == e) null else e) } })
        }
    }
}

@Composable
fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    ApexCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseArt(exercise.category, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    "${exercise.primaryMuscle} · ${exercise.equipment.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    exercise.difficulty.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            trailing()
        }
    }
}

@Composable
fun LibraryScreen(onOpenExercise: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Exercises", subtitle = if (ui.loading) null else "${ui.total} exercises")
        ApexTextField(
            value = filters.query,
            onValueChange = { v -> viewModel.update { it.copy(query = v.take(60)) } },
            label = "Search name or muscle",
            leadingIcon = Icons.Rounded.Search,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.size(10.dp))
        FilterChips(filters, viewModel::update)
        Spacer(Modifier.size(8.dp))
        when {
            ui.loading -> LoadingBox()
            ui.items.isEmpty() -> EmptyState(
                Icons.Rounded.SearchOff, "No exercises found",
                "Try a different search or clear a filter.",
                actionLabel = "Clear filters",
                onAction = { viewModel.update { LibraryFilters() } },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(ui.items, key = { it.id }) { ex ->
                    ExerciseRow(ex, onClick = { onOpenExercise(ex.id) }) {
                        ApexIconButton(
                            if (ex.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (ex.isFavorite) "Remove ${ex.name} from favorites" else "Add ${ex.name} to favorites",
                            { viewModel.toggleFavorite(ex.id) },
                            tint = if (ex.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
