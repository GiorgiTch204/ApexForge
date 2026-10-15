package com.apexforge.app.data.repo

import com.apexforge.app.data.local.BodyWeightDao
import com.apexforge.app.data.local.BodyWeightEntity
import com.apexforge.app.data.local.DayTotal
import com.apexforge.app.data.local.ExerciseDao
import com.apexforge.app.data.local.FavoriteEntity
import com.apexforge.app.data.local.RoutineDao
import com.apexforge.app.data.local.RoutineEntity
import com.apexforge.app.data.local.RoutineExerciseEntity
import com.apexforge.app.data.local.WaterDao
import com.apexforge.app.data.local.WaterEntryEntity
import com.apexforge.app.domain.Exercise
import com.apexforge.app.domain.WaterCalc
import com.apexforge.app.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class ExerciseRepository @Inject constructor(private val dao: ExerciseDao) {
    val exercises: Flow<List<Exercise>> = combine(dao.observeAll(), dao.observeFavoriteIds()) { list, favorites ->
        val favoriteSet = favorites.toSet()
        list.map { it.toDomain(it.id in favoriteSet) }
    }

    fun observe(id: String): Flow<Exercise?> = exercises.map { list -> list.firstOrNull { it.id == id } }

    suspend fun toggleFavorite(id: String) {
        if (dao.favoriteCount(id) > 0) dao.removeFavorite(id)
        else dao.addFavorite(FavoriteEntity(exerciseId = id, addedAt = System.currentTimeMillis()))
    }
}

data class RoutineItem(val entity: RoutineExerciseEntity, val exercise: Exercise?)

data class RoutineWithItems(val routine: RoutineEntity, val items: List<RoutineItem>) {
    /** Rough estimate: about 40 seconds of work per set plus the configured rest. */
    val estimatedMinutes: Int
        get() = ceil(items.sumOf { it.entity.sets * (40 + it.entity.restSec) } / 60.0).toInt().coerceAtLeast(1)
}

@Singleton
class RoutineRepository @Inject constructor(
    private val dao: RoutineDao,
    exerciseRepository: ExerciseRepository,
) {
    val routines: Flow<List<RoutineWithItems>> = combine(
        dao.observeRoutines(),
        dao.observeAllItems(),
        exerciseRepository.exercises,
    ) { routines, items, exercises ->
        val byId = exercises.associateBy { it.id }
        val grouped = items.groupBy { it.routineId }
        routines.map { routine ->
            RoutineWithItems(
                routine,
                grouped[routine.id].orEmpty().map { RoutineItem(it, byId[it.exerciseId]) },
            )
        }
    }

    suspend fun getRoutine(id: String): RoutineEntity? = dao.getRoutine(id)

    suspend fun getItems(id: String): List<RoutineExerciseEntity> = dao.getItems(id)

    suspend fun save(routine: RoutineEntity, items: List<RoutineExerciseEntity>) = dao.replace(routine, items)

    suspend fun duplicate(id: String) {
        val source = dao.getRoutine(id) ?: return
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (copy)".take(60),
            templateKey = null,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null,
        )
        dao.replace(copy, dao.getItems(id))
    }

    suspend fun delete(id: String) = dao.deleteRoutine(id)
}

@Singleton
class WaterRepository @Inject constructor(private val dao: WaterDao) {
    fun observeDay(date: LocalDate): Flow<List<WaterEntryEntity>> = dao.observeDay(date.toString())

    fun observeTotalsSince(from: LocalDate): Flow<List<DayTotal>> = dao.observeTotalsSince(from.toString())

    /** The calendar day is fixed when the drink is logged, so later time-zone changes never move it. */
    suspend fun add(amountMl: Int, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val amount = amountMl.coerceIn(1, MAX_ENTRY_ML)
        val date = WaterCalc.dayKey(now, zone).toString()
        return dao.insert(WaterEntryEntity(date = date, amountMl = amount, timestamp = now))
    }

    suspend fun updateAmount(id: Long, amountMl: Int) = dao.updateAmount(id, amountMl.coerceIn(1, MAX_ENTRY_ML))

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun getById(id: Long): WaterEntryEntity? = dao.getById(id)

    suspend fun restore(entry: WaterEntryEntity) = dao.restore(entry)

    companion object {
        const val MAX_ENTRY_ML = 5000
    }
}

@Singleton
class BodyWeightRepository @Inject constructor(private val dao: BodyWeightDao) {
    val entries: Flow<List<BodyWeightEntity>> = dao.observeAll()

    suspend fun add(weightKg: Double, date: LocalDate = LocalDate.now()) {
        dao.insert(BodyWeightEntity(date = date.toString(), weightKg = weightKg, timestamp = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
