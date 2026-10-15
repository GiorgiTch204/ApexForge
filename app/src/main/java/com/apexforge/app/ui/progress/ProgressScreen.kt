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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ShowChart
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.BodyWeightRepository
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.domain.Analytics
import com.apexforge.app.domain.ChartPoint
import com.apexforge.app.domain.PRHit
import com.apexforge.app.domain.PeriodStats
import com.apexforge.app.domain.RangeOption
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.displayWeight
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.toKg
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.BarChart
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.ChartBar
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LineChart
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.NumberInput
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.components.StatTile
import com.apexforge.app.ui.formatDate
import com.apexforge.app.ui.formatMinutes
import com.apexforge.app.ui.formatVolume
import com.apexforge.app.ui.formatWeight
import com.apexforge.app.ui.shortDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

data class ExerciseSummaryRow(val id: String, val name: String, val sets: Int, val topKg: Double)

data class ProgressUi(
    val loading: Boolean = true,
    val range: RangeOption = RangeOption.M3,
    val units: UnitSystem = UnitSystem.METRIC,
    val totalWorkouts: Int = 0,
    val current: PeriodStats = PeriodStats(0, 0, 0.0, 0.0),
    val previous: PeriodStats? = null,
    val weekly: List<com.apexforge.app.domain.WeekBucket> = emptyList(),
    val bodyWeight: List<ChartPoint> = emptyList(),
    val latestBodyKg: Double? = null,
    val exercises: List<ExerciseSummaryRow> = emptyList(),
    val prs: List<PRHit> = emptyList(),
    val exerciseNames: Map<String, String> = emptyMap(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    stats: StatsRepository,
    prefs: PreferencesRepository,
    private val bodyWeights: BodyWeightRepository,
) : ViewModel() {
    private val range = MutableStateFlow(RangeOption.M3)
    private val zone = ZoneId.systemDefault()

    val ui: StateFlow<ProgressUi> = combine(stats.snapshot, prefs.prefs, range, bodyWeights.entries) { snap, p, r, body ->
        val today = LocalDate.now()
        val earliest = snap.sessions.minOfOrNull { it.day(zone) }
        val from = Analytics.rangeStart(r, today, earliest)
        val length = Analytics.daysBetween(from, today)
        val prevFrom = from.minusDays(length)
        val prevTo = from.minusDays(1)
        val current = Analytics.periodStats(snap.sessions, from, today, zone)
        // "All" has no earlier period to compare with.
        val previous = if (r == RangeOption.ALL) null else Analytics.periodStats(snap.sessions, prevFrom, prevTo, zone)
        val inRange = snap.loggedSets.filter {
            val d = java.time.Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate()
            !d.isBefore(from)
        }
        val exercises = inRange.groupBy { it.exerciseId }.map { (id, sets) ->
            ExerciseSummaryRow(id, snap.exerciseName(id), sets.size, sets.maxOf { it.weightKg })
        }.sortedByDescending { it.sets }
        val bodyPoints = body.filter { runCatching { !LocalDate.parse(it.date).isBefore(from) }.getOrDefault(true) }
            .sortedBy { it.timestamp }
            .map { ChartPoint(shortDay(LocalDate.parse(it.date)), p.units.displayWeight(it.weightKg)) }
        ProgressUi(
            loading = false, range = r, units = p.units, totalWorkouts = snap.totalWorkouts,
            current = current, previous = previous,
            weekly = Analytics.weeklyBuckets(snap.sessions, from, today, zone),
            bodyWeight = bodyPoints,
            latestBodyKg = body.maxByOrNull { it.timestamp }?.weightKg,
            exercises = exercises,
            prs = snap.prHits.take(5),
            exerciseNames = snap.exerciseNames,
            currentStreak = snap.currentStreak, longestStreak = snap.longestStreak,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())

    fun setRange(r: RangeOption) { range.value = r }
    fun addBodyWeight(kg: Double) { if (kg in 20.0..500.0) viewModelScope.launch { bodyWeights.add(kg) } }
}

@Composable
private fun ChangeLabel(current: Double, previous: Double?) {
    val change = previous?.let { Analytics.percentChange(current, it) }
    val text = when {
        previous == null -> return
        change == null -> "No earlier data to compare"
        abs(change) < 0.5 -> "Same as the previous period"
        change > 0 -> "▲ ${change.roundToInt()}% vs previous period"
        else -> "▼ ${abs(change).roundToInt()}% vs previous period"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ProgressScreen(
    onOpenExerciseProgress: (String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var weightDialog by remember { mutableStateOf(false) }

    if (weightDialog) {
        var value by remember { mutableStateOf(ui.latestBodyKg?.let { ui.units.displayWeight(it) } ?: 0.0) }
        val kg = ui.units.toKg(value)
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            title = { Text("Log body weight") },
            text = {
                Column {
                    Text("Weight (${ui.units.weightUnit})", style = MaterialTheme.typography.labelMedium)
                    NumberInput(value, { value = it }, Modifier.fillMaxWidth().height(56.dp), decimal = true, contentDescription = "Body weight")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addBodyWeight(kg); weightDialog = false }, enabled = kg in 20.0..500.0) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Progress", actions = { ApexIconButton(Icons.Rounded.History, "Workout history", onOpenHistory) })
        when {
            ui.loading -> LoadingBox()
            ui.totalWorkouts == 0 -> EmptyState(
                Icons.Rounded.ShowChart, "Your progress starts here",
                "Finish a workout and your volume, records and trends will show up on this screen.",
            )
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeOption.entries.forEach { r -> ApexChip(r.label, ui.range == r, { viewModel.setRange(r) }) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        StatTile("Workouts", ui.current.workouts.toString(), Modifier.fillMaxWidth())
                        ChangeLabel(ui.current.workouts.toDouble(), ui.previous?.workouts?.toDouble())
                    }
                    Column(Modifier.weight(1f)) {
                        StatTile("Volume", formatVolume(ui.current.volumeKg, ui.units), Modifier.fillMaxWidth(), unit = ui.units.weightUnit)
                        ChangeLabel(ui.current.volumeKg, ui.previous?.volumeKg)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Sets", ui.current.sets.toString(), Modifier.weight(1f))
                    StatTile("Avg duration", if (ui.current.workouts == 0) "-" else formatMinutes((ui.current.avgDurationMin * 60).toLong()), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Current streak", "${ui.currentStreak}", Modifier.weight(1f), unit = "days")
                    StatTile("Longest streak", "${ui.longestStreak}", Modifier.weight(1f), unit = "days")
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Workouts per week")
                ApexCard(Modifier.fillMaxWidth()) {
                    BarChart(
                        bars = ui.weekly.map { ChartBar(shortDay(it.weekStart), it.workouts.toDouble()) },
                        labelEvery = (ui.weekly.size / 6).coerceAtLeast(1),
                    )
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("Weekly volume")
                ApexCard(Modifier.fillMaxWidth()) {
                    LineChart(
                        points = ui.weekly.map { ChartPoint(shortDay(it.weekStart), ui.units.displayWeight(it.volumeKg)) },
                        valueFormat = { String.format(java.util.Locale.US, "%,.0f ${ui.units.weightUnit}", it) },
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Body weight", actionLabel = "Log", onAction = { weightDialog = true })
                ApexCard(Modifier.fillMaxWidth()) {
                    if (ui.bodyWeight.isEmpty()) {
                        Text("No entries in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LineChart(
                            points = ui.bodyWeight,
                            valueFormat = { String.format(java.util.Locale.US, "%.1f ${ui.units.weightUnit}", it) },
                            emptyText = "Log another weigh-in to see a trend.",
                        )
                    }
                }

                if (ui.prs.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Recent records")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.prs.forEach { hit ->
                            ApexCard(Modifier.fillMaxWidth(), onClick = { onOpenExerciseProgress(hit.exerciseId) }) {
                                Text(ui.exerciseNames[hit.exerciseId] ?: "Exercise", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${hit.type.label}: ${formatWeight(hit.weightKg, ui.units)} ${ui.units.weightUnit} × ${hit.reps} · ${formatDate(hit.achievedAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Exercises")
                if (ui.exercises.isEmpty()) {
                    Text("No exercises logged in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.exercises.forEach { row ->
                            ApexCard(Modifier.fillMaxWidth(), onClick = { onOpenExerciseProgress(row.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    Text("${row.sets} sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
