package com.apexforge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.repo.ExerciseRepository
import com.apexforge.app.domain.Exercise
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PickerViewModel @Inject constructor(repository: ExerciseRepository) : ViewModel() {
    private val _filters = MutableStateFlow(LibraryFilters())
    val filters: StateFlow<LibraryFilters> = _filters
    val items: StateFlow<List<Exercise>> = combine(repository.exercises, _filters) { all, f -> filterExercises(all, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(transform: (LibraryFilters) -> LibraryFilters) = _filters.update(transform)
}

/** Multi-select exercise chooser shown as a bottom sheet. [onConfirm] receives ids in tap order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val vm: PickerViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(listOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Text("Add exercises", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.size(10.dp))
            ApexTextField(
                value = filters.query,
                onValueChange = { v -> vm.update { it.copy(query = v.take(60)) } },
                label = "Search",
                leadingIcon = Icons.Rounded.Search,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.size(10.dp))
            FilterChips(filters, vm::update)
            Spacer(Modifier.size(8.dp))
            if (items.isEmpty()) {
                EmptyState(Icons.Rounded.Search, "Nothing matches", "Clear a filter or change the search.", Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { ex ->
                        val isSelected = ex.id in selected
                        ExerciseRow(ex, onClick = {
                            selected = if (isSelected) selected - ex.id else selected + ex.id
                        }) {
                            if (isSelected) Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            ApexButton(
                text = if (selected.isEmpty()) "Select exercises" else "Add ${selected.size}",
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
        }
    }
}
