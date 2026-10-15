package com.apexforge.app.ui.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.local.WaterEntryEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.WaterRepository
import com.apexforge.app.domain.WaterCalc
import com.apexforge.app.domain.WaterUnit
import com.apexforge.app.domain.fromMl
import com.apexforge.app.domain.toMl
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.BarChart
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.ChartBar
import com.apexforge.app.ui.components.NumberInput
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.components.SegmentedChoice
import com.apexforge.app.ui.components.Stepper
import com.apexforge.app.ui.components.WaterGlass
import com.apexforge.app.ui.formatWater
import com.apexforge.app.ui.theme.LocalHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class WaterUi(
    val loading: Boolean = true,
    val prefs: UserPrefs = UserPrefs(),
    val today: LocalDate = LocalDate.now(),
    val entries: List<WaterEntryEntity> = emptyList(),
    val week: List<Pair<LocalDate, Int>> = emptyList(),
) {
    val totalMl: Int get() = WaterCalc.total(entries.map { it.amountMl })
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WaterViewModel @Inject constructor(
    private val water: WaterRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    // Re-evaluated every 30 s so the screen rolls over at midnight without being reopened.
    private val today = flow {
        while (true) { emit(LocalDate.now()); delay(30_000) }
    }.distinctUntilChanged()

    val ui: StateFlow<WaterUi> = today.flatMapLatest { day ->
        combine(
            water.observeDay(day),
            water.observeTotalsSince(day.minusDays(6)),
            prefs.prefs,
        ) { entries, totals, p ->
            val byDay = totals.associate { it.date to it.totalMl }
            WaterUi(false, p, day, entries.sortedByDescending { it.timestamp },
                (6 downTo 0).map { day.minusDays(it.toLong()) }.map { it to (byDay[it.toString()] ?: 0) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUi())

    fun add(ml: Int) { if (ml > 0) viewModelScope.launch { water.add(ml) } }
    fun update(id: Long, ml: Int) { if (ml > 0) viewModelScope.launch { water.updateAmount(id, ml) } }
    suspend fun delete(id: Long): WaterEntryEntity? = water.getById(id)?.also { water.delete(id) }
    fun restore(entry: WaterEntryEntity) { viewModelScope.launch { water.restore(entry) } }
    fun setGoal(ml: Int) { viewModelScope.launch { prefs.update { it.copy(waterGoalMl = ml.coerceIn(500, 8000)) } } }
    fun setUnit(unit: WaterUnit) { viewModelScope.launch { prefs.update { it.copy(waterUnit = unit) } } }
}

@Composable
fun WaterScreen(onBack: () -> Unit, viewModel: WaterViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val unit = ui.prefs.waterUnit
    val goal = ui.prefs.waterGoalMl
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current

    var customDialog by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WaterEntryEntity?>(null) }

    if (customDialog) AmountDialog("Add water", unit, null, { customDialog = false }) { viewModel.add(it); customDialog = false }
    editEntry?.let { e -> AmountDialog("Edit amount", unit, e.amountMl, { editEntry = null }) { viewModel.update(e.id, it); editEntry = null } }
    if (goalDialog) {
        var draft by remember { mutableStateOf(goal) }
        AlertDialog(
            onDismissRequest = { goalDialog = false },
            title = { Text("Daily goal") },
            text = {
                Column {
                    Stepper("Goal (${unit.short})", formatWater(draft, unit),
                        { draft = (draft - 250).coerceAtLeast(500) }, { draft = (draft + 250).coerceAtMost(8000) })
                    Spacer(Modifier.height(12.dp))
                    SegmentedChoice(WaterUnit.entries, unit, { it.short }, viewModel::setUnit)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setGoal(draft); goalDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { goalDialog = false }) { Text("Cancel") } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("Water", onBack = onBack, actions = {
                ApexIconButton(Icons.Rounded.Flag, "Edit daily goal", { goalDialog = true })
            })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WaterGlass(WaterCalc.progress(ui.totalMl, goal), Modifier.size(220.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatWater(ui.totalMl, unit), style = MaterialTheme.typography.displaySmall)
                            Text("of ${formatWater(goal, unit)} ${unit.short}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (ui.totalMl >= goal) "Goal reached. Nice work."
                    else "${formatWater(WaterCalc.remaining(ui.totalMl, goal), unit)} ${unit.short} to go (${WaterCalc.percent(ui.totalMl, goal)}%)",
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150, 250, 350, 500).forEach { ml ->
                        ApexChip("+${formatWater(ml, unit)}", false, { haptics.confirm(); viewModel.add(ml) }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                ApexButton("Custom amount", { customDialog = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = Icons.Rounded.Add)

                Spacer(Modifier.height(24.dp))
                SectionHeader("Today")
                if (ui.entries.isEmpty()) {
                    Text("Nothing logged yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.entries.forEach { e ->
                            ApexCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${formatWater(e.amountMl, unit)} ${unit.short}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    ApexIconButton(Icons.Rounded.Edit, "Edit entry", { editEntry = e })
                                    ApexIconButton(Icons.Rounded.Delete, "Delete entry", {
                                        scope.launch {
                                            val removed = viewModel.delete(e.id) ?: return@launch
                                            snackbar.currentSnackbarData?.dismiss()
                                            val r = snackbar.showSnackbar("Entry removed", "Undo", duration = SnackbarDuration.Short)
                                            if (r == SnackbarResult.ActionPerformed) viewModel.restore(removed)
                                        }
                                    }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Last 7 days")
                ApexCard(Modifier.fillMaxWidth()) {
                    BarChart(
                        bars = ui.week.map { (d, ml) ->
                            ChartBar(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3), unit.fromMl(ml), highlight = d == ui.today)
                        },
                        valueFormat = { String.format(Locale.US, "%,.0f ${unit.short}", it) },
                    )
                }
                Spacer(Modifier.height(32.dp).navigationBarsPadding())
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
private fun AmountDialog(title: String, unit: WaterUnit, initialMl: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var value by remember { mutableStateOf(initialMl?.let { Math.round(unit.fromMl(it) * 10) / 10.0 } ?: 0.0) }
    val ml = unit.toMl(value)
    val valid = ml in 1..WaterRepository.MAX_ENTRY_ML
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Amount (${unit.short})", style = MaterialTheme.typography.labelMedium)
                NumberInput(value, { value = it }, Modifier.fillMaxWidth().height(56.dp), decimal = unit == WaterUnit.FL_OZ, contentDescription = "Amount")
                if (value > 0 && !valid) {
                    Text("Enter up to ${formatWater(WaterRepository.MAX_ENTRY_ML, unit)} ${unit.short}.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(ml) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
