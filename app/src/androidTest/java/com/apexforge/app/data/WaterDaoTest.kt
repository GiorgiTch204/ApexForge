package com.apexforge.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apexforge.app.data.local.ApexDatabase
import com.apexforge.app.data.local.WaterEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterDaoTest {
    private lateinit var db: ApexDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ApexDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private fun entry(date: String, ml: Int) = WaterEntryEntity(date = date, amountMl = ml, timestamp = 1L)

    @Test fun dayQueryReturnsOnlyThatDay() = runBlocking {
        val dao = db.waterDao()
        dao.insert(entry("2026-03-10", 250))
        dao.insert(entry("2026-03-10", 500))
        dao.insert(entry("2026-03-11", 300))
        assertEquals(750, dao.observeDay("2026-03-10").first().sumOf { it.amountMl })
    }

    @Test fun totalsAreGroupedPerDay() = runBlocking {
        val dao = db.waterDao()
        dao.insert(entry("2026-03-10", 250))
        dao.insert(entry("2026-03-10", 500))
        dao.insert(entry("2026-03-11", 300))
        val totals = dao.observeTotalsSince("2026-03-10").first().associate { it.date to it.totalMl }
        assertEquals(mapOf("2026-03-10" to 750, "2026-03-11" to 300), totals)
    }

    @Test fun editDeleteAndRestore() = runBlocking {
        val dao = db.waterDao()
        val id = dao.insert(entry("2026-03-10", 250))
        dao.updateAmount(id, 400)
        assertEquals(400, dao.getById(id)!!.amountMl)
        val saved = dao.getById(id)!!
        dao.delete(id)
        assertNull(dao.getById(id))
        dao.restore(saved)
        assertEquals(400, dao.getById(id)!!.amountMl)
    }
}
