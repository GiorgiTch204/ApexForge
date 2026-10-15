package com.apexforge.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isPublished = 1 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    /** IGNORE keeps user data intact and lets newer app versions add exercises. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT exerciseId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE exerciseId = :id")
    suspend fun removeFavorite(id: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE exerciseId = :id")
    suspend fun favoriteCount(id: String): Int
}

@Dao
abstract class RoutineDao {
    @Query("SELECT * FROM routines ORDER BY CASE WHEN templateKey IS NULL THEN 0 ELSE 1 END, updatedAt DESC")
    abstract fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercises ORDER BY routineId, position")
    abstract fun observeAllItems(): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    abstract suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routine_exercises WHERE routineId = :id ORDER BY position")
    abstract suspend fun getItems(id: String): List<RoutineExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRoutine(routine: RoutineEntity)

    @Insert
    abstract suspend fun insertItems(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :id")
    abstract suspend fun deleteItems(id: String)

    @Query("DELETE FROM routines WHERE id = :id")
    abstract suspend fun deleteRoutine(id: String)

    @Query("SELECT templateKey FROM routines WHERE templateKey IS NOT NULL")
    abstract suspend fun templateKeys(): List<String>

    @Query("UPDATE routines SET lastUsedAt = :timestamp WHERE id = :id")
    abstract suspend fun markUsed(id: String, timestamp: Long)

    @Transaction
    open suspend fun replace(routine: RoutineEntity, items: List<RoutineExerciseEntity>) {
        upsertRoutine(routine)
        deleteItems(routine.id)
        insertItems(items.mapIndexed { index, item -> item.copy(id = 0, routineId = routine.id, position = index) })
    }
}

internal const val LOGGED_SET_SQL = """
    SELECT s.id AS setId, s.sessionId AS sessionId, w.startedAt AS startedAt,
           COALESCE(w.endedAt, w.startedAt) AS endedAt, s.exerciseId AS exerciseId,
           s.setNumber AS setNumber, s.weightKg AS weightKg, s.reps AS reps, s.rpe AS rpe
    FROM workout_sets s
    INNER JOIN workout_sessions w ON w.id = s.sessionId
    WHERE s.completed = 1 AND w.status = 'COMPLETED'
"""

@Dao
abstract class WorkoutDao {
    @Insert
    abstract suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert
    abstract suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Insert
    abstract suspend fun insertSet(set: WorkoutSetEntity): Long

    @Update
    abstract suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun getSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY endedAt DESC")
    abstract fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :id ORDER BY exerciseOrder, setNumber")
    abstract fun observeSets(id: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :id ORDER BY exerciseOrder, setNumber")
    abstract suspend fun getSets(id: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    abstract suspend fun getSet(id: Long): WorkoutSetEntity?

    @Query("UPDATE workout_sets SET weightKg = :weightKg WHERE id = :id")
    abstract suspend fun setWeight(id: Long, weightKg: Double)

    @Query("UPDATE workout_sets SET reps = :reps WHERE id = :id")
    abstract suspend fun setReps(id: Long, reps: Int)

    @Query("UPDATE workout_sets SET completed = :completed, completedAt = :at WHERE id = :id")
    abstract suspend fun setCompleted(id: Long, completed: Boolean, at: Long?)

    @Query("UPDATE workout_sets SET rpe = :rpe, note = :note WHERE id = :id")
    abstract suspend fun setRpeAndNote(id: Long, rpe: Int?, note: String)

    @Query("UPDATE workout_sets SET setNumber = :number WHERE id = :id")
    abstract suspend fun setNumber(id: Long, number: Int)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    abstract suspend fun deleteSetRow(id: Long)

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    abstract suspend fun deleteExerciseSets(sessionId: String, exerciseId: String)

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId AND completed = 0")
    abstract suspend fun deleteIncompleteSets(sessionId: String)

    @Query("SELECT MAX(exerciseOrder) FROM workout_sets WHERE sessionId = :sessionId")
    abstract suspend fun maxOrder(sessionId: String): Int?

    @Query("UPDATE workout_sessions SET status = 'COMPLETED', endedAt = :at WHERE id = :id")
    abstract suspend fun markCompleted(id: String, at: Long)

    @Query("UPDATE workout_sessions SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: String, name: String)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    abstract suspend fun deleteSession(id: String)

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = 'COMPLETED' AND endedAt BETWEEN :from AND :to")
    abstract suspend fun countCompletedBetween(from: Long, to: Long): Int

    @Query(LOGGED_SET_SQL)
    abstract fun observeLoggedSets(): Flow<List<LoggedSet>>

    @Query("$LOGGED_SET_SQL AND s.exerciseId = :exerciseId AND s.sessionId != :excludeSessionId ORDER BY w.startedAt DESC, s.setNumber ASC")
    abstract suspend fun getPreviousRows(exerciseId: String, excludeSessionId: String): List<LoggedSet>

    /**
     * Starts a session unless one is already active. Returning the existing id makes
     * double-taps and races harmless: there can never be two active workouts.
     */
    @Transaction
    open suspend fun startSessionIfNone(session: WorkoutSessionEntity, sets: List<WorkoutSetEntity>): String {
        val active = getActiveSession()
        if (active != null) return active.id
        insertSession(session)
        if (sets.isNotEmpty()) insertSets(sets)
        return session.id
    }

    /** Deletes a set and closes the numbering gap for its exercise. */
    @Transaction
    open suspend fun deleteSetAndRenumber(setId: Long) {
        val target = getSet(setId) ?: return
        deleteSetRow(setId)
        getSets(target.sessionId)
            .filter { it.exerciseId == target.exerciseId }
            .sortedBy { it.setNumber }
            .forEachIndexed { index, set ->
                if (set.setNumber != index + 1) setNumber(set.id, index + 1)
            }
    }

    /**
     * Completes a session. Unfinished sets are dropped (they were never performed).
     * Returns false, changing nothing, when no set was completed.
     */
    @Transaction
    open suspend fun finish(id: String, at: Long): Boolean {
        val completedCount = getSets(id).count { it.completed }
        if (completedCount == 0) return false
        deleteIncompleteSets(id)
        markCompleted(id, at)
        return true
    }
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_entries WHERE date = :date ORDER BY timestamp DESC")
    fun observeDay(date: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT date AS date, SUM(amountMl) AS totalMl FROM water_entries WHERE date >= :fromDate GROUP BY date ORDER BY date")
    fun observeTotalsSince(fromDate: String): Flow<List<DayTotal>>

    @Query("SELECT * FROM water_entries WHERE id = :id")
    suspend fun getById(id: Long): WaterEntryEntity?

    @Insert
    suspend fun insert(entry: WaterEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(entry: WaterEntryEntity)

    @Query("UPDATE water_entries SET amountMl = :amountMl WHERE id = :id")
    suspend fun updateAmount(id: Long, amountMl: Int)

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weights ORDER BY date, timestamp")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Insert
    suspend fun insert(entry: BodyWeightEntity): Long

    @Query("DELETE FROM body_weights WHERE id = :id")
    suspend fun delete(id: Long)
}
