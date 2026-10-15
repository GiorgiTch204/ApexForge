package com.apexforge.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.domain.SessionSummary
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.formatDateTime
import com.apexforge.app.ui.formatMinutes
import com.apexforge.app.ui.formatVolume
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class HistoryUi(
    val loading: Boolean = true,
    val sessions: List<SessionSummary> = emptyList(),
    val days: Set<LocalDate> = emptySet(),
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val query: String = "",
    val units: UnitSystem = UnitSystem.METRIC,
    val totalCount: Int = 0,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(stats: StatsRepository, prefs: PreferencesRepository) : ViewModel() {
    private data class Local(val month: YearMonth, val day: LocalDate?, val query: String)

    private val local = MutableStateFlow(Local(YearMonth.now(), null, ""))
    private val zone = ZoneId.systemDefault()

    val ui: StateFlow<HistoryUi> = combine(stats.snapshot, prefs.prefs, local) { snap, p, l ->
        val q = l.query.trim().lowercase()
        val filtered = snap.sessions
            .sortedByDescending { it.endedAt }
            .filter { l.day == null || it.day(zone) == l.day }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.exerciseNames.any { n -> n.lowercase().contains(q) } }
        HistoryUi(false, filtered, snap.workoutDays, l.month, l.day, l.query, p.units, snap.sessions.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun shiftMonth(delta: Long) = local.update { it.copy(month = it.month.plusMonths(delta)) }
    fun selectDay(day: LocalDate) = local.update { it.copy(day = if (it.day == day) null else day) }
    fun clearDay() = local.update { it.copy(day = null) }
    fun setQuery(q: String) = local.update { it.copy(query = q.take(60)) }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("History", subtitle = "${ui.totalCount} workout${if (ui.totalCount == 1) "" else "s"}", onBack = onBack)
        when {
            ui.loading -> LoadingBox()
            ui.totalCount == 0 -> EmptyState(Icons.Rounded.History, "No workouts yet", "Finished workouts appear here with their sets and records.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { CalendarCard(ui, viewModel) }
                item {
                    ApexTextField(
                        ui.query, viewModel::setQuery, "Search workouts or exercises",
                        leadingIcon = Icons.Rounded.Search,
                    )
                }
                ui.selectedDay?.let { day ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ApexChip(com.apexforge.app.ui.formatDay(day) + "  ✕", true, viewModel::clearDay)
                        }
                    }
                }
                if (ui.sessions.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, "No matches", "Try another search or clear the selected day.") }
                } else {
                    items(ui.sessions, key = { it.id }) { s -> SessionCard(s, ui.units) { onOpenSession(s.id) } }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(ui: HistoryUi, vm: HistoryViewModel) {
    val month = ui.month
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // Monday-first
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ApexIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", { vm.shiftMonth(-1) })
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            )
            ApexIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", { vm.shiftMonth(1) }, enabled = month < YearMonth.now())
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNum)
                        val has = date in ui.days
                        val selected = date == ui.selectedDay
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                .background(
                                    when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        has -> MaterialTheme.colorScheme.primaryContainer
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .clickable(enabled = has) { vm.selectDay(date) }
                                .semantics { contentDescription = if (has) "$date, workout logged" else "$date" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dayNum.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    has -> MaterialTheme.colorScheme.primary
                                    date == today -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCard(s: SessionSummary, units: UnitSystem, onClick: () -> Unit) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (s.prCount > 0) {
                Icon(Icons.Rounded.EmojiEvents, "${s.prCount} personal records", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(" ${s.prCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(formatDateTime(s.endedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            "${formatMinutes(s.durationSec)} · ${s.sets} sets · ${formatVolume(s.volumeKg, units)} ${units.weightUnit}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (s.exerciseNames.isNotEmpty()) {
            Text(
                s.exerciseNames.take(3).joinToString(" · ") + if (s.exerciseNames.size > 3) " +${s.exerciseNames.size - 3}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}
