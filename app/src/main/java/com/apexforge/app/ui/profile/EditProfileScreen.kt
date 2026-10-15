package com.apexforge.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.repo.BodyWeightRepository
import com.apexforge.app.domain.Experience
import com.apexforge.app.domain.Goal
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.cmToInches
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.inchesToCm
import com.apexforge.app.domain.toKg
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexChip
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.Avatar
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.components.Stepper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Year
import javax.inject.Inject
import kotlin.math.max

data class EditProfileState(
    val loading: Boolean = true,
    val name: String = "",
    val goal: Goal? = null,
    val experience: Experience? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    val height: String = "",
    val weight: String = "",
    val birthYear: String = "",
    val frequency: Int = 3,
    val photoPath: String = "",
    val photoError: String? = null,
    val saving: Boolean = false,
) {
    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    private val heightCm: Double? get() = num(height)?.let { if (units == UnitSystem.METRIC) it else inchesToCm(it) }
    private val weightKg: Double? get() = num(weight)?.let { units.toKg(it) }
    val heightError get() = height.isNotBlank() && (heightCm == null || heightCm!! !in 50.0..260.0)
    val weightError get() = weight.isNotBlank() && (weightKg == null || weightKg!! !in 20.0..500.0)
    val birthError get() = birthYear.isNotBlank() && (birthYear.toIntOrNull()?.let { it in 1900..Year.now().value } != true)
    val valid get() = !heightError && !weightError && !birthError && name.isNotBlank()
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val bodyWeights: BodyWeightRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state
    private var originalWeightKg = 0.0

    init {
        viewModelScope.launch {
            val p = prefs.prefs.first()
            originalWeightKg = p.weightKg
            fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)
            _state.value = EditProfileState(
                loading = false, name = p.name, goal = p.goal, experience = p.experience, units = p.units,
                height = if (p.heightCm > 0) fmt(if (p.units == UnitSystem.METRIC) p.heightCm else cmToInches(p.heightCm)) else "",
                weight = if (p.weightKg > 0) fmt(p.units.fromKg(p.weightKg)) else "",
                birthYear = if (p.birthYear > 0) p.birthYear.toString() else "",
                frequency = p.frequencyPerWeek, photoPath = p.photoPath,
            )
        }
    }

    fun update(transform: (EditProfileState) -> EditProfileState) = _state.update(transform)

    /** Decodes a down-scaled copy into app storage so a huge camera photo never lives in memory or on disk. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (max(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Could not read the image")
                    val file = File(context.filesDir, "profile_photo.jpg")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    bitmap.recycle()
                    file.absolutePath
                }.getOrNull()
            }
            if (path == null) _state.update { it.copy(photoError = "That image couldn't be used. Try another one.") }
            else {
                prefs.update { it.copy(photoPath = path) }
                _state.update { it.copy(photoPath = path, photoError = null) }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { File(context.filesDir, "profile_photo.jpg").delete() }
            prefs.update { it.copy(photoPath = "") }
            _state.update { it.copy(photoPath = "", photoError = null) }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.valid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            fun num(t: String) = t.replace(',', '.').toDoubleOrNull()
            val heightCm = num(s.height)?.let { if (s.units == UnitSystem.METRIC) it else inchesToCm(it) } ?: 0.0
            val weightKg = num(s.weight)?.let { s.units.toKg(it) } ?: 0.0
            prefs.update {
                it.copy(
                    name = s.name.trim().take(40), goal = s.goal, experience = s.experience,
                    frequencyPerWeek = s.frequency, heightCm = heightCm, weightKg = weightKg,
                    birthYear = s.birthYear.toIntOrNull() ?: 0,
                )
            }
            // A changed weight is also recorded as a body-weight entry so the progress chart stays current.
            if (weightKg > 0.0 && kotlin.math.abs(weightKg - originalWeightKg) > 0.05) bodyWeights.add(weightKg)
            onDone()
        }
    }
}

@Composable
fun EditProfileScreen(onBack: () -> Unit, viewModel: EditProfileViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setPhoto(uri)
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader("Edit profile", onBack = onBack)
        if (s.loading) { LoadingBox(); return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(s.name, s.photoPath, 80.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApexButton("Choose photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, style = ButtonStyle.Secondary, compact = true)
                    if (s.photoPath.isNotBlank()) ApexButton("Remove", viewModel::removePhoto, style = ButtonStyle.Ghost, compact = true)
                }
            }
            s.photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(20.dp))
            ApexTextField(s.name, { v -> viewModel.update { it.copy(name = v.take(40)) } }, "Name",
                isError = s.name.isBlank(), supportingText = if (s.name.isBlank()) "Name can't be empty" else null)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    ApexTextField(s.height, { v -> viewModel.update { it.copy(height = v.take(6)) } },
                        "Height", suffix = if (s.units == UnitSystem.METRIC) "cm" else "in", keyboardType = KeyboardType.Decimal,
                        isError = s.heightError, supportingText = if (s.heightError) "Check this value" else null)
                }
                Column(Modifier.weight(1f)) {
                    ApexTextField(s.weight, { v -> viewModel.update { it.copy(weight = v.take(6)) } },
                        "Weight", suffix = s.units.weightUnit, keyboardType = KeyboardType.Decimal,
                        isError = s.weightError, supportingText = if (s.weightError) "Check this value" else null)
                }
            }
            Spacer(Modifier.height(12.dp))
            ApexTextField(s.birthYear, { v -> viewModel.update { it.copy(birthYear = v.filter(Char::isDigit).take(4)) } },
                "Birth year (optional)", keyboardType = KeyboardType.Number,
                isError = s.birthError, supportingText = if (s.birthError) "Enter a valid year" else null)
            Spacer(Modifier.height(20.dp))
            Text("Goal", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ChipWrap(Goal.entries, s.goal, { it.label }) { g -> viewModel.update { it.copy(goal = if (it.goal == g) null else g) } }
            Spacer(Modifier.height(16.dp))
            Text("Experience", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ChipWrap(Experience.entries, s.experience, { it.label }) { e -> viewModel.update { it.copy(experience = if (it.experience == e) null else e) } }
            Spacer(Modifier.height(16.dp))
            Stepper("Workouts per week", s.frequency.toString(),
                { viewModel.update { it.copy(frequency = (it.frequency - 1).coerceAtLeast(1)) } },
                { viewModel.update { it.copy(frequency = (it.frequency + 1).coerceAtMost(7)) } })
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            ApexButton("Save", { viewModel.save(onBack) }, Modifier.fillMaxWidth(), enabled = s.valid && !s.saving)
        }
    }
}

/** Wrapping row of single-select chips. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChipWrap(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { ApexChip(label(it), it == selected, { onSelect(it) }) }
    }
}
