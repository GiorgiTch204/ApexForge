package com.apexforge.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apexforge.app.domain.Experience
import com.apexforge.app.domain.Goal
import com.apexforge.app.domain.ThemeMode
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.WaterUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apexDataStore by preferencesDataStore(name = "apex_prefs")

data class UserPrefs(
    val onboardingDone: Boolean = false,
    val name: String = "",
    val goal: Goal? = null,
    val experience: Experience? = null,
    val frequencyPerWeek: Int = 3,
    val units: UnitSystem = UnitSystem.METRIC,
    val waterUnit: WaterUnit = WaterUnit.ML,
    val waterGoalMl: Int = 2500,
    /** 0 means "not provided". */
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val birthYear: Int = 0,
    val photoPath: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoRestTimer: Boolean = true,
    val defaultRestSec: Int = 90,
    val haptics: Boolean = true,
    val workoutReminder: Boolean = false,
    val workoutReminderMinutes: Int = 18 * 60,
    val waterReminder: Boolean = false,
    val waterReminderHours: Int = 2,
    val weeklySummary: Boolean = false,
    val createdAt: Long = 0L,
    val templatesSeeded: Boolean = false,
)

private object Keys {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val NAME = stringPreferencesKey("name")
    val GOAL = stringPreferencesKey("goal")
    val EXPERIENCE = stringPreferencesKey("experience")
    val FREQUENCY = intPreferencesKey("frequency")
    val UNITS = stringPreferencesKey("units")
    val WATER_UNIT = stringPreferencesKey("water_unit")
    val WATER_GOAL = intPreferencesKey("water_goal_ml")
    val HEIGHT = doublePreferencesKey("height_cm")
    val WEIGHT = doublePreferencesKey("weight_kg")
    val BIRTH_YEAR = intPreferencesKey("birth_year")
    val PHOTO = stringPreferencesKey("photo_path")
    val THEME = stringPreferencesKey("theme_mode")
    val AUTO_REST = booleanPreferencesKey("auto_rest")
    val DEFAULT_REST = intPreferencesKey("default_rest_sec")
    val HAPTICS = booleanPreferencesKey("haptics")
    val WORKOUT_REMINDER = booleanPreferencesKey("workout_reminder")
    val WORKOUT_REMINDER_MIN = intPreferencesKey("workout_reminder_minutes")
    val WATER_REMINDER = booleanPreferencesKey("water_reminder")
    val WATER_REMINDER_HOURS = intPreferencesKey("water_reminder_hours")
    val WEEKLY_SUMMARY = booleanPreferencesKey("weekly_summary")
    val CREATED_AT = longPreferencesKey("created_at")
    val TEMPLATES_SEEDED = booleanPreferencesKey("templates_seeded")
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }

private fun Preferences.toUserPrefs(): UserPrefs {
    val d = UserPrefs()
    return UserPrefs(
        onboardingDone = this[Keys.ONBOARDING_DONE] ?: d.onboardingDone,
        name = this[Keys.NAME] ?: d.name,
        goal = this[Keys.GOAL].toEnumOrNull<Goal>(),
        experience = this[Keys.EXPERIENCE].toEnumOrNull<Experience>(),
        frequencyPerWeek = this[Keys.FREQUENCY] ?: d.frequencyPerWeek,
        units = this[Keys.UNITS].toEnumOrNull<UnitSystem>() ?: d.units,
        waterUnit = this[Keys.WATER_UNIT].toEnumOrNull<WaterUnit>() ?: d.waterUnit,
        waterGoalMl = this[Keys.WATER_GOAL] ?: d.waterGoalMl,
        heightCm = this[Keys.HEIGHT] ?: d.heightCm,
        weightKg = this[Keys.WEIGHT] ?: d.weightKg,
        birthYear = this[Keys.BIRTH_YEAR] ?: d.birthYear,
        photoPath = this[Keys.PHOTO] ?: d.photoPath,
        themeMode = this[Keys.THEME].toEnumOrNull<ThemeMode>() ?: d.themeMode,
        autoRestTimer = this[Keys.AUTO_REST] ?: d.autoRestTimer,
        defaultRestSec = this[Keys.DEFAULT_REST] ?: d.defaultRestSec,
        haptics = this[Keys.HAPTICS] ?: d.haptics,
        workoutReminder = this[Keys.WORKOUT_REMINDER] ?: d.workoutReminder,
        workoutReminderMinutes = this[Keys.WORKOUT_REMINDER_MIN] ?: d.workoutReminderMinutes,
        waterReminder = this[Keys.WATER_REMINDER] ?: d.waterReminder,
        waterReminderHours = this[Keys.WATER_REMINDER_HOURS] ?: d.waterReminderHours,
        weeklySummary = this[Keys.WEEKLY_SUMMARY] ?: d.weeklySummary,
        createdAt = this[Keys.CREATED_AT] ?: d.createdAt,
        templatesSeeded = this[Keys.TEMPLATES_SEEDED] ?: d.templatesSeeded,
    )
}

private fun MutablePreferences.write(p: UserPrefs) {
    this[Keys.ONBOARDING_DONE] = p.onboardingDone
    this[Keys.NAME] = p.name
    this[Keys.GOAL] = p.goal?.name ?: ""
    this[Keys.EXPERIENCE] = p.experience?.name ?: ""
    this[Keys.FREQUENCY] = p.frequencyPerWeek
    this[Keys.UNITS] = p.units.name
    this[Keys.WATER_UNIT] = p.waterUnit.name
    this[Keys.WATER_GOAL] = p.waterGoalMl
    this[Keys.HEIGHT] = p.heightCm
    this[Keys.WEIGHT] = p.weightKg
    this[Keys.BIRTH_YEAR] = p.birthYear
    this[Keys.PHOTO] = p.photoPath
    this[Keys.THEME] = p.themeMode.name
    this[Keys.AUTO_REST] = p.autoRestTimer
    this[Keys.DEFAULT_REST] = p.defaultRestSec
    this[Keys.HAPTICS] = p.haptics
    this[Keys.WORKOUT_REMINDER] = p.workoutReminder
    this[Keys.WORKOUT_REMINDER_MIN] = p.workoutReminderMinutes
    this[Keys.WATER_REMINDER] = p.waterReminder
    this[Keys.WATER_REMINDER_HOURS] = p.waterReminderHours
    this[Keys.WEEKLY_SUMMARY] = p.weeklySummary
    this[Keys.CREATED_AT] = p.createdAt
    this[Keys.TEMPLATES_SEEDED] = p.templatesSeeded
}

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val prefs: Flow<UserPrefs> = context.apexDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toUserPrefs() }

    /** Atomic read-modify-write of the whole preferences object. */
    suspend fun update(transform: (UserPrefs) -> UserPrefs) {
        context.apexDataStore.edit { stored -> stored.write(transform(stored.toUserPrefs())) }
    }

    suspend fun ensureCreatedAt(now: Long) {
        update { if (it.createdAt == 0L) it.copy(createdAt = now) else it }
    }

    suspend fun clearAll() {
        context.apexDataStore.edit { it.clear() }
    }
}
