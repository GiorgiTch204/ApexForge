package com.apexforge.app.data.seed

import android.content.Context
import android.util.Log
import com.apexforge.app.data.local.ExerciseDao
import com.apexforge.app.data.local.RoutineDao
import com.apexforge.app.data.local.RoutineEntity
import com.apexforge.app.data.local.RoutineExerciseEntity
import com.apexforge.app.data.prefs.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Loads every assets/exercises_*.txt file into Room. Existing rows are left untouched. */
@Singleton
class ExerciseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExerciseDao,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val files = context.assets.list("")
            .orEmpty()
            .filter { it.startsWith("exercises_") && it.endsWith(".txt") }
            .sorted()
        val parsed = files.flatMap { file ->
            val lines = context.assets.open(file).bufferedReader(Charsets.UTF_8).use { it.readLines() }
            val result = ExerciseParser.parse(lines)
            result.errors.forEach { Log.w("ExerciseSeeder", "$file: $it") }
            result.exercises
        }
        if (parsed.isNotEmpty()) dao.insertAll(parsed)
    }
}

@Singleton
class TemplateSeeder @Inject constructor(
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val prefs: PreferencesRepository,
) {
    /** Seeds the starter routines once. Deleting them afterwards is respected. */
    suspend fun seedIfNeeded() {
        if (prefs.prefs.first().templatesSeeded) return
        insertMissing()
        prefs.update { it.copy(templatesSeeded = true) }
    }

    /** Re-adds any built-in routine the user has deleted. Returns how many were restored. */
    suspend fun restoreMissing(): Int = insertMissing()

    private suspend fun insertMissing(): Int {
        val existing = routineDao.templateKeys().toSet()
        val now = System.currentTimeMillis()
        var inserted = 0
        for (def in TemplateRoutines.ALL) {
            if (def.key in existing) continue
            val items = def.items
                .filter { exerciseDao.getById(it.exerciseId) != null }
                .mapIndexed { index, item ->
                    RoutineExerciseEntity(
                        routineId = "",
                        exerciseId = item.exerciseId,
                        position = index,
                        sets = item.sets,
                        reps = item.reps,
                        weightKg = 0.0,
                        restSec = item.restSec,
                        notes = "",
                    )
                }
            if (items.isEmpty()) continue
            val routine = RoutineEntity(
                id = UUID.randomUUID().toString(),
                name = def.name,
                description = def.description,
                templateKey = def.key,
                scheduledDays = 0,
                createdAt = now,
                updatedAt = now,
                lastUsedAt = null,
            )
            routineDao.replace(routine, items)
            inserted++
        }
        return inserted
    }
}
