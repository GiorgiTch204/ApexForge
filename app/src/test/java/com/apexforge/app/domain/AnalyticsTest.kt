package com.apexforge.app.domain

import com.apexforge.app.data.local.LoggedSet
import com.apexforge.app.data.local.SessionStatus
import com.apexforge.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsTest {
    private fun set(session: String, time: Long, exercise: String, kg: Double, reps: Int, n: Int = 1) =
        LoggedSet(setId = (time + n), sessionId = session, startedAt = time, endedAt = time, exerciseId = exercise,
            setNumber = n, weightKg = kg, reps = reps, rpe = null)

    @Test fun firstEverLogIsABaselineNotAPr() {
        val hits = Analytics.computePRHits(listOf(set("a", 1000, "bench", 80.0, 5)))
        assertTrue(hits.isEmpty())
    }

    @Test fun heavierSecondSessionIsAPrForWeightAndE1rm() {
        val hits = Analytics.computePRHits(
            listOf(set("a", 1000, "bench", 80.0, 5), set("b", 2000, "bench", 85.0, 5)),
        )
        val types = hits.map { it.type }.toSet()
        assertTrue(RecordType.MAX_WEIGHT in types)
        assertTrue(RecordType.BEST_E1RM in types)
        val weight = hits.first { it.type == RecordType.MAX_WEIGHT }
        assertEquals(85.0, weight.value, 1e-9)
        assertEquals(80.0, weight.previous, 1e-9)
        assertEquals("b", weight.sessionId)
    }

    @Test fun equalPerformanceIsNotAPr() {
        val hits = Analytics.computePRHits(
            listOf(set("a", 1000, "squat", 100.0, 5), set("b", 2000, "squat", 100.0, 5)),
        )
        assertTrue(hits.isEmpty())
    }

    @Test fun lighterSessionDoesNotResetTheBest() {
        val hits = Analytics.computePRHits(
            listOf(
                set("a", 1000, "squat", 100.0, 5),
                set("b", 2000, "squat", 60.0, 5),
                set("c", 3000, "squat", 90.0, 5),
            ),
        )
        assertTrue(hits.none { it.type == RecordType.MAX_WEIGHT })
    }

    @Test fun exercisesAreTrackedIndependently() {
        val hits = Analytics.computePRHits(
            listOf(set("a", 1000, "bench", 80.0, 5), set("b", 2000, "squat", 200.0, 5)),
        )
        assertTrue(hits.isEmpty())
    }

    @Test fun bodyweightRepsRecordIsDetected() {
        val hits = Analytics.computePRHits(
            listOf(set("a", 1000, "pullup", 0.0, 8), set("b", 2000, "pullup", 0.0, 10)),
        )
        val reps = hits.single { it.type == RecordType.MAX_REPS }
        assertEquals(10.0, reps.value, 1e-9)
    }

    @Test fun bestsSummariseAnExercise() {
        val bests = Analytics.bestsFor(listOf(set("a", 1, "bench", 80.0, 5), set("a", 1, "bench", 90.0, 3, 2)))
        assertEquals(90.0, bests.maxWeightKg, 1e-9)
    }

    @Test fun exercisePointsGroupBySessionInTimeOrder() {
        val points = Analytics.exercisePoints(
            listOf(set("b", 2000, "bench", 90.0, 5), set("a", 1000, "bench", 80.0, 5), set("a", 1000, "bench", 70.0, 10, 2)),
        )
        assertEquals(listOf("a", "b"), points.map { it.sessionId })
        assertEquals(80.0, points[0].topWeightKg, 1e-9)
        assertEquals(2, points[0].setCount)
        assertEquals(15, points[0].totalReps)
    }

    @Test fun snapshotTotalsAndStreak() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 3, 10)
        fun ms(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val s1 = WorkoutSessionEntity("a", null, "A", ms(today.minusDays(1)) - 1000, ms(today.minusDays(1)), SessionStatus.COMPLETED, "")
        val s2 = WorkoutSessionEntity("b", null, "B", ms(today) - 1000, ms(today), SessionStatus.COMPLETED, "")
        val sets = listOf(
            set("a", ms(today.minusDays(1)), "bench", 100.0, 5),
            set("b", ms(today), "bench", 100.0, 6),
        )
        val snap = Analytics.buildSnapshot(listOf(s1, s2), sets, mapOf("bench" to "Bench Press"), today, zone)
        assertEquals(2, snap.totalWorkouts)
        assertEquals(2, snap.currentStreak)
        assertEquals(1100.0, snap.totalVolumeKg, 1e-9)
        assertEquals("Bench Press", snap.exerciseName("bench"))
        assertEquals("Exercise", snap.exerciseName("missing"))
        assertEquals(1, snap.sessions.first { it.id == "b" }.prCount)
    }

    @Test fun activeSessionsAreExcludedFromStats() {
        val zone = ZoneId.of("UTC")
        val active = WorkoutSessionEntity("x", null, "X", 1000, null, SessionStatus.ACTIVE, "")
        val snap = Analytics.buildSnapshot(listOf(active), emptyList(), emptyMap(), LocalDate.of(2026, 3, 10), zone)
        assertEquals(0, snap.totalWorkouts)
    }

    @Test fun percentChangeHandlesNoBaseline() {
        assertNull(Analytics.percentChange(10.0, 0.0))
        assertEquals(50.0, Analytics.percentChange(150.0, 100.0)!!, 1e-9)
        assertEquals(-25.0, Analytics.percentChange(75.0, 100.0)!!, 1e-9)
    }

    @Test fun weeklyBucketsIncludeEmptyWeeks() {
        val zone = ZoneId.of("UTC")
        fun summary(id: String, d: LocalDate): SessionSummary {
            val t = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            return SessionSummary(id, id, t - 3_600_000, t, emptyList(), 3, 30, 1000.0, 0)
        }
        val from = LocalDate.of(2026, 3, 2) // Monday
        val buckets = Analytics.weeklyBuckets(
            listOf(summary("a", from), summary("b", from.plusWeeks(2))), from, from.plusWeeks(2).plusDays(3), zone,
        )
        assertEquals(listOf(1, 0, 1), buckets.map { it.workouts })
    }

    @Test fun rangeStartUsesEarliestForAll() {
        val today = LocalDate.of(2026, 3, 10)
        assertEquals(today.minusDays(27), Analytics.rangeStart(RangeOption.W4, today, null))
        assertEquals(LocalDate.of(2025, 1, 1), Analytics.rangeStart(RangeOption.ALL, today, LocalDate.of(2025, 1, 1)))
    }
}
