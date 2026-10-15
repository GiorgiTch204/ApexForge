package com.apexforge.app.domain

import com.apexforge.app.data.local.LoggedSet
import com.apexforge.app.data.local.WorkoutSessionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class PRHit(
    val sessionId: String,
    val exerciseId: String,
    val type: RecordType,
    val value: Double,
    val previous: Double,
    val achievedAt: Long,
    val weightKg: Double,
    val reps: Int,
)

data class SessionSummary(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val exerciseNames: List<String>,
    val sets: Int,
    val reps: Int,
    val volumeKg: Double,
    val prCount: Int,
) {
    val durationSec: Long get() = ((endedAt - startedAt) / 1000L).coerceAtLeast(0L)
    fun day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(endedAt).atZone(zone).toLocalDate()
}

data class StatsSnapshot(
    val sessions: List<SessionSummary>,
    val loggedSets: List<LoggedSet>,
    val prHits: List<PRHit>,
    val exerciseNames: Map<String, String>,
    val totalWorkouts: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolumeKg: Double,
    val currentStreak: Int,
    val longestStreak: Int,
    val workoutDays: Set<LocalDate>,
) {
    fun exerciseName(id: String): String = exerciseNames[id] ?: "Exercise"

    companion object {
        val EMPTY = StatsSnapshot(emptyList(), emptyList(), emptyList(), emptyMap(), 0, 0, 0, 0.0, 0, 0, emptySet())
    }
}

data class ChartPoint(val label: String, val value: Double)

data class ExerciseBests(
    val maxWeightKg: Double,
    val bestE1rmKg: Double,
    val bestSetVolumeKg: Double,
    val maxBodyweightReps: Int,
)

/** Per-session aggregate for one exercise; drives the progression chart and history table. */
data class ExercisePoint(
    val sessionId: String,
    val time: Long,
    val topWeightKg: Double,
    val topReps: Int,
    val bestE1rmKg: Double,
    val volumeKg: Double,
    val totalReps: Int,
    val setCount: Int,
)

data class WeekBucket(
    val weekStart: LocalDate,
    val workouts: Int,
    val volumeKg: Double,
    val sets: Int,
    val avgDurationMin: Double,
)

data class PeriodStats(val workouts: Int, val sets: Int, val volumeKg: Double, val avgDurationMin: Double)

object Analytics {

    private fun metric(type: RecordType, set: LoggedSet): Double = when (type) {
        RecordType.MAX_WEIGHT -> if (set.reps >= 1) set.weightKg else 0.0
        RecordType.BEST_E1RM -> Calc.estimateOneRepMax(set.weightKg, set.reps)
        RecordType.BEST_SET_VOLUME -> Calc.setVolume(set.weightKg, set.reps)
        // Reps only count as a record for unloaded (bodyweight) sets.
        RecordType.MAX_REPS -> if (set.weightKg <= 0.0) set.reps.toDouble() else 0.0
    }

    /**
     * Walks sessions in time order and reports every moment a session beat the previous best for an
     * exercise. The first time an exercise is ever logged sets the baseline and is not a PR.
     * Weights are compared in kg, so switching display units never creates or hides a record.
     */
    fun computePRHits(sets: List<LoggedSet>): List<PRHit> {
        val sessions = sets.groupBy { it.sessionId }.values.sortedBy { group -> group.first().endedAt }
        val best = mutableMapOf<Pair<String, RecordType>, Double>()
        val hits = mutableListOf<PRHit>()
        for (session in sessions) {
            val pending = mutableMapOf<Pair<String, RecordType>, Double>()
            for ((exerciseId, exerciseSets) in session.groupBy { it.exerciseId }) {
                for (type in RecordType.entries) {
                    val top = exerciseSets.maxByOrNull { metric(type, it) } ?: continue
                    val value = metric(type, top)
                    if (value <= 0.0) continue
                    val key = exerciseId to type
                    val previous = best[key]
                    if (previous != null && value > previous + 1e-9) {
                        hits += PRHit(
                            sessionId = top.sessionId,
                            exerciseId = exerciseId,
                            type = type,
                            value = value,
                            previous = previous,
                            achievedAt = top.endedAt,
                            weightKg = top.weightKg,
                            reps = top.reps,
                        )
                    }
                    if (previous == null || value > previous) pending[key] = value
                }
            }
            best.putAll(pending)
        }
        return hits.sortedByDescending { it.achievedAt }
    }

    fun bestsFor(sets: List<LoggedSet>): ExerciseBests = ExerciseBests(
        maxWeightKg = sets.maxOfOrNull { metric(RecordType.MAX_WEIGHT, it) } ?: 0.0,
        bestE1rmKg = sets.maxOfOrNull { metric(RecordType.BEST_E1RM, it) } ?: 0.0,
        bestSetVolumeKg = sets.maxOfOrNull { metric(RecordType.BEST_SET_VOLUME, it) } ?: 0.0,
        maxBodyweightReps = sets.maxOfOrNull { metric(RecordType.MAX_REPS, it).toInt() } ?: 0,
    )

    fun exercisePoints(sets: List<LoggedSet>): List<ExercisePoint> =
        sets.groupBy { it.sessionId }.values.map { group ->
            val top = group.maxWithOrNull(compareBy<LoggedSet> { it.weightKg }.thenBy { it.reps })!!
            ExercisePoint(
                sessionId = top.sessionId,
                time = top.endedAt,
                topWeightKg = top.weightKg,
                topReps = top.reps,
                bestE1rmKg = group.maxOf { Calc.estimateOneRepMax(it.weightKg, it.reps) },
                volumeKg = group.sumOf { Calc.setVolume(it.weightKg, it.reps) },
                totalReps = group.sumOf { it.reps },
                setCount = group.size,
            )
        }.sortedBy { it.time }

    fun buildSnapshot(
        sessions: List<WorkoutSessionEntity>,
        sets: List<LoggedSet>,
        exerciseNames: Map<String, String>,
        today: LocalDate,
        zone: ZoneId,
    ): StatsSnapshot {
        val hits = computePRHits(sets)
        val hitsBySession = hits.groupBy { it.sessionId }
        val setsBySession = sets.groupBy { it.sessionId }
        val summaries = sessions
            .filter { it.endedAt != null }
            .map { session ->
                val sessionSets = setsBySession[session.id].orEmpty()
                SessionSummary(
                    id = session.id,
                    name = session.name,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt ?: session.startedAt,
                    exerciseNames = sessionSets.map { it.exerciseId }.distinct().map { exerciseNames[it] ?: "Exercise" },
                    sets = sessionSets.size,
                    reps = sessionSets.sumOf { it.reps },
                    volumeKg = sessionSets.sumOf { Calc.setVolume(it.weightKg, it.reps) },
                    prCount = hitsBySession[session.id].orEmpty().map { it.exerciseId }.distinct().size,
                )
            }
            .sortedByDescending { it.endedAt }
        val days = summaries.map { it.day(zone) }.toSet()
        return StatsSnapshot(
            sessions = summaries,
            loggedSets = sets,
            prHits = hits,
            exerciseNames = exerciseNames,
            totalWorkouts = summaries.size,
            totalSets = summaries.sumOf { it.sets },
            totalReps = summaries.sumOf { it.reps },
            totalVolumeKg = summaries.sumOf { it.volumeKg },
            currentStreak = Calc.currentStreak(days, today),
            longestStreak = Calc.longestStreak(days),
            workoutDays = days,
        )
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** One bucket per week between [from] and [to], including empty weeks so charts show real gaps. */
    fun weeklyBuckets(sessions: List<SessionSummary>, from: LocalDate, to: LocalDate, zone: ZoneId): List<WeekBucket> {
        val grouped = sessions.groupBy { weekStart(it.day(zone)) }
        val buckets = mutableListOf<WeekBucket>()
        var cursor = weekStart(from)
        val last = weekStart(to)
        while (!cursor.isAfter(last)) {
            val inWeek = grouped[cursor].orEmpty()
            buckets += WeekBucket(
                weekStart = cursor,
                workouts = inWeek.size,
                volumeKg = inWeek.sumOf { it.volumeKg },
                sets = inWeek.sumOf { it.sets },
                avgDurationMin = if (inWeek.isEmpty()) 0.0 else inWeek.map { it.durationSec }.average() / 60.0,
            )
            cursor = cursor.plusWeeks(1)
        }
        return buckets
    }

    fun periodStats(sessions: List<SessionSummary>, from: LocalDate, to: LocalDate, zone: ZoneId): PeriodStats {
        val inRange = sessions.filter { val d = it.day(zone); !d.isBefore(from) && !d.isAfter(to) }
        return PeriodStats(
            workouts = inRange.size,
            sets = inRange.sumOf { it.sets },
            volumeKg = inRange.sumOf { it.volumeKg },
            avgDurationMin = if (inRange.isEmpty()) 0.0 else inRange.map { it.durationSec }.average() / 60.0,
        )
    }

    /** Percentage change from [previous] to [current]; null when there is no baseline to compare with. */
    fun percentChange(current: Double, previous: Double): Double? =
        if (previous <= 0.0) null else (current - previous) / previous * 100.0

    /** Start date for a range option, or the earliest session date for "All". */
    fun rangeStart(option: RangeOption, today: LocalDate, earliest: LocalDate?): LocalDate = when {
        option.days != null -> today.minusDays(option.days.toLong() - 1)
        earliest != null -> earliest
        else -> today.minusDays(27)
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to) + 1
}
