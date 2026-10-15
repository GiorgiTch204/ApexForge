package com.apexforge.app.ui.progress

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
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.domain.Analytics
import com.apexforge.app.domain.ChartPoint
import com.apexforge.app.domain.ExerciseBests
import com.apexforge.app.domain.ExercisePoint
import com.apexforge.app.domain.PRHit
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.displayWeight
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LineChart
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.components.StatTile
import com.apexforge.app.ui.formatDate
import com.apexforge.app.ui.formatVolume
import com.apexforge.app.ui.formatWeight
import com.apexforge.app.ui.formatWeightWithUnit
import com.apexforge.app.ui.shortDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ExerciseProgressUi(
    val loading: Boolean = true,
    val name: String = "",
    val units: UnitSystem = UnitSystem.METRIC,
    val points: List<ExercisePoint> = emptyList(),
    val bests: ExerciseBests? = null,
    val prs: List<PRHit> = emptyList(),
)

@HiltViewModel
class ExerciseProgressViewModel @Inject constructor(
    savedState: SavedStateHandle,
    stats: StatsRepository,
    prefs: PreferencesRepository,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])

    val ui: StateFlow<ExerciseProgressUi> = combine(stats.snapshot, prefs.prefs) { snap, p ->
        val sets = snap.loggedSets.filter { it.exerciseId == id }
        ExerciseProgressUi(
            loading = false,
            name = snap.exerciseName(id),
            units = p.units,
            points = Analytics.exercisePoints(sets),
            bests = if (sets.isEmpty()) null else Analytics.bestsFor(sets),
            prs = snap.prHits.filter { it.exerciseId == id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressUi())
}

@Composable
fun ExerciseProgressScreen(onBack: () -> Unit, viewModel: ExerciseProgressViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    fun label(t: Long) = shortDay(Instant.ofEpochMilli(t).atZone(zone).toLocalDate())

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(ui.name.ifBlank { "Progress" }, subtitle = "Progress", onBack = onBack)
        when {
            ui.loading -> LoadingBox()
            ui.points.isEmpty() -> EmptyState(
                Icons.Rounded.ShowChart, "No data yet",
                "Complete sets of this exercise in a workout and your trend will appear here.",
            )
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                val bests = ui.bests
                val weighted = (bests?.maxWeightKg ?: 0.0) > 0.0
                if (bests != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (weighted) {
                            StatTile("Heaviest", formatWeight(bests.maxWeightKg, ui.units), Modifier.weight(1f), unit = ui.units.weightUnit)
                            StatTile("Est. 1RM", formatWeight(bests.bestE1rmKg, ui.units), Modifier.weight(1f), unit = ui.units.weightUnit)
                        } else {
                            StatTile("Most reps", bests.maxBodyweightReps.toString(), Modifier.weight(1f))
                            StatTile("Workouts", ui.points.size.toString(), Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (weighted) {
                    SectionHeader("Estimated 1RM")
                    ApexCard(Modifier.fillMaxWidth()) {
                        LineChart(
                            points = ui.points.map { ChartPoint(label(it.time), ui.units.displayWeight(it.bestE1rmKg)) },
                            valueFormat = { String.format(java.util.Locale.US, "%.1f ${ui.units.weightUnit}", it) },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Top set weight")
                    ApexCard(Modifier.fillMaxWidth()) {
                        LineChart(
                            points = ui.points.map { ChartPoint(label(it.time), ui.units.displayWeight(it.topWeightKg)) },
                            valueFormat = { String.format(java.util.Locale.US, "%.1f ${ui.units.weightUnit}", it) },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Volume per workout")
                    ApexCard(Modifier.fillMaxWidth()) {
                        LineChart(
                            points = ui.points.map { ChartPoint(label(it.time), ui.units.displayWeight(it.volumeKg)) },
                            valueFormat = { String.format(java.util.Locale.US, "%,.0f ${ui.units.weightUnit}", it) },
                        )
                    }
                } else {
                    SectionHeader("Total reps per workout")
                    ApexCard(Modifier.fillMaxWidth()) {
                        LineChart(points = ui.points.map { ChartPoint(label(it.time), it.totalReps.toDouble()) })
                    }
                }
                Spacer(Modifier.height(20.dp))
                SectionHeader("Personal records")
                if (ui.prs.isEmpty()) {
                    Text(
                        "No records beaten yet. Your first logged workout sets the baseline.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.prs.take(15).forEach { hit ->
                            ApexCard(Modifier.fillMaxWidth()) {
                                Text(hit.type.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "${formatWeightWithUnit(hit.weightKg, ui.units)} × ${hit.reps}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(formatDate(hit.achievedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
