package com.apexforge.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.RoutineRepository
import com.apexforge.app.data.repo.RoutineWithItems
import com.apexforge.app.data.repo.StartRequest
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.data.repo.WaterRepository
import com.apexforge.app.data.repo.WorkoutRepository
import com.apexforge.app.domain.Analytics
import com.apexforge.app.domain.Calc
import com.apexforge.app.domain.PRHit
import com.apexforge.app.domain.StatsSnapshot
import com.apexforge.app.domain.WaterCalc
import com.apexforge.app.domain.displayWeight
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.Avatar
import com.apexforge.app.ui.components.BarChart
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.ChartBar
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ProgressRing
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.components.StatTile
import com.apexforge.app.ui.formatDate
import com.apexforge.app.ui.formatVolume
import com.apexforge.app.ui.formatWater
import com.apexforge.app.ui.formatWeight
import com.apexforge.app.ui.greetingFor
import com.apexforge.app.ui.rememberToday
import com.apexforge.app.ui.shortDay
import com.apexforge.app.ui.workout.rememberWorkoutStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class HomeUi(
    val loading: Boolean = true,
    val prefs: UserPrefs = UserPrefs(),
    val today: LocalDate = LocalDate.now(),
    val snapshot: StatsSnapshot = StatsSnapshot.EMPTY,
    val todaysRoutine: RoutineWithItems? = null,
    val active: WorkoutSessionEntity? = null,
    val waterMl: Int = 0,
    val todaySets: Int = 0,
    val todayReps: Int = 0,
    val todayVolumeKg: Double = 0.0,
    val weekDays: List<Pair<LocalDate, Boolean>> = emptyList(),
    val weeklyVolume: List<Pair<LocalDate, Double>> = emptyList(),
    val recentPrs: List<PRHit> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    stats: StatsRepository,
    prefs: PreferencesRepository,
    routines: RoutineRepository,
    workouts: WorkoutRepository,
    water: WaterRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = flow { while (true) { emit(LocalDate.now()); delay(30_000) } }.distinctUntilChanged()

    val ui: StateFlow<HomeUi> = today.flatMapLatest { day ->
        combine(
            stats.snapshot, prefs.prefs, routines.routines, workouts.observeActiveSession(), water.observeDay(day),
        ) { snap, p, rs, active, entries ->
            val bit = 1 shl (day.dayOfWeek.value - 1)
            val todays = snap.loggedSets.filter {
                java.time.Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() == day
            }
            val monday = Analytics.weekStart(day)
            val buckets = Analytics.weeklyBuckets(snap.sessions, day.minusWeeks(5), day, zone)
            HomeUi(
                loading = false, prefs = p, today = day, snapshot = snap,
                todaysRoutine = rs.firstOrNull { it.routine.scheduledDays and bit != 0 && it.items.isNotEmpty() },
                active = active,
                waterMl = WaterCalc.total(entries.map { it.amountMl }),
                todaySets = todays.size,
                todayReps = todays.sumOf { it.reps },
                todayVolumeKg = todays.sumOf { Calc.setVolume(it.weightKg, it.reps) },
                weekDays = (0..6).map { monday.plusDays(it.toLong()) }.map { it to (it in snap.workoutDays) },
                weeklyVolume = buckets.map { it.weekStart to it.volumeKg },
                recentPrs = snap.prHits.take(3),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

@Composable
fun HomeScreen(
    onOpenWorkout: (String) -> Unit,
    onOpenWater: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val starter = rememberWorkoutStarter(onOpenWorkout)
    starter.Dialog()
    val clockToday by rememberToday()
    val hour = remember(clockToday) { LocalTime.now().hour }
    if (ui.loading) { LoadingBox(); return }
    val p = ui.prefs
    val snap = ui.snapshot
    val units = p.units

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greetingFor(hour), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.name.ifBlank { "Athlete" }, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
            }
            Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Open profile", onClick = onOpenProfile)) {
                Avatar(p.name, p.photoPath, 48.dp)
            }
        }

        ui.active?.let { active ->
            ApexCard(Modifier.fillMaxWidth(), onClick = { onOpenWorkout(active.id) }, containerColor = MaterialTheme.colorScheme.primaryContainer, borderColor = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Workout in progress", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(active.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Resume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Today's plan
        val planned = ui.todaysRoutine
        ApexCard(Modifier.fillMaxWidth()) {
            if (planned != null) {
                Text("Today's workout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(planned.routine.name, style = MaterialTheme.typography.headlineSmall)
                Text("${planned.items.size} exercises · ~${planned.estimatedMinutes} min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                ApexButton("Start", { starter.start(StartRequest.FromRoutine(planned.routine.id)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            } else if (snap.totalWorkouts == 0) {
                Text("Welcome to ApexForge", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Start an empty workout, or pick a ready-made template to log your first session.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ApexButton("Empty workout", { starter.start(StartRequest.Empty) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                    ApexButton("Templates", onOpenRoutines, Modifier.weight(1f), style = ButtonStyle.Secondary)
                }
            } else {
                Text("Nothing scheduled today", style = MaterialTheme.typography.titleLarge)
                Text("Schedule a routine for this weekday, or train freestyle.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ApexButton("Empty workout", { starter.start(StartRequest.Empty) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                    ApexButton("Routines", onOpenRoutines, Modifier.weight(1f), style = ButtonStyle.Secondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Sets", ui.todaySets.toString(), Modifier.weight(1f))
            StatTile("Reps", ui.todayReps.toString(), Modifier.weight(1f))
            StatTile("Volume", formatVolume(ui.todayVolumeKg, units), Modifier.weight(1f), unit = units.weightUnit)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ApexCard(Modifier.weight(1f), onClick = onOpenWater) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(WaterCalc.progress(ui.waterMl, p.waterGoalMl), Modifier.size(56.dp), strokeWidth = 6.dp) {
                        Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(formatWater(ui.waterMl, p.waterUnit), style = MaterialTheme.typography.titleMedium)
                        Text("of ${formatWater(p.waterGoalMl, p.waterUnit)} ${p.waterUnit.short}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ApexCard(Modifier.weight(1f), onClick = onOpenProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${snap.currentStreak}", style = MaterialTheme.typography.titleLarge)
                        Text(if (snap.currentStreak == 1) "day streak" else "day streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("This week", actionLabel = "History", onAction = onOpenHistory)
        ApexCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ui.weekDays.forEach { (date, done) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(
                                when {
                                    done -> MaterialTheme.colorScheme.primary
                                    date == ui.today -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                                color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Goal: ${p.frequencyPerWeek} workouts per week",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (snap.totalWorkouts > 0) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Volume, last 6 weeks", actionLabel = "Progress", onAction = onOpenProgress)
            ApexCard(Modifier.fillMaxWidth()) {
                BarChart(
                    bars = ui.weeklyVolume.map { (d, kg) -> ChartBar(shortDay(d), units.displayWeight(kg)) },
                    height = 120.dp,
                    valueFormat = { String.format(java.util.Locale.US, "%,.0f ${units.weightUnit}", it) },
                    labelEvery = 2,
                )
            }
        }

        if (ui.recentPrs.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Recent records")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.recentPrs.forEach { hit ->
                    ApexCard(Modifier.fillMaxWidth(), onClick = { onOpenExercise(hit.exerciseId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(snap.exerciseName(hit.exerciseId), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${hit.type.label}: ${formatWeight(hit.weightKg, units)} ${units.weightUnit} × ${hit.reps} · ${formatDate(hit.achievedAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ApexButton("Library", onOpenLibrary, Modifier.weight(1f), style = ButtonStyle.Secondary, icon = Icons.Rounded.MenuBook)
            ApexButton("Water", onOpenWater, Modifier.weight(1f), style = ButtonStyle.Secondary, icon = Icons.Rounded.WaterDrop)
        }
        Spacer(Modifier.height(32.dp))
    }
}
