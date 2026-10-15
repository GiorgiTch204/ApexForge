package com.apexforge.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ExerciseEntity::class,
        FavoriteEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        WaterEntryEntity::class,
        BodyWeightEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ApexDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun waterDao(): WaterDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "apexforge.db"
    }
}

/**
 * Schema history. Version 1 is the first release, so there is nothing to migrate yet.
 *
 * When you change an entity: bump `version` above, add a Migration here, e.g.
 *
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE exercises ADD COLUMN videoUrl TEXT")
 *       }
 *   }
 *
 * and register it in ALL. Exported schemas land in app/schemas/ for review and testing.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
