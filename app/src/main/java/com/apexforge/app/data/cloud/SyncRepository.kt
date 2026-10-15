package com.apexforge.app.data.cloud

import android.content.Context
import com.apexforge.app.data.local.BodyWeightEntity
import com.apexforge.app.data.local.FavoriteEntity
import com.apexforge.app.data.local.RoutineDao
import com.apexforge.app.data.local.RoutineEntity
import com.apexforge.app.data.local.RoutineExerciseEntity
import com.apexforge.app.data.local.SessionStatus
import com.apexforge.app.data.local.SyncDao
import com.apexforge.app.data.local.WaterEntryEntity
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.local.WorkoutSetEntity
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(val pushed: Int, val pulled: Int)

/**
 * Two-way merge between Room and Firestore (users/{uid}/...). Room stays the source of truth and the app
 * works fully offline; sync is an explicit action.
 *
 * Rules, chosen so that running sync twice, or on two phones, never duplicates or corrupts data:
 *  - Workouts: completed sessions are immutable history. Missing on one side -> copied to it. If both
 *    sides have the id, nothing is overwritten.
 *  - Routines: newest `updatedAt` wins. Built-in templates are matched by their templateKey, so two
 *    phones never end up with two "Push Day" copies.
 *  - Water entries and body weights: identified by their timestamp; missing ones are copied across.
 *  - Favorites: union of both sides.
 * Deletions are NOT propagated: an item deleted on one device returns from the cloud on the next sync.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: AuthRepository,
    private val sync: SyncDao,
    private val routineDao: RoutineDao,
) {
    private val mutex = Mutex()
    private val store = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    private val _lastSyncedAt = MutableStateFlow(store.getLong(KEY_LAST, 0L).takeIf { it > 0 })
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun syncNow(): Result<SyncSummary> = runCatching {
        val signedIn = auth.state.value as? AuthState.SignedIn ?: error("Sign in to sync.")
        mutex.withLock {
            _running.value = true
            try {
                val summary = doSync(signedIn.uid)
                val now = System.currentTimeMillis()
                store.edit().putLong(KEY_LAST, now).apply()
                _lastSyncedAt.value = now
                summary
            } finally {
                _running.value = false
            }
        }
    }

    private suspend fun doSync(uid: String): SyncSummary {
        val db = FirebaseFirestore.getInstance()
        val root = db.collection("users").document(uid)
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
        var pulled = 0

        // ---- Workouts (immutable history) ----
        val remoteSessions = root.collection("workouts").get().await().documents.associate { it.id to it.data.orEmpty() }
        val knownLocal = sync.allSessionIds().toSet()
        for ((id, data) in remoteSessions) {
            if (id in knownLocal) continue
            val parsed = parseSession(id, data) ?: continue
            sync.insertSessionWithSets(parsed.first, parsed.second)
            pulled++
        }
        val toPush = sync.completedSessions().filter { it.id !in remoteSessions }
        toPush.chunked(400).forEach { chunk ->
            val setsBySession = sync.completedSetsFor(chunk.map { it.id }).groupBy { it.sessionId }
            chunk.forEach { s ->
                writes += root.collection("workouts").document(s.id) to sessionToMap(s, setsBySession[s.id].orEmpty())
            }
        }

        // ---- Routines (newest wins, templates matched by key) ----
        val remoteRoutines = root.collection("routines").get().await().documents.associate { it.id to it.data.orEmpty() }
        val localRoutines = sync.routines()
        val localByDoc = localRoutines.associateBy { routineDocId(it) }
        for ((docId, data) in remoteRoutines) {
            val local = localByDoc[docId]
            val remoteUpdated = data.long("updatedAt")
            if (local == null) {
                val parsed = parseRoutine(data.str("id").ifBlank { docId }, data) ?: continue
                routineDao.replace(parsed.first, parsed.second)
                pulled++
            } else if (remoteUpdated > local.updatedAt) {
                val parsed = parseRoutine(local.id, data) ?: continue
                routineDao.replace(parsed.first, parsed.second)
                pulled++
            }
        }
        for ((docId, local) in localByDoc) {
            val remoteUpdated = remoteRoutines[docId]?.long("updatedAt")
            if (remoteUpdated == null || local.updatedAt > remoteUpdated) {
                writes += root.collection("routines").document(docId) to routineToMap(local, sync.routineItems(local.id))
            }
        }

        // ---- Water entries ----
        val remoteWater = root.collection("water").get().await().documents.associate { it.id to it.data.orEmpty() }
        val localWater = sync.waterEntries()
        val localWaterIds = localWater.map { it.timestamp.toString() }.toSet()
        for ((id, data) in remoteWater) {
            if (id in localWaterIds) continue
            val ml = data.int("amountMl")
            val date = data.str("date")
            if (ml <= 0 || date.isBlank()) continue
            sync.insertWater(WaterEntryEntity(date = date, amountMl = ml.coerceIn(1, 5000), timestamp = id.toLongOrNull() ?: continue))
            pulled++
        }
        localWater.filter { it.timestamp.toString() !in remoteWater }.forEach { e ->
            writes += root.collection("water").document(e.timestamp.toString()) to
                mapOf("date" to e.date, "amountMl" to e.amountMl, "timestamp" to e.timestamp)
        }

        // ---- Body weights ----
        val remoteBody = root.collection("bodyWeights").get().await().documents.associate { it.id to it.data.orEmpty() }
        val localBody = sync.bodyWeights()
        val localBodyIds = localBody.map { it.timestamp.toString() }.toSet()
        for ((id, data) in remoteBody) {
            if (id in localBodyIds) continue
            val kg = data.dbl("weightKg")
            val date = data.str("date")
            if (kg !in 20.0..500.0 || date.isBlank()) continue
            sync.insertBodyWeight(BodyWeightEntity(date = date, weightKg = kg, timestamp = id.toLongOrNull() ?: continue))
            pulled++
        }
        localBody.filter { it.timestamp.toString() !in remoteBody }.forEach { e ->
            writes += root.collection("bodyWeights").document(e.timestamp.toString()) to
                mapOf("date" to e.date, "weightKg" to e.weightKg, "timestamp" to e.timestamp)
        }

        // ---- Favorites (union) ----
        val favRef = root.collection("settings").document("favorites")
        val remoteFav = (favRef.get().await().get("ids") as? List<*>).orEmpty().filterIsInstance<String>().toSet()
        val localFav = sync.favoriteIds().toSet()
        (remoteFav - localFav).forEach {
            sync.addFavorite(FavoriteEntity(exerciseId = it, addedAt = System.currentTimeMillis()))
            pulled++
        }
        if ((localFav - remoteFav).isNotEmpty()) {
            writes += favRef to mapOf("ids" to (localFav + remoteFav).sorted())
        }

        writes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
        return SyncSummary(pushed = writes.size, pulled = pulled)
    }

    // ---- Mapping ----

    private fun routineDocId(r: RoutineEntity): String = r.templateKey?.let { "tpl-$it" } ?: r.id

    private fun sessionToMap(s: WorkoutSessionEntity, sets: List<WorkoutSetEntity>): Map<String, Any?> = mapOf(
        "routineId" to s.routineId,
        "name" to s.name,
        "startedAt" to s.startedAt,
        "endedAt" to s.endedAt,
        "notes" to s.notes,
        "sets" to sets.sortedWith(compareBy({ it.exerciseOrder }, { it.setNumber })).map {
            mapOf(
                "exerciseId" to it.exerciseId,
                "exerciseOrder" to it.exerciseOrder,
                "setNumber" to it.setNumber,
                "weightKg" to it.weightKg,
                "reps" to it.reps,
                "rpe" to it.rpe,
                "note" to it.note,
                "restSec" to it.restSec,
                "completedAt" to it.completedAt,
            )
        },
    )

    private fun parseSession(id: String, d: Map<String, Any?>): Pair<WorkoutSessionEntity, List<WorkoutSetEntity>>? {
        val started = d.long("startedAt")
        val ended = d.long("endedAt")
        if (started <= 0 || ended <= 0) return null
        val session = WorkoutSessionEntity(
            id = id,
            routineId = d["routineId"] as? String,
            name = d.str("name").take(60).ifBlank { "Workout" },
            startedAt = started,
            endedAt = ended,
            status = SessionStatus.COMPLETED,
            notes = d.str("notes").take(500),
        )
        val sets = (d["sets"] as? List<*>).orEmpty().mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val s = raw as? Map<String, Any?> ?: return@mapNotNull null
            val exerciseId = s.str("exerciseId")
            if (exerciseId.isBlank()) return@mapNotNull null
            WorkoutSetEntity(
                sessionId = id,
                exerciseId = exerciseId,
                exerciseOrder = s.int("exerciseOrder"),
                setNumber = s.int("setNumber").coerceAtLeast(1),
                weightKg = s.dbl("weightKg").coerceIn(0.0, 2000.0),
                reps = s.int("reps").coerceIn(0, 1000),
                completed = true,
                rpe = (s["rpe"] as? Number)?.toInt(),
                note = s.str("note").take(200),
                restSec = s.int("restSec").coerceIn(0, 600),
                completedAt = (s["completedAt"] as? Number)?.toLong(),
            )
        }
        return session to sets
    }

    private fun routineToMap(r: RoutineEntity, items: List<RoutineExerciseEntity>): Map<String, Any?> = mapOf(
        "id" to r.id,
        "name" to r.name,
        "description" to r.description,
        "templateKey" to r.templateKey,
        "scheduledDays" to r.scheduledDays,
        "createdAt" to r.createdAt,
        "updatedAt" to r.updatedAt,
        "lastUsedAt" to r.lastUsedAt,
        "items" to items.map {
            mapOf(
                "exerciseId" to it.exerciseId,
                "sets" to it.sets,
                "reps" to it.reps,
                "weightKg" to it.weightKg,
                "restSec" to it.restSec,
                "notes" to it.notes,
            )
        },
    )

    private fun parseRoutine(id: String, d: Map<String, Any?>): Pair<RoutineEntity, List<RoutineExerciseEntity>>? {
        val name = d.str("name").trim().take(60)
        if (name.isBlank()) return null
        val items = (d["items"] as? List<*>).orEmpty().mapIndexedNotNull { index, raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: return@mapIndexedNotNull null
            val exerciseId = m.str("exerciseId")
            if (exerciseId.isBlank()) return@mapIndexedNotNull null
            RoutineExerciseEntity(
                routineId = id,
                exerciseId = exerciseId,
                position = index,
                sets = m.int("sets").coerceIn(1, 20),
                reps = m.int("reps").coerceIn(1, 100),
                weightKg = m.dbl("weightKg").coerceIn(0.0, 2000.0),
                restSec = m.int("restSec").coerceIn(0, 600),
                notes = m.str("notes").take(200),
            )
        }
        if (items.isEmpty()) return null
        val routine = RoutineEntity(
            id = id,
            name = name,
            description = d.str("description").take(200),
            templateKey = d["templateKey"] as? String,
            scheduledDays = d.int("scheduledDays") and 127,
            createdAt = d.long("createdAt"),
            updatedAt = d.long("updatedAt"),
            lastUsedAt = (d["lastUsedAt"] as? Number)?.toLong(),
        )
        return routine to items
    }

    private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""
    private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
    private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
    private fun Map<String, Any?>.dbl(key: String): Double = (this[key] as? Number)?.toDouble() ?: 0.0

    private companion object {
        const val KEY_LAST = "last_sync_at"
    }
}
