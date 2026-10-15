package com.apexforge.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.WaterUnit
import com.apexforge.app.domain.displayWeight
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.fromMl
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}

fun formatMinutes(totalSeconds: Long): String {
    val minutes = (totalSeconds / 60).toInt()
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60}h ${String.format(Locale.US, "%02d", minutes % 60)}m"
    }
}

/** Number without unit, no trailing ".0". */
fun formatWeight(kg: Double, units: UnitSystem): String {
    val v = units.displayWeight(kg)
    return if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
}

fun formatWeightWithUnit(kg: Double, units: UnitSystem): String = "${formatWeight(kg, units)} ${units.weightUnit}"

/** Large volumes get thousands separators and no decimals. */
fun formatVolume(kg: Double, units: UnitSystem): String =
    NumberFormat.getIntegerInstance().format(units.fromKg(kg).roundToInt())

fun formatWater(ml: Int, unit: WaterUnit): String {
    val v = unit.fromMl(ml)
    return if (unit == WaterUnit.ML) NumberFormat.getIntegerInstance().format(v.roundToInt())
    else String.format(Locale.US, "%.1f", v)
}

fun formatDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(epochMs).atZone(zone))

fun formatDateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(epochMs).atZone(zone))

fun formatDay(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

fun shortDay(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date)

fun greetingFor(hour: Int): String = when {
    hour < 5 -> "Good evening"
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    else -> "Good evening"
}

/** Seconds since [startedAt], ticking once per second while composed. */
@Composable
fun rememberElapsedSeconds(startedAt: Long): State<Long> =
    produceState(initialValue = (System.currentTimeMillis() - startedAt) / 1000, startedAt) {
        while (true) {
            value = (System.currentTimeMillis() - startedAt) / 1000
            delay(1000)
        }
    }

/** Today's local date, refreshed so screens roll over at midnight without being reopened. */
@Composable
fun rememberToday(): State<LocalDate> =
    produceState(initialValue = LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "A"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
    }
}
