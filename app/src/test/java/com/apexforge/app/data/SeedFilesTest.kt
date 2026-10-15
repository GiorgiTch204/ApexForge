package com.apexforge.app.data

import com.apexforge.app.data.seed.ExerciseParser
import com.apexforge.app.data.seed.TemplateRoutines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Validates the bundled exercise library and templates straight from the asset files. */
class SeedFilesTest {
    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val assets = File("src/main/assets")

    private fun allLines(): List<String> {
        val files = assets.listFiles { f -> f.name.startsWith("exercises_") && f.name.endsWith(".txt") }.orEmpty()
        assertTrue("No exercise asset files found in ${assets.absolutePath}", files.isNotEmpty())
        return files.sortedBy { it.name }.flatMap { it.readLines() }
    }

    @Test fun libraryParsesWithoutErrors() {
        val result = ExerciseParser.parse(allLines())
        assertTrue("Parse errors: ${result.errors}", result.errors.isEmpty())
    }

    @Test fun libraryHasAtLeast150Exercises() {
        assertTrue(ExerciseParser.parse(allLines()).exercises.size >= 150)
    }

    @Test fun idsAreUniqueAndNonBlank() {
        val ids = ExerciseParser.parse(allLines()).exercises.map { it.id }
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everyExerciseHasStepsCuesAndPrimaryMuscle() {
        ExerciseParser.parse(allLines()).exercises.forEach {
            assertTrue("${it.id} has no steps", it.steps.isNotBlank())
            assertTrue("${it.id} has no primary muscle", it.primaryMuscle.isNotBlank())
        }
    }

    @Test fun templatesReferenceExistingExercisesAndValidValues() {
        val ids = ExerciseParser.parse(allLines()).exercises.map { it.id }.toSet()
        TemplateRoutines.ALL.forEach { def ->
            assertTrue("${def.key} is empty", def.items.isNotEmpty())
            def.items.forEach { item ->
                assertTrue("${def.key} references unknown exercise ${item.exerciseId}", item.exerciseId in ids)
                assertTrue(item.sets in 1..20 && item.reps in 1..100 && item.restSec in 0..600)
            }
        }
        assertEquals(TemplateRoutines.ALL.size, TemplateRoutines.ALL.map { it.key }.toSet().size)
    }
}
