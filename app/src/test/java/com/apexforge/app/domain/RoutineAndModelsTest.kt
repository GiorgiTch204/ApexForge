package com.apexforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineAndModelsTest {
    private val ok = listOf(RoutineValidator.Item(3, 10, 90))

    @Test fun validRoutinePasses() = assertNull(RoutineValidator.validate("Push", ok))

    @Test fun blankNameRejected() = assertNotNull(RoutineValidator.validate("   ", ok))

    @Test fun overlongNameRejected() = assertNotNull(RoutineValidator.validate("x".repeat(61), ok))

    @Test fun emptyRoutineRejected() = assertNotNull(RoutineValidator.validate("Push", emptyList()))

    @Test fun outOfRangeValuesRejectedWithPosition() {
        assertTrue(RoutineValidator.validate("A", listOf(RoutineValidator.Item(0, 10, 60)))!!.contains("Exercise 1"))
        assertTrue(RoutineValidator.validate("A", ok + RoutineValidator.Item(3, 101, 60))!!.contains("Exercise 2"))
        assertNotNull(RoutineValidator.validate("A", listOf(RoutineValidator.Item(3, 10, 601))))
        assertNotNull(RoutineValidator.validate("A", listOf(RoutineValidator.Item(21, 10, 60))))
    }

    @Test fun slugifyProducesStableIds() {
        assertEquals("barbell-bench-press", slugify("Barbell Bench Press"))
        assertEquals("t-bar-row", slugify("T-Bar Row"))
        assertEquals("90-90-hip-switch", slugify("  90/90 Hip Switch! "))
    }

    private fun ex(id: String, muscle: String, cat: Category, eq: Equipment, diff: Difficulty = Difficulty.BEGINNER) =
        Exercise(id, id, cat, eq, diff, muscle, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, false)

    @Test fun alternativesExcludeSelfAndPreferSameMuscleDifferentEquipment() {
        val target = ex("bench", "Chest", Category.CHEST, Equipment.BARBELL)
        val all = listOf(
            target,
            ex("db-press", "Chest", Category.CHEST, Equipment.DUMBBELL),
            ex("bb-incline", "Chest", Category.CHEST, Equipment.BARBELL),
            ex("curl", "Biceps", Category.BICEPS, Equipment.DUMBBELL),
        )
        val alts = Alternatives.forExercise(target, all)
        assertTrue(alts.none { it.id == "bench" })
        assertTrue(alts.none { it.id == "curl" })
        assertEquals("db-press", alts.first().id)
    }

    @Test fun alternativesAreLimited() {
        val target = ex("t", "Chest", Category.CHEST, Equipment.BARBELL)
        val all = listOf(target) + (1..20).map { ex("e$it", "Chest", Category.CHEST, Equipment.CABLE) }
        assertEquals(6, Alternatives.forExercise(target, all).size)
        assertEquals(3, Alternatives.forExercise(target, all, 3).size)
    }

    @Test fun safetyTipsAlwaysIncludeMedicalNote() {
        for (eq in Equipment.entries) for (cat in Category.entries) {
            val tips = SafetyTips.forExercise(cat, eq, Difficulty.ADVANCED)
            assertTrue(tips.any { it.contains("medical advice") })
            assertTrue(tips.any { it.contains("advanced") })
        }
    }
}
