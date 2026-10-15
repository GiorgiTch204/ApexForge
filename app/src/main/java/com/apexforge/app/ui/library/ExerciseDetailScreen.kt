package com.apexforge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.ExerciseRepository
import com.apexforge.app.data.repo.StartRequest
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.domain.Alternatives
import com.apexforge.app.domain.Analytics
import com.apexforge.app.domain.Exercise
import com.apexforge.app.domain.ExerciseBests
import com.apexforge.app.domain.SafetyTips
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.ExerciseArt
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.formatWeightWithUnit
import com.apexforge.app.ui.workout.rememberWorkoutStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUi(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val alternatives: List<Exercise> = emptyList(),
    val bests: ExerciseBests? = null,
    val timesPerformed: Int = 0,
    val units: UnitSystem = UnitSystem.METRIC,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ExerciseRepository,
    stats: StatsRepository,
    prefs: PreferencesRepository,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])

    val ui: StateFlow<ExerciseDetailUi> = combine(repository.exercises, stats.snapshot, prefs.prefs) { all, snap, p: UserPrefs ->
        val ex = all.firstOrNull { it.id == id }
        val sets = snap.loggedSets.filter { it.exerciseId == id }
        ExerciseDetailUi(
            loading = false,
            exercise = ex,
            alternatives = ex?.let { Alternatives.forExercise(it, all) }.orEmpty(),
            bests = if (sets.isEmpty()) null else Analytics.bestsFor(sets),
            timesPerformed = sets.map { it.sessionId }.distinct().size,
            units = p.units,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUi())

    fun toggleFavorite() { viewModelScope.launch { repository.toggleFavorite(id) } }
}

@Composable
private fun BulletSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    lines.forEachIndexed { index, line ->
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (title == "How to do it") "${index + 1}." else "•",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenProgress: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val starter = rememberWorkoutStarter(onOpenWorkout)
    starter.Dialog()
    val ex = ui.exercise

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = ex?.name ?: "Exercise",
            onBack = onBack,
            actions = {
                if (ex != null) {
                    ApexIconButton(
                        if (ex.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (ex.isFavorite) "Remove from favorites" else "Add to favorites",
                        viewModel::toggleFavorite,
                        tint = if (ex.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
        when {
            ui.loading -> LoadingBox()
            ex == null -> EmptyState(Icons.Rounded.SearchOff, "Exercise not found", "It may have been removed.")
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                ExerciseArt(ex.category, Modifier.fillMaxWidth().height(180.dp), iconSize = 64.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "${ex.category.label} · ${ex.equipment.label} · ${ex.difficulty.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text("Primary: ${ex.primaryMuscle}", style = MaterialTheme.typography.bodyLarge)
                if (ex.secondaryMuscles.isNotEmpty()) {
                    Text(
                        "Also works: ${ex.secondaryMuscles.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))
                ApexCard(Modifier.fillMaxWidth()) {
                    val bests = ui.bests
                    if (bests == null) {
                        Text("Your records", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "You haven't logged this exercise yet. Complete a set in a workout to see your records.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Your records", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (bests.maxWeightKg > 0.0) {
                            Text("Heaviest: ${formatWeightWithUnit(bests.maxWeightKg, ui.units)}")
                            Text("Estimated 1RM: ${formatWeightWithUnit(bests.bestE1rmKg, ui.units)}")
                        } else {
                            Text("Most reps in a set: ${bests.maxBodyweightReps}")
                        }
                        Text(
                            "Performed in ${ui.timesPerformed} workout${if (ui.timesPerformed == 1) "" else "s"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        ApexButton("View progress", { onOpenProgress(ex.id) }, style = ButtonStyle.Secondary, icon = Icons.Rounded.BarChart, compact = true)
                    }
                }

                BulletSection("How to do it", ex.steps)
                BulletSection("Form cues", ex.cues)
                BulletSection("Common mistakes", ex.mistakes)
                BulletSection("Safety", ex.safety + SafetyTips.forExercise(ex.category, ex.equipment, ex.difficulty))

                if (ui.alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Alternatives")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.alternatives.forEach { alt -> ExerciseRow(alt, onClick = { onOpenExercise(alt.id) }) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (ex != null) {
            Box(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
                ApexButton(
                    "Start workout with this exercise",
                    { starter.start(StartRequest.WithExercise(ex.id)) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PlayArrow,
                )
            }
        }
    }
}
