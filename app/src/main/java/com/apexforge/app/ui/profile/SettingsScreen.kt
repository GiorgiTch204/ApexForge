package com.apexforge.app.ui.profile

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.DataManager
import com.apexforge.app.data.repo.ExportManager
import com.apexforge.app.data.seed.TemplateSeeder
import com.apexforge.app.domain.ThemeMode
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.WaterUnit
import com.apexforge.app.notifications.ReminderScheduler
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.components.SegmentedChoice
import com.apexforge.app.ui.components.Stepper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val scheduler: ReminderScheduler,
    private val exporter: ExportManager,
    private val data: DataManager,
    private val templates: TemplateSeeder,
) : ViewModel() {
    val ui: StateFlow<UserPrefs?> = prefs.prefs.map<UserPrefs, UserPrefs?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(resyncReminders: Boolean = false, transform: (UserPrefs) -> UserPrefs) {
        viewModelScope.launch {
            prefs.update(transform)
            if (resyncReminders) scheduler.sync(prefs.prefs.first(), replace = true)
        }
    }

    suspend fun export(units: UnitSystem) = exporter.exportHistoryCsv(units)
    suspend fun restoreTemplates(): Int = templates.restoreMissing()

    fun deleteEverything(onDone: () -> Unit) {
        viewModelScope.launch {
            scheduler.cancelAll()
            data.deleteEverything()
            onDone()
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAccount: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val p by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingReminder by remember { mutableStateOf<((UserPrefs) -> UserPrefs)?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val enable = pendingReminder
        pendingReminder = null
        if (granted && enable != null) viewModel.update(true, enable)
        else message = "Notifications are blocked, so reminders can't be shown. You can allow them in system settings."
    }

    /** Turning a reminder on asks for the notification permission first; turning it off never does. */
    fun toggleReminder(on: Boolean, change: (UserPrefs, Boolean) -> UserPrefs) {
        val apply: (UserPrefs) -> UserPrefs = { change(it, on) }
        if (on && needsNotificationPermission(context)) {
            pendingReminder = apply
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.update(true, apply)
    }

    message?.let {
        AlertDialog(onDismissRequest = { message = null }, title = { Text("ApexForge") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently erases your workouts, routines, water log, body weights and profile from this device, then restarts onboarding. Export your history first if you want a copy.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteEverything {} }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Settings", onBack = onBack)
        val prefs = p
        if (prefs == null) { LoadingBox(); return@Column }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionHeader("Account")
            ApexButton("Account & cloud sync", onOpenAccount, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            Text("Optional. Sign in to back up and sync your training across phones.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(16.dp))
            SectionHeader("Appearance")
            SegmentedChoice(ThemeMode.entries, prefs.themeMode, { it.label }, { m -> viewModel.update { it.copy(themeMode = m) } })
            SwitchRow("Haptic feedback", prefs.haptics, { v -> viewModel.update { it.copy(haptics = v) } })

            Spacer(Modifier.height(16.dp))
            SectionHeader("Units")
            SegmentedChoice(UnitSystem.entries, prefs.units, { it.label }, { u ->
                viewModel.update { it.copy(units = u, waterUnit = if (u == UnitSystem.IMPERIAL) WaterUnit.FL_OZ else WaterUnit.ML) }
            })
            Spacer(Modifier.height(8.dp))
            SegmentedChoice(WaterUnit.entries, prefs.waterUnit, { it.label }, { w -> viewModel.update { it.copy(waterUnit = w) } })
            Text("Your data is stored in kg and ml, so switching units never changes what you logged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(16.dp))
            SectionHeader("Workout")
            SwitchRow("Auto-start rest timer", prefs.autoRestTimer, { v -> viewModel.update { it.copy(autoRestTimer = v) } },
                "Starts after you complete a set")
            Stepper("Default rest (seconds)", prefs.defaultRestSec.toString(),
                { viewModel.update { it.copy(defaultRestSec = (it.defaultRestSec - 15).coerceAtLeast(15)) } },
                { viewModel.update { it.copy(defaultRestSec = (it.defaultRestSec + 15).coerceAtMost(600)) } })

            Spacer(Modifier.height(16.dp))
            SectionHeader("Reminders")
            SwitchRow("Workout reminder", prefs.workoutReminder, { on -> toggleReminder(on) { s, v -> s.copy(workoutReminder = v) } })
            if (prefs.workoutReminder) {
                val minutes = prefs.workoutReminderMinutes
                ApexButton(
                    "Time: %02d:%02d".format(minutes / 60, minutes % 60),
                    {
                        TimePickerDialog(context, { _, h, m -> viewModel.update(true) { it.copy(workoutReminderMinutes = h * 60 + m) } },
                            minutes / 60, minutes % 60, true).show()
                    },
                    style = ButtonStyle.Secondary, compact = true,
                )
            }
            SwitchRow("Water reminder", prefs.waterReminder, { on -> toggleReminder(on) { s, v -> s.copy(waterReminder = v) } })
            if (prefs.waterReminder) {
                Stepper("Every (hours)", prefs.waterReminderHours.toString(),
                    { viewModel.update(true) { it.copy(waterReminderHours = (it.waterReminderHours - 1).coerceAtLeast(1)) } },
                    { viewModel.update(true) { it.copy(waterReminderHours = (it.waterReminderHours + 1).coerceAtMost(6)) } })
            }
            SwitchRow("Weekly summary", prefs.weeklySummary, { on -> toggleReminder(on) { s, v -> s.copy(weeklySummary = v) } })
            Text("Android may deliver reminders a few minutes late to save battery.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))
            SectionHeader("Data")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ApexButton("Export history (CSV)", {
                    scope.launch {
                        runCatching { viewModel.export(prefs.units) }
                            .onSuccess { uri ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Export workout history"))
                            }
                            .onFailure { message = "Export failed. Make sure there is free storage and try again." }
                    }
                }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                ApexButton("Restore default templates", {
                    scope.launch {
                        val n = viewModel.restoreTemplates()
                        message = if (n == 0) "All default templates are already there." else "Restored $n template${if (n == 1) "" else "s"}."
                    }
                }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                ApexButton("Delete all data", { confirmDelete = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
            }

            Spacer(Modifier.height(24.dp))
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0" }
            Text("ApexForge $version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("All data stays on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}
