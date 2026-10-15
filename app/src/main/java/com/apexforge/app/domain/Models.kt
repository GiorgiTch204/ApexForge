package com.apexforge.app.domain

import com.apexforge.app.data.local.ExerciseEntity

/** Domain view of an exercise, with enums and lists parsed out of the stored text columns. */
data class Exercise(
    val id: String,
    val name: String,
    val category: Category,
    val equipment: Equipment,
    val difficulty: Difficulty,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val steps: List<String>,
    val cues: List<String>,
    val mistakes: List<String>,
    val safety: List<String>,
    val imageAsset: String?,
    val isFavorite: Boolean,
)

private fun String.toLines(): List<String> = split('\n').map { it.trim() }.filter { it.isNotEmpty() }

fun ExerciseEntity.toDomain(isFavorite: Boolean): Exercise = Exercise(
    id = id,
    name = name,
    category = Category.fromName(category) ?: Category.FUNCTIONAL,
    equipment = Equipment.fromName(equipment) ?: Equipment.BODYWEIGHT,
    difficulty = Difficulty.fromName(difficulty) ?: Difficulty.INTERMEDIATE,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles.toLines(),
    steps = steps.toLines(),
    cues = cues.toLines(),
    mistakes = mistakes.toLines(),
    safety = safety.toLines(),
    imageAsset = imageAsset,
    isFavorite = isFavorite,
)

/** Alternatives are computed, never stored, so they stay correct as the library grows. */
object Alternatives {
    fun forExercise(target: Exercise, all: List<Exercise>, limit: Int = 6): List<Exercise> =
        all.asSequence()
            .filter { it.id != target.id }
            .filter { it.primaryMuscle == target.primaryMuscle || it.category == target.category }
            .sortedWith(
                compareByDescending<Exercise> { it.primaryMuscle == target.primaryMuscle }
                    .thenByDescending { it.equipment != target.equipment }
                    .thenBy { kotlin.math.abs(it.difficulty.rank - target.difficulty.rank) }
                    .thenBy { it.name },
            )
            .take(limit)
            .toList()
}

/** Generic safety notes derived from equipment and category, appended to any per-exercise notes. */
object SafetyTips {
    fun forExercise(category: Category, equipment: Equipment, difficulty: Difficulty): List<String> {
        val tips = mutableListOf<String>()
        when (equipment) {
            Equipment.BARBELL -> tips += "Use safety arms or a spotter for heavy sets and secure the collars."
            Equipment.DUMBBELL -> tips += "Pick a weight you can control through the full range before adding load."
            Equipment.CABLE -> tips += "Check the attachment and pin before each set and avoid jerking the stack."
            Equipment.MACHINE -> tips += "Adjust the seat and pads so the joint lines up with the machine's pivot."
            Equipment.SMITH -> tips += "Set the safety stops, and position your feet so the bar path feels natural."
            Equipment.BANDS -> tips += "Inspect bands for nicks and anchor them securely to avoid snap-back."
            Equipment.KETTLEBELL -> tips += "Clear the space around you and keep a firm grip on the handle."
            Equipment.PULLUP_BAR -> tips += "Make sure the bar is firmly mounted and step down rather than dropping."
            Equipment.CARDIO_MACHINE -> tips += "Start at a low intensity and clip any safety key to your clothing."
            Equipment.BODYWEIGHT -> tips += "Choose the easiest variation that lets you keep clean form."
        }
        when (category) {
            Category.MOBILITY -> tips += "Stretch to a point of tension, never sharp pain, and breathe steadily."
            Category.CARDIO -> tips += "Warm up gradually and stop if you feel dizzy or unwell."
            Category.CORE -> tips += "Keep your lower back neutral and exhale on the hardest part of the rep."
            else -> tips += "Warm up with lighter sets first and stop a set if form breaks down."
        }
        if (difficulty == Difficulty.ADVANCED) tips += "This is an advanced movement. Learn it with light loads or coaching first."
        tips += "Stop and seek medical advice if you feel sharp pain or have an existing injury."
        return tips
    }
}

/** Lowercase, hyphen-separated identifier derived from an exercise name. */
fun slugify(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
