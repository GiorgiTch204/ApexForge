package com.apexforge.app.di

import android.content.Context
import androidx.room.Room
import com.apexforge.app.data.local.ApexDatabase
import com.apexforge.app.data.local.BodyWeightDao
import com.apexforge.app.data.local.ExerciseDao
import com.apexforge.app.data.local.Migrations
import com.apexforge.app.data.local.RoutineDao
import com.apexforge.app.data.local.SyncDao
import com.apexforge.app.data.local.WaterDao
import com.apexforge.app.data.local.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ApexDatabase =
        Room.databaseBuilder(context, ApexDatabase::class.java, ApexDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun provideExerciseDao(db: ApexDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideRoutineDao(db: ApexDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: ApexDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideWaterDao(db: ApexDatabase): WaterDao = db.waterDao()
    @Provides fun provideBodyWeightDao(db: ApexDatabase): BodyWeightDao = db.bodyWeightDao()
    @Provides fun provideSyncDao(db: ApexDatabase): SyncDao = db.syncDao()
}
