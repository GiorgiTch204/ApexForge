package com.apexforge.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Bulk reads and idempotent inserts used only by cloud sync. It adds no tables, so the database
 * schema (and its version) is unchanged.
 */
@Dao
abstract class SyncDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED'")
    abstract suspend fun completedSessions(): List<WorkoutSessionEntity>

    @Query("SELECT id FROM workout_sessions")
    abstract suspend fun allSessionIds(): List<String>

    @Query("SELECT * FROM workout_sets WHERE sessionId IN (:sessionIds) AND completed = 1")
    abstract suspend fun completedSetsFor(sessionIds: List<String>): List<WorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert
    abstract suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Transaction
    open suspend fun insertSessionWithSets(session: WorkoutSessionEntity, sets: List<WorkoutSetEntity>) {
        insertSession(session)
        if (sets.isNotEmpty()) insertSets(sets)
    }

    @Query("SELECT * FROM routines")
    abstract suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position")
    abstract suspend fun routineItems(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM water_entries")
    abstract suspend fun waterEntries(): List<WaterEntryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertWater(entry: WaterEntryEntity): Long

    @Query("SELECT * FROM body_weights")
    abstract suspend fun bodyWeights(): List<BodyWeightEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBodyWeight(entry: BodyWeightEntity): Long

    @Query("SELECT exerciseId FROM favorites")
    abstract suspend fun favoriteIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addFavorite(favorite: FavoriteEntity)
}
