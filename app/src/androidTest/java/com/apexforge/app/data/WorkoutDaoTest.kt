package com.apexforge.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apexforge.app.data.local.ApexDatabase
import com.apexforge.app.data.local.SessionStatus
import com.apexforge.app.data.local.WorkoutDao
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.local.WorkoutSetEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a file-backed database (not in-memory) so persistence across close and reopen is real. */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "workout-dao-test.db"
    private lateinit var db: ApexDatabase
    private lateinit var dao: WorkoutDao

    private fun open(): ApexDatabase = Room.databaseBuilder(context, ApexDatabase::class.java, dbName).build()

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        db = open()
        dao = db.workoutDao()
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun session(id: String) = WorkoutSessionEntity(id, null, "Workout $id", 1_000L, null, SessionStatus.ACTIVE, "")

    private fun set(sessionId: String, exercise: String, number: Int, completed: Boolean, reps: Int = 5) =
        WorkoutSetEntity(sessionId = sessionId, exerciseId = exercise, exerciseOrder = 0, setNumber = number,
            weightKg = 50.0, reps = reps, completed = completed, rpe = null, note = "", restSec = 90, completedAt = null)

    @Test fun onlyOneSessionCanBeActive() = runBlocking {
        val first = dao.startSessionIfNone(session("a"), emptyList())
        val second = dao.startSessionIfNone(session("b"), emptyList())
        assertEquals("a", first)
        assertEquals("a", second)
        assertNull(dao.getSession("b"))
    }

    @Test fun finishDropsIncompleteSetsAndMarksCompleted() = runBlocking {
        dao.startSessionIfNone(session("a"), listOf(set("a", "bench", 1, true), set("a", "bench", 2, false)))
        assertTrue(dao.finish("a", 5_000L))
        val sets = dao.getSets("a")
        assertEquals(1, sets.size)
        assertTrue(sets.single().completed)
        val finished = dao.getSession("a")!!
        assertEquals(SessionStatus.COMPLETED, finished.status)
        assertEquals(5_000L, finished.endedAt)
        assertNull(dao.getActiveSession())
    }

    @Test fun finishRefusesWhenNothingWasCompleted() = runBlocking {
        dao.startSessionIfNone(session("a"), listOf(set("a", "bench", 1, false)))
        assertFalse(dao.finish("a", 5_000L))
        assertEquals(SessionStatus.ACTIVE, dao.getSession("a")!!.status)
        assertEquals(1, dao.getSets("a").size)
    }

    @Test fun deletingASetRenumbersTheRest() = runBlocking {
        dao.startSessionIfNone(session("a"), listOf(set("a", "bench", 1, true), set("a", "bench", 2, true), set("a", "bench", 3, true)))
        val middle = dao.getSets("a").first { it.setNumber == 2 }
        dao.deleteSetAndRenumber(middle.id)
        assertEquals(listOf(1, 2), dao.getSets("a").map { it.setNumber }.sorted())
    }

    @Test fun activeSessionSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        dao.startSessionIfNone(session("a"), listOf(set("a", "bench", 1, true)))
        db.close()
        db = open()
        dao = db.workoutDao()
        val restored = dao.getActiveSession()
        assertNotNull(restored)
        assertEquals("a", restored!!.id)
        assertEquals(1, dao.getSets("a").size)
    }

    @Test fun deletingASessionCascadesToItsSets() = runBlocking {
        dao.startSessionIfNone(session("a"), listOf(set("a", "bench", 1, true)))
        dao.deleteSession("a")
        assertTrue(dao.getSets("a").isEmpty())
    }
}
