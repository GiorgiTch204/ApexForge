package com.apexforge.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.ExerciseRepository
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.domain.Exercise
import com.apexforge.app.domain.StatsSnapshot
import com.apexforge.app.domain.cmToInches
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.fromKg
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.Avatar
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.SectionHeader
import com.apexforge.app.ui.components.StatTile
import com.apexforge.app.ui.formatDate
import com.apexforge.app.ui.formatVolume
import com.apexforge.app.ui.formatWeight
import com.apexforge.app.ui.library.ExerciseRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileUi(
    val loading: Boolean = true,
    val prefs: UserPrefs = UserPrefs(),
    val stats: StatsSnapshot = StatsSnapshot.EMPTY,
    val favorites: List<Exercise> = emptyList(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    prefs: PreferencesRepository,
    stats: StatsRepository,
    exercises: ExerciseRepository,
) : ViewModel() {
    val ui: StateFlow<ProfileUi> = combine(prefs.prefs, stats.snapshot, exercises.exercises) { p, s, ex ->
        ProfileUi(false, p, s, ex.filter { it.isFavorite }.sortedBy { it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val p = ui.prefs
    val s = ui.stats
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Profile", actions = {
            ApexIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
        })
        if (ui.loading) { LoadingBox(); return@Column }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.name, p.photoPath, 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { "Athlete" }, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                    val tagline = listOfNotNull(p.goal?.label, p.experience?.label).joinToString(" · ")
                    if (tagline.isNotBlank()) {
                        Text(tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.createdAt > 0) {
                        Text("Member since ${formatDate(p.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ApexIconButton(Icons.Rounded.Edit, "Edit profile", onEditProfile)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Workouts", s.totalWorkouts.toString(), Modifier.weight(1f))
                StatTile("Streak", s.currentStreak.toString(), Modifier.weight(1f), unit = "days")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Total volume", formatVolume(s.totalVolumeKg, p.units), Modifier.weight(1f), unit = p.units.weightUnit)
                StatTile("Longest streak", s.longestStreak.toString(), Modifier.weight(1f), unit = "days")
            }
            if (p.heightCm > 0 || p.weightKg > 0) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (p.heightCm > 0) {
                        val text = if (p.units == UnitSystem.METRIC) "${p.heightCm.toInt()}" else String.format(java.util.Locale.US, "%.1f", cmToInches(p.heightCm))
                        StatTile("Height", text, Modifier.weight(1f), unit = if (p.units == UnitSystem.METRIC) "cm" else "in")
                    }
                    if (p.weightKg > 0) {
                        StatTile("Weight", formatWeight(p.weightKg, p.units), Modifier.weight(1f), unit = p.units.weightUnit)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkRow(Icons.Rounded.History, "Workout history", onOpenHistory)
                LinkRow(Icons.Rounded.FitnessCenter, "My routines", onOpenRoutines)
                LinkRow(Icons.Rounded.WaterDrop, "Water tracker", onOpenWater)
            }
            Spacer(Modifier.height(24.dp))
            SectionHeader("Favorite exercises")
            if (ui.favorites.isEmpty()) {
                Text("Tap the heart on any exercise to save it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.favorites.forEach { ex -> ExerciseRow(ex, onClick = { onOpenExercise(ex.id) }) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
