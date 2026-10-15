package com.apexforge.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** A single logged set as the calculators see it. */
data class SetValue(val weightKg: Double, val reps: Int, val completed: Boolean)

object Calc {
    /** Volume of one set. Sets without load or reps contribute nothing. */
    fun setVolume(weightKg: Double, reps: Int): Double =
        if (weightKg <= 0.0 || reps <= 0) 0.0 else weightKg * reps

    /** Training volume = sum of weight x reps over COMPLETED sets only. */
    fun trainingVolume(sets: List<SetValue>): Double =
        sets.filter { it.completed }.sumOf { setVolume(it.weightKg, it.reps) }

    fun completedSets(sets: List<SetValue>): Int = sets.count { it.completed }

    fun completedReps(sets: List<SetValue>): Int = sets.filter { it.completed }.sumOf { it.reps.coerceAtLeast(0) }

    /**
     * Epley estimate of a one-rep max. A single rep is returned as-is.
     * Estimates become unreliable above ~12 reps, so treat them as a trend, not a test result.
     */
    fun estimateOneRepMax(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        return if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)
    }

    /** Consecutive days with a workout, ending today (or yesterday if today has none yet). */
    fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun longestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i - 1].plusDays(1) == sorted[i]) {
                run++
                if (run > best) best = run
            } else {
                run = 1
            }
        }
        return best
    }
}

object WaterCalc {
    fun total(entriesMl: List<Int>): Int = entriesMl.sumOf { it.coerceAtLeast(0) }

    /** 0..1 for progress indicators; never exceeds 1 so the animation stays inside its container. */
    fun progress(totalMl: Int, goalMl: Int): Float =
        if (goalMl <= 0) 0f else (totalMl.toFloat() / goalMl).coerceIn(0f, 1f)

    /** Can exceed 100 when the goal is surpassed. */
    fun percent(totalMl: Int, goalMl: Int): Int =
        if (goalMl <= 0) 0 else (totalMl * 100f / goalMl).roundToInt()

    fun remaining(totalMl: Int, goalMl: Int): Int = (goalMl - totalMl).coerceAtLeast(0)

    /** The local calendar day an instant belongs to. Daily totals reset when this value changes. */
    fun dayKey(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}

/** Validation for the routine editor, kept pure so it can be unit tested. */
object RoutineValidator {
    data class Item(val sets: Int, val reps: Int, val restSec: Int)

    fun validate(name: String, items: List<Item>): String? {
        if (name.isBlank()) return "Give your routine a name."
        if (name.trim().length > 60) return "Routine names can be at most 60 characters."
        if (items.isEmpty()) return "Add at least one exercise."
        items.forEachIndexed { index, item ->
            val n = index + 1
            if (item.sets !in 1..20) return "Exercise $n: sets must be between 1 and 20."
            if (item.reps !in 1..100) return "Exercise $n: reps must be between 1 and 100."
            if (item.restSec !in 0..600) return "Exercise $n: rest must be between 0 and 600 seconds."
        }
        return null
    }
}

/**
 * Rest timer without any Android dependency. [now] is a monotonic clock in milliseconds
 * (SystemClock.elapsedRealtime in the app, a fake in tests).
 */
class RestTimer(private val now: () -> Long) {
    var totalMs: Long = 0L
        private set
    private var endsAt: Long = 0L
    private var pausedRemaining: Long? = null
    private var active = false

    val isActive: Boolean get() = active
    val isPaused: Boolean get() = pausedRemaining != null

    fun start(seconds: Int) {
        totalMs = seconds.coerceAtLeast(1) * 1000L
        endsAt = now() + totalMs
        pausedRemaining = null
        active = true
    }

    fun remainingMs(): Long {
        if (!active) return 0L
        pausedRemaining?.let { return it }
        return (endsAt - now()).coerceAtLeast(0L)
    }

    val isFinished: Boolean get() = active && remainingMs() <= 0L

    fun pause() {
        if (active && pausedRemaining == null) pausedRemaining = remainingMs()
    }

    fun resume() {
        val remaining = pausedRemaining ?: return
        endsAt = now() + remaining
        pausedRemaining = null
    }

    /** Restart the countdown from the original duration. */
    fun reset() {
        if (!active) return
        endsAt = now() + totalMs
        pausedRemaining = null
    }

    /** Add or remove time, clamped so the timer never goes negative. */
    fun adjust(deltaSec: Int) {
        if (!active) return
        val delta = deltaSec * 1000L
        totalMs = (totalMs + delta).coerceAtLeast(1000L)
        val paused = pausedRemaining
        if (paused != null) pausedRemaining = (paused + delta).coerceAtLeast(0L)
        else endsAt = (endsAt + delta).coerceAtLeast(now())
    }

    fun stop() {
        active = false
        pausedRemaining = null
    }
}
