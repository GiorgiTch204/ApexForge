package com.apexforge.app.data.seed

import com.apexforge.app.data.local.ExerciseEntity
import com.apexforge.app.domain.Category
import com.apexforge.app.domain.Difficulty
import com.apexforge.app.domain.Equipment
import com.apexforge.app.domain.SafetyTips
import com.apexforge.app.domain.slugify

/**
 * Parses the exercise text files in assets (exercises_*.txt). One exercise per line:
 *
 *   name|CATEGORY|EQUIPMENT|DIFFICULTY|Primary muscle|Secondary;Secondary|step;step;step|cue;cue|mistake;mistake
 *
 * Lines starting with # and blank lines are ignored. Lists use ';' as separator, so avoid ';' and '|'
 * inside text. Add exercises by appending lines; no UI or schema changes are needed.
 */
object ExerciseParser {
    private const val COLUMNS = 9

    data class ParseResult(val exercises: List<ExerciseEntity>, val errors: List<String>)

    fun parse(lines: List<String>): ParseResult {
        val exercises = mutableListOf<ExerciseEntity>()
        val errors = mutableListOf<String>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split('|').map { it.trim() }
            if (parts.size != COLUMNS) {
                errors += "Line ${index + 1}: expected $COLUMNS columns but found ${parts.size}"
                return@forEachIndexed
            }
            val category = Category.fromName(parts[1])
            val equipment = Equipment.fromName(parts[2])
            val difficulty = Difficulty.fromName(parts[3])
            if (category == null || equipment == null || difficulty == null) {
                errors += "Line ${index + 1}: invalid category, equipment or difficulty in '${parts[0]}'"
                return@forEachIndexed
            }
            fun list(value: String) = value.split(';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            exercises += ExerciseEntity(
                id = slugify(parts[0]),
                name = parts[0],
                category = category.name,
                equipment = equipment.name,
                difficulty = difficulty.name,
                primaryMuscle = parts[4],
                secondaryMuscles = list(parts[5]),
                steps = list(parts[6]),
                cues = list(parts[7]),
                mistakes = list(parts[8]),
                safety = SafetyTips.forExercise(category, equipment, difficulty).joinToString("\n"),
            )
        }
        return ParseResult(exercises, errors)
    }
}
