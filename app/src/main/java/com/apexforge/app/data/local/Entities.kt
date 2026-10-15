package com.apexforge.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Multi-value text columns (steps, cues, ...) are stored joined by newlines so the exercise
 * table stays flat and new exercises can be added without schema changes.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val equipment: String,
    val difficulty: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val steps: String,
    val cues: String,
    val mistakes: String,
    val safety: String,
    /** Optional asset path for a licensed image. Null means the generated illustration is used. */
    val imageAsset: String? = null,
    val isPublished: Boolean = true,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val exerciseId: String,
    val addedAt: Long,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** Non-null for the built-in templates so they can be restored later. */
    val templateKey: String?,
    /** Bitmask of scheduled weekdays: Monday = 1, Tuesday = 2, ... Sunday = 64. */
    val scheduledDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: Int,
    val weightKg: Double,
    val restSec: Int,
    val notes: String,
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index("status"), Index("startedAt")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val routineId: String?,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    /** "ACTIVE" or "COMPLETED". At most one session is ACTIVE at a time. */
    val status: String,
    val notes: String,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val exerciseId: String,
    val exerciseOrder: Int,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean,
    val rpe: Int?,
    val note: String,
    val restSec: Int,
    val completedAt: Long?,
)

@Entity(tableName = "water_entries", indices = [Index("date")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local calendar day (ISO yyyy-MM-dd) fixed at the moment of logging, so time-zone changes never rewrite history. */
    val date: String,
    val amountMl: Int,
    val timestamp: Long,
)

@Entity(tableName = "body_weights", indices = [Index("date")])
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val timestamp: Long,
)

/** One completed set joined with its session. Read-only projection used by analytics. */
data class LoggedSet(
    val setId: Long,
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
)

data class DayTotal(
    val date: String,
    val totalMl: Int,
)

object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
}
