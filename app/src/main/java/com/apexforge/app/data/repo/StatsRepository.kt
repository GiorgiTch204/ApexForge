package com.apexforge.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.apexforge.app.data.local.ApexDatabase
import com.apexforge.app.data.local.WorkoutDao
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.seed.ExerciseSeeder
import com.apexforge.app.data.seed.TemplateSeeder
import com.apexforge.app.domain.Analytics
import com.apexforge.app.domain.StatsSnapshot
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.fromKg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for derived statistics. Everything is recomputed from the database, never cached by hand. */
@Singleton
class StatsRepository @Inject constructor(
    workoutDao: WorkoutDao,
    exerciseRepository: ExerciseRepository,
) {
    val snapshot: Flow<StatsSnapshot> = combine(
        workoutDao.observeCompletedSessions(),
        workoutDao.observeLoggedSets(),
        exerciseRepository.exercises,
    ) { sessions, sets, exercises ->
        Analytics.buildSnapshot(
            sessions = sessions,
            sets = sets,
            exerciseNames = exercises.associate { it.id to it.name },
            today = LocalDate.now(),
            zone = ZoneId.systemDefault(),
        )
    }.flowOn(Dispatchers.Default)
}

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stats: StatsRepository,
) {
    /** Writes every completed set to a CSV in the cache directory and returns a shareable content Uri. */
    suspend fun exportHistoryCsv(units: UnitSystem): Uri = withContext(Dispatchers.IO) {
        val snapshot = stats.snapshot.first()
        val sessionNames = snapshot.sessions.associate { it.id to it.name }
        val unitLabel = units.weightUnit
        val zone = ZoneId.systemDefault()
        val builder = StringBuilder()
        builder.append("date,workout,exercise,set,weight_kg,weight_$unitLabel,reps,rpe,volume_kg\n")
        snapshot.loggedSets
            .sortedWith(compareBy({ it.endedAt }, { it.exerciseId }, { it.setNumber }))
            .forEach { set ->
                val date = Instant.ofEpochMilli(set.endedAt).atZone(zone).toLocalDateTime().toString()
                builder.append(csv(date)).append(',')
                    .append(csv(sessionNames[set.sessionId] ?: "")).append(',')
                    .append(csv(snapshot.exerciseName(set.exerciseId))).append(',')
                    .append(set.setNumber).append(',')
                    .append(String.format(Locale.US, "%.2f", set.weightKg)).append(',')
                    .append(String.format(Locale.US, "%.1f", units.fromKg(set.weightKg))).append(',')
                    .append(set.reps).append(',')
                    .append(set.rpe?.toString() ?: "").append(',')
                    .append(String.format(Locale.US, "%.1f", set.weightKg * set.reps)).append('\n')
            }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "apexforge_history.csv")
        file.writeText(builder.toString(), Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

/** Wipes local data (the app's "delete account") and restores the built-in library afterwards. */
@Singleton
class DataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ApexDatabase,
    private val prefs: PreferencesRepository,
    private val exerciseSeeder: ExerciseSeeder,
    private val templateSeeder: TemplateSeeder,
) {
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        File(context.filesDir, "profile_photo.jpg").delete()
        File(context.cacheDir, "exports").deleteRecursively()
        prefs.clearAll()
        prefs.ensureCreatedAt(System.currentTimeMillis())
        exerciseSeeder.seedIfNeeded()
        templateSeeder.seedIfNeeded()
    }
}
