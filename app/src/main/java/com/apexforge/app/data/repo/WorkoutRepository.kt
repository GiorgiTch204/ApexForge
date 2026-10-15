package com.apexforge.app.data.repo

import com.apexforge.app.data.local.ExerciseDao
import com.apexforge.app.data.local.LoggedSet
import com.apexforge.app.data.local.RoutineDao
import com.apexforge.app.data.local.SessionStatus
import com.apexforge.app.data.local.WorkoutDao
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StartRequest {
    data object Empty : StartRequest
    data class FromRoutine(val routineId: String) : StartRequest
    data class FromSession(val sessionId: String) : StartRequest
    data class WithExercise(val exerciseId: String) : StartRequest
}

private data class SetDraft(val weightKg: Double, val reps: Int)
private data class ExerciseDraft(val exerciseId: String, val restSec: Int, val sets: List<SetDraft>)

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
) {
    fun observeActiveSession(): Flow<WorkoutSessionEntity?> = dao.observeActiveSession()
    fun observeSession(id: String): Flow<WorkoutSessionEntity?> = dao.observeSession(id)
    fun observeSets(id: String): Flow<List<WorkoutSetEntity>> = dao.observeSets(id)

    suspend fun getActiveSession(): WorkoutSessionEntity? = dao.getActiveSession()

    /**
     * Creates a workout and returns its id. If one is already active, that id is returned instead,
     * so a double tap (or a race between screens) can never produce a duplicate record.
     */
    suspend fun startSession(request: StartRequest, defaultRestSec: Int): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        var name = "Quick workout"
        var routineId: String? = null
        var drafts: List<ExerciseDraft> = emptyList()

        when (request) {
            StartRequest.Empty -> Unit
            is StartRequest.FromRoutine -> {
                val routine = routineDao.getRoutine(request.routineId)
                if (routine != null) {
                    name = routine.name
                    routineId = routine.id
                    drafts = routineDao.getItems(routine.id)
                        .filter { exerciseDao.getById(it.exerciseId) != null }
                        .map { item ->
                            val previous = previousSets(item.exerciseId, id)
                            ExerciseDraft(
                                exerciseId = item.exerciseId,
                                restSec = item.restSec,
                                sets = (1..item.sets).map { number ->
                                    val weight = when {
                                        item.weightKg > 0.0 -> item.weightKg
                                        else -> previous.getOrNull(number - 1)?.weightKg
                                            ?: previous.lastOrNull()?.weightKg
                                            ?: 0.0
                                    }
                                    SetDraft(weight, item.reps)
                                },
                            )
                        }
                    routineDao.markUsed(routine.id, now)
                }
            }
            is StartRequest.FromSession -> {
                val source = dao.getSession(request.sessionId)
                if (source != null) {
                    name = source.name
                    routineId = source.routineId
                    drafts = dao.getSets(source.id)
                        .groupBy { it.exerciseId }
                        .map { (exerciseId, sets) ->
                            ExerciseDraft(
                                exerciseId = exerciseId,
                                restSec = sets.first().restSec,
                                sets = sets.sortedBy { it.setNumber }.map { SetDraft(it.weightKg, it.reps) },
                            )
                        }
                }
            }
            is StartRequest.WithExercise -> {
                if (exerciseDao.getById(request.exerciseId) != null) {
                    drafts = listOf(defaultDraft(request.exerciseId, id, defaultRestSec))
                }
            }
        }

        val sets = drafts.flatMapIndexed { order, draft ->
            draft.sets.mapIndexed { index, set ->
                WorkoutSetEntity(
                    sessionId = id,
                    exerciseId = draft.exerciseId,
                    exerciseOrder = order,
                    setNumber = index + 1,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completed = false,
                    rpe = null,
                    note = "",
                    restSec = draft.restSec,
                    completedAt = null,
                )
            }
        }
        val session = WorkoutSessionEntity(
            id = id,
            routineId = routineId,
            name = name,
            startedAt = now,
            endedAt = null,
            status = SessionStatus.ACTIVE,
            notes = "",
        )
        return dao.startSessionIfNone(session, sets)
    }

    private suspend fun defaultDraft(exerciseId: String, sessionId: String, restSec: Int): ExerciseDraft {
        val previous = previousSets(exerciseId, sessionId)
        val sets = if (previous.isNotEmpty()) previous.take(5).map { SetDraft(it.weightKg, it.reps) }
        else List(3) { SetDraft(0.0, 10) }
        return ExerciseDraft(exerciseId, restSec, sets)
    }

    /** Sets of the most recent completed session that included [exerciseId], excluding [excludeSessionId]. */
    suspend fun previousSets(exerciseId: String, excludeSessionId: String): List<LoggedSet> {
        val rows = dao.getPreviousRows(exerciseId, excludeSessionId)
        if (rows.isEmpty()) return emptyList()
        val latest = rows.first().sessionId
        return rows.takeWhile { it.sessionId == latest }
    }

    suspend fun addExercises(sessionId: String, exerciseIds: List<String>, defaultRestSec: Int) {
        var order = (dao.maxOrder(sessionId) ?: -1) + 1
        for (exerciseId in exerciseIds) {
            if (exerciseDao.getById(exerciseId) == null) continue
            val draft = defaultDraft(exerciseId, sessionId, defaultRestSec)
            val sets = draft.sets.mapIndexed { index, set ->
                WorkoutSetEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exerciseOrder = order,
                    setNumber = index + 1,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completed = false,
                    rpe = null,
                    note = "",
                    restSec = draft.restSec,
                    completedAt = null,
                )
            }
            dao.insertSets(sets)
            order++
        }
    }

    suspend fun addSet(sessionId: String, exerciseId: String) {
        val existing = dao.getSets(sessionId).filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }
        val last = existing.lastOrNull() ?: return
        dao.insertSet(
            last.copy(
                id = 0,
                setNumber = last.setNumber + 1,
                completed = false,
                completedAt = null,
                rpe = null,
                note = "",
            ),
        )
    }

    suspend fun deleteSet(setId: Long) = dao.deleteSetAndRenumber(setId)

    suspend fun removeExercise(sessionId: String, exerciseId: String) = dao.deleteExerciseSets(sessionId, exerciseId)

    suspend fun setWeight(setId: Long, weightKg: Double) = dao.setWeight(setId, weightKg.coerceIn(0.0, 2000.0))

    suspend fun setReps(setId: Long, reps: Int) = dao.setReps(setId, reps.coerceIn(0, 1000))

    suspend fun setRpeAndNote(setId: Long, rpe: Int?, note: String) = dao.setRpeAndNote(setId, rpe, note.take(200))

    /** Returns false when the set cannot be completed yet (no reps entered). */
    suspend fun toggleComplete(setId: Long): Boolean {
        val set = dao.getSet(setId) ?: return false
        if (!set.completed && set.reps <= 0) return false
        dao.setCompleted(setId, !set.completed, if (set.completed) null else System.currentTimeMillis())
        return true
    }

    /** Fills every unfinished set of an exercise with the values from last time. */
    suspend fun usePrevious(sessionId: String, exerciseId: String) {
        val previous = previousSets(exerciseId, sessionId)
        if (previous.isEmpty()) return
        dao.getSets(sessionId)
            .filter { it.exerciseId == exerciseId && !it.completed }
            .forEach { set ->
                val source = previous.getOrNull(set.setNumber - 1) ?: previous.last()
                dao.setWeight(set.id, source.weightKg)
                dao.setReps(set.id, source.reps)
            }
    }

    suspend fun renameSession(id: String, name: String) = dao.rename(id, name.trim().take(60).ifBlank { "Workout" })

    /** True when the workout was saved. False means nothing was completed and nothing changed. */
    suspend fun finish(id: String): Boolean = dao.finish(id, System.currentTimeMillis())

    suspend fun discard(id: String) = dao.deleteSession(id)

    suspend fun hasWorkoutBetween(from: Long, to: Long): Boolean = dao.countCompletedBetween(from, to) > 0
}
