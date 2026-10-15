package com.apexforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UnitsAndWaterTest {
    @Test fun kgToLbAndBackRoundTrips() {
        val kg = 82.5
        val back = UnitSystem.IMPERIAL.toKg(UnitSystem.IMPERIAL.fromKg(kg))
        assertEquals(kg, back, 1e-9)
    }

    @Test fun metricIsIdentity() {
        assertEquals(100.0, UnitSystem.METRIC.fromKg(100.0), 1e-12)
        assertEquals(100.0, UnitSystem.METRIC.toKg(100.0), 1e-12)
    }

    @Test fun poundsConvertToKnownKilograms() {
        assertEquals(45.359237, UnitSystem.IMPERIAL.toKg(100.0), 1e-6)
        assertEquals(220.5, UnitSystem.IMPERIAL.displayWeight(100.0), 1e-9)
    }

    @Test fun displayWeightRoundsToOneDecimal() = assertEquals(82.5, UnitSystem.METRIC.displayWeight(82.4999), 1e-9)

    @Test fun waterUnitRoundTripStaysWithinOneMl() {
        for (ml in listOf(150, 250, 355, 500, 2500)) {
            val back = WaterUnit.FL_OZ.toMl(WaterUnit.FL_OZ.fromMl(ml))
            assertTrue("ml=$ml back=$back", kotlin.math.abs(back - ml) <= 1)
        }
        assertEquals(250, WaterUnit.ML.toMl(WaterUnit.ML.fromMl(250)))
    }

    @Test fun heightConversions() {
        assertEquals(180.34, inchesToCm(71.0), 1e-9)
        assertEquals(71.0, cmToInches(180.34), 1e-9)
    }

    @Test fun waterProgressIsCappedButPercentIsNot() {
        assertEquals(0.5f, WaterCalc.progress(1250, 2500), 1e-6f)
        assertEquals(1f, WaterCalc.progress(4000, 2500), 1e-6f)
        assertEquals(160, WaterCalc.percent(4000, 2500))
        assertEquals(0f, WaterCalc.progress(100, 0), 1e-6f)
    }

    @Test fun waterRemainingNeverNegative() {
        assertEquals(1000, WaterCalc.remaining(1500, 2500))
        assertEquals(0, WaterCalc.remaining(3000, 2500))
    }

    @Test fun waterTotalIgnoresNegativeEntries() = assertEquals(300, WaterCalc.total(listOf(100, 200, -50)))

    @Test fun dayKeyRollsOverAtLocalMidnight() {
        val zone = ZoneId.of("Asia/Tbilisi") // UTC+4
        val justBefore = LocalDate.of(2026, 3, 10).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val justAfter = LocalDate.of(2026, 3, 11).atTime(0, 0, 1).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 3, 10), WaterCalc.dayKey(justBefore, zone))
        assertEquals(LocalDate.of(2026, 3, 11), WaterCalc.dayKey(justAfter, zone))
    }

    @Test fun dayKeyDependsOnZoneNotOnUtc() {
        val instant = LocalDate.of(2026, 3, 10).atTime(22, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 3, 10), WaterCalc.dayKey(instant, ZoneId.of("UTC")))
        assertEquals(LocalDate.of(2026, 3, 11), WaterCalc.dayKey(instant, ZoneId.of("Asia/Tbilisi")))
    }
}
