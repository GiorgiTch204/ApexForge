package com.apexforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalcTest {
    @Test fun setVolumeIsWeightTimesReps() = assertEquals(400.0, Calc.setVolume(80.0, 5), 1e-9)

    @Test fun setWithoutLoadOrRepsHasNoVolume() {
        assertEquals(0.0, Calc.setVolume(0.0, 10), 1e-9)
        assertEquals(0.0, Calc.setVolume(50.0, 0), 1e-9)
    }

    @Test fun trainingVolumeCountsOnlyCompletedSets() {
        val sets = listOf(
            SetValue(100.0, 5, true),
            SetValue(100.0, 5, true),
            SetValue(100.0, 5, false),
        )
        assertEquals(1000.0, Calc.trainingVolume(sets), 1e-9)
        assertEquals(2, Calc.completedSets(sets))
        assertEquals(10, Calc.completedReps(sets))
    }

    @Test fun epleyOneRepMax() {
        assertEquals(100.0, Calc.estimateOneRepMax(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 5 / 30.0), Calc.estimateOneRepMax(100.0, 5), 1e-9)
        assertEquals(0.0, Calc.estimateOneRepMax(0.0, 5), 1e-9)
        assertEquals(0.0, Calc.estimateOneRepMax(100.0, 0), 1e-9)
    }

    private val today = LocalDate.of(2026, 3, 10)

    @Test fun streakEndingTodayCountsToday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, Calc.currentStreak(days, today))
    }

    @Test fun streakMayEndYesterday() {
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, Calc.currentStreak(days, today))
    }

    @Test fun streakBrokenByGap() {
        assertEquals(0, Calc.currentStreak(setOf(today.minusDays(2)), today))
        assertEquals(0, Calc.currentStreak(emptySet(), today))
    }

    @Test fun longestStreakFindsBestRun() {
        val days = setOf(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 12),
        )
        assertEquals(3, Calc.longestStreak(days))
        assertEquals(0, Calc.longestStreak(emptySet()))
        assertEquals(1, Calc.longestStreak(setOf(today)))
    }
}
