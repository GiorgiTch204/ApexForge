package com.apexforge.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.apexforge.app.domain.WaterUnit
import com.apexforge.app.domain.fromMl
import com.apexforge.app.domain.inchesToCm
import com.apexforge.app.domain.toKg
import com.apexforge.app.domain.toMl
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.SegmentedChoice
import com.apexforge.app.ui.components.Stepper
import com.apexforge.app.ui.formatWater
import com.apexforge.app.ui.theme.ApexPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val goal: Goal? = null,
    val experience: Experience? = null,
    val name: String = "",
    val units: UnitSystem = UnitSystem.METRIC,
    val height: String = "",
    val weight: String = "",
    val waterGoalMl: Int = 2500,
    val frequency: Int = 3,
) {
    val heightValue: Double? get() = height.replace(',', '.').toDoubleOrNull()
    val weightValue: Double? get() = weight.replace(',', '.').toDoubleOrNull()

    private val heightCm: Double? get() = heightValue?.let { if (units == UnitSystem.METRIC) it else inchesToCm(it) }
    private val weightKg: Double? get() = weightValue?.let { units.toKg(it) }

    val heightError: Boolean get() = height.isNotBlank() && (heightCm == null || heightCm!! !in 50.0..260.0)
    val weightError: Boolean get() = weight.isNotBlank() && (weightKg == null || weightKg!! !in 20.0..500.0)
    val canContinue: Boolean get() = !(step == STEP_BODY && (heightError || weightError))

    companion object {
        const val LAST_STEP = 5
        const val STEP_BODY = 4
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val bodyWeights: BodyWeightRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun update(transform: (OnboardingState) -> OnboardingState) = _state.update(transform)

    fun next() = _state.update { if (it.step < OnboardingState.LAST_STEP) it.copy(step = it.step + 1) else it }

    fun back() = _state.update { if (it.step > 0) it.copy(step = it.step - 1) else it }

    fun setUnits(units: UnitSystem) = _state.update { it.copy(units = units, height = "", weight = "") }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            val heightCm = s.heightValue?.let { if (s.units == UnitSystem.METRIC) it else inchesToCm(it) }
                ?.takeIf { it in 50.0..260.0 } ?: 0.0
            val weightKg = s.weightValue?.let { s.units.toKg(it) }?.takeIf { it in 20.0..500.0 } ?: 0.0
            prefs.update {
                it.copy(
                    onboardingDone = true,
                    name = s.name.trim().take(40),
                    goal = s.goal,
                    experience = s.experience,
                    units = s.units,
                    waterUnit = if (s.units == UnitSystem.IMPERIAL) WaterUnit.FL_OZ else WaterUnit.ML,
                    waterGoalMl = s.waterGoalMl,
                    frequencyPerWeek = s.frequency,
                    heightCm = heightCm,
                    weightKg = weightKg,
                )
            }
            if (weightKg > 0.0) bodyWeights.add(weightKg)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLast = state.step == OnboardingState.LAST_STEP
    val canSkip = state.step in 2..OnboardingState.LAST_STEP

    Box(Modifier.fillMaxSize()) {
        // Soft atmospheric glow behind the content.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(300f, 200f),
                        radius = 900f,
                    ),
                ),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(OnboardingState.LAST_STEP + 1) { index ->
                        Box(
                            Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (index <= state.step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        )
                    }
                }
                if (canSkip) {
                    TextButton(onClick = { if (isLast) viewModel.finish(onFinished) else viewModel.next() }) {
                        Text("Skip", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Spacer(Modifier.size(8.dp))
                }
            }

            AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
                    }
                },
                label = "onboardingStep",
            ) { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp)) {
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> FeaturesStep()
                        2 -> GoalStep(state, viewModel)
                        3 -> ExperienceStep(state, viewModel)
                        4 -> BodyStep(state, viewModel)
                        else -> RoutineStep(state, viewModel)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step > 0) {
                    ApexButton("Back", viewModel::back, Modifier.weight(1f), style = ButtonStyle.Secondary)
                }
                ApexButton(
                    text = when {
                        state.step == 0 -> "Get started"
                        isLast -> "Start training"
                        else -> "Continue"
                    },
                    onClick = { if (isLast) viewModel.finish(onFinished) else viewModel.next() },
                    modifier = Modifier.weight(if (state.step > 0) 2f else 1f),
                    enabled = state.canContinue,
                )
            }
        }
    }
}

@Composable
private fun ApexLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val tri = Path().apply {
            moveTo(50f * s, 6f * s)
            lineTo(92f * s, 94f * s)
            lineTo(70f * s, 94f * s)
            lineTo(50f * s, 50f * s)
            lineTo(30f * s, 94f * s)
            lineTo(8f * s, 94f * s)
            close()
        }
        drawPath(tri, ApexPalette.Lime)
        drawRect(
            ApexPalette.Lime,
            topLeft = androidx.compose.ui.geometry.Offset(33f * s, 70f * s),
            size = androidx.compose.ui.geometry.Size(34f * s, 12f * s),
        )
    }
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(24.dp))
    ApexLogoMark(Modifier.size(88.dp))
    Spacer(Modifier.height(28.dp))
    Text("Forge your", style = MaterialTheme.typography.displayMedium)
    Text("best self.", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(
        "ApexForge is a training log built for the gym floor: fast set entry, honest progress, nothing in your way.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Everything stays on this device. No account needed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeaturesStep() {
    Text("Everything you need to lift better", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(16.dp))
    FeatureRow(Icons.Rounded.FitnessCenter, "Log every set in one tap", "Big inputs, last session's numbers beside each set and an automatic rest timer.")
    FeatureRow(Icons.Rounded.BarChart, "See real progress", "Personal records, volume and strength trends calculated only from what you log.")
    FeatureRow(Icons.Rounded.WaterDrop, "Stay hydrated", "A daily water tracker with a goal you control and reminders you can switch off.")
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ChoiceCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    ApexCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        onClick = onClick,
        borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun GoalStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepTitle("What's your main goal?", "This only personalises your home screen. You can change it any time.")
    Goal.entries.forEach { goal ->
        ChoiceCard(goal.label, goal.blurb, state.goal == goal) { vm.update { it.copy(goal = goal) } }
    }
}

@Composable
private fun ExperienceStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepTitle("How experienced are you?", "Be honest, there's no wrong answer.")
    Experience.entries.forEach { level ->
        ChoiceCard(level.label, level.blurb, state.experience == level) { vm.update { it.copy(experience = level) } }
    }
}

@Composable
private fun BodyStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepTitle("About you", "All optional. Skip anything you'd rather add later.")
    ApexTextField(
        value = state.name,
        onValueChange = { v -> vm.update { it.copy(name = v.take(40)) } },
        label = "Your name",
    )
    Spacer(Modifier.height(16.dp))
    Text("Units", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    SegmentedChoice(
        options = UnitSystem.entries,
        selected = state.units,
        label = { it.label },
        onSelect = vm::setUnits,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            ApexTextField(
                value = state.height,
                onValueChange = { v -> vm.update { it.copy(height = v.take(6)) } },
                label = "Height",
                suffix = if (state.units == UnitSystem.METRIC) "cm" else "in",
                keyboardType = KeyboardType.Decimal,
                isError = state.heightError,
                supportingText = if (state.heightError) "Check this value" else null,
            )
        }
        Column(Modifier.weight(1f)) {
            ApexTextField(
                value = state.weight,
                onValueChange = { v -> vm.update { it.copy(weight = v.take(6)) } },
                label = "Weight",
                suffix = state.units.weightUnit,
                keyboardType = KeyboardType.Decimal,
                isError = state.weightError,
                supportingText = if (state.weightError) "Check this value" else null,
            )
        }
    }
}

@Composable
private fun RoutineStep(state: OnboardingState, vm: OnboardingViewModel) {
    val waterUnit = if (state.units == UnitSystem.IMPERIAL) WaterUnit.FL_OZ else WaterUnit.ML
    StepTitle("Your weekly rhythm", "Set a training target and a daily water goal. Both are just guidance you can adjust.")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stepper(
            label = "Workouts per week",
            valueText = state.frequency.toString(),
            onMinus = { vm.update { it.copy(frequency = (it.frequency - 1).coerceAtLeast(1)) } },
            onPlus = { vm.update { it.copy(frequency = (it.frequency + 1).coerceAtMost(7)) } },
        )
    }
    Spacer(Modifier.height(20.dp))
    Stepper(
        label = "Daily water goal (${waterUnit.short})",
        valueText = formatWater(state.waterGoalMl, waterUnit),
        onMinus = { vm.update { it.copy(waterGoalMl = (it.waterGoalMl - 250).coerceAtLeast(500)) } },
        onPlus = { vm.update { it.copy(waterGoalMl = (it.waterGoalMl + 250).coerceAtMost(6000)) } },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Hydration needs vary with body size, climate and activity. Pick what works for you, this isn't medical advice.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
