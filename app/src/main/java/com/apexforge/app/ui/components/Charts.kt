package com.apexforge.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apexforge.app.domain.ChartPoint
import com.apexforge.app.ui.theme.apex
import kotlin.math.roundToInt

data class ChartBar(val label: String, val value: Double, val highlight: Boolean = false)

@Composable
private fun ChartHint(text: String, height: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Smooth line chart with tap / drag selection. Draws only real values: with fewer than two points it
 * shows an honest empty state instead of inventing a trend.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    valueFormat: (Double) -> String = { String.format(java.util.Locale.US, "%.1f", it) },
    emptyText: String = "Log a couple more sessions to see your trend.",
) {
    if (points.isEmpty()) {
        ChartHint(emptyText, height)
        return
    }
    if (points.size == 1) {
        Column(modifier) {
            Text(points[0].label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueFormat(points[0].value), style = MaterialTheme.typography.headlineSmall)
            ChartHint(emptyText, height - 40.dp)
        }
        return
    }

    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val grid = MaterialTheme.apex.chartGrid
    val n = points.size
    val safeSelected = selected.coerceIn(0, n - 1)

    Column(
        modifier = modifier.semantics {
            contentDescription = "Line chart with $n points from ${points.first().label} to ${points.last().label}. " +
                "Selected ${points[safeSelected].label}: ${valueFormat(points[safeSelected].value)}"
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(valueFormat(points[safeSelected].value), style = MaterialTheme.typography.headlineSmall)
            Text(points[safeSelected].label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val pad = 12.dp.toPx()
                        val ratio = (offset.x - pad) / (size.width - 2 * pad)
                        selected = (ratio * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        val pad = 12.dp.toPx()
                        val ratio = (change.position.x - pad) / (size.width - 2 * pad)
                        selected = (ratio * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val padX = 12.dp.toPx()
            val padY = 10.dp.toPx()
            val minV = points.minOf { it.value }
            val maxV = points.maxOf { it.value }
            val flat = maxV - minV < 1e-9
            val span = if (flat) 1.0 else maxV - minV

            fun xAt(i: Int) = padX + (w - 2 * padX) * i / (n - 1)
            fun yAt(v: Double): Float =
                if (flat) h / 2f else h - padY - ((v - minV) / span).toFloat() * (h - 2 * padY)

            for (g in 0..2) {
                val y = padY + (h - 2 * padY) * g / 2f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            fun buildPath(closeToBottom: Boolean): Path = Path().apply {
                moveTo(xAt(0), yAt(points[0].value))
                for (i in 1 until n) {
                    val cx = (xAt(i - 1) + xAt(i)) / 2f
                    cubicTo(cx, yAt(points[i - 1].value), cx, yAt(points[i].value), xAt(i), yAt(points[i].value))
                }
                if (closeToBottom) {
                    lineTo(xAt(n - 1), h)
                    lineTo(xAt(0), h)
                    close()
                }
            }

            clipRect(left = 0f, top = 0f, right = w * progress.value, bottom = h) {
                drawPath(
                    buildPath(closeToBottom = true),
                    brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), startY = 0f, endY = h),
                )
                drawPath(
                    buildPath(closeToBottom = false),
                    color = color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            if (progress.value >= 0.99f) {
                val sx = xAt(safeSelected)
                val sy = yAt(points[safeSelected].value)
                drawLine(color.copy(alpha = 0.35f), Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color, radius = 6.dp.toPx(), center = Offset(sx, sy))
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = Offset(sx, sy))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bar chart with tap / drag selection. Zero bars are drawn as a thin stub so gaps stay visible. */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    valueFormat: (Double) -> String = { it.roundToInt().toString() },
    showSelectionHeader: Boolean = true,
    labelEvery: Int = 1,
) {
    if (bars.isEmpty()) {
        ChartHint("No data yet.", height)
        return
    }
    val n = bars.size
    var selected by remember(bars) { mutableIntStateOf(-1) }
    val progress = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val grid = MaterialTheme.apex.chartGrid
    val muted = color.copy(alpha = 0.55f)

    Column(
        modifier = modifier.semantics {
            contentDescription = "Bar chart with $n bars. " + bars.joinToString(". ") { "${it.label}: ${valueFormat(it.value)}" }
        },
    ) {
        if (showSelectionHeader) {
            val shown = bars.getOrNull(selected) ?: bars.lastOrNull { it.value > 0 } ?: bars.last()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(valueFormat(shown.value), style = MaterialTheme.typography.headlineSmall)
                Text(shown.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        selected = (offset.x / size.width * n).toInt().coerceIn(0, n - 1)
                    }
                }
                .pointerInput(bars) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val maxV = bars.maxOf { it.value }.let { if (it <= 0.0) 1.0 else it }
            val slot = w / n
            val barW = slot * 0.6f
            val radius = CornerRadius(minOf(6.dp.toPx(), barW / 2f))
            drawLine(grid, Offset(0f, h - 1.dp.toPx()), Offset(w, h - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val full = ((bar.value / maxV).toFloat() * (h - 6.dp.toPx())).coerceAtLeast(3.dp.toPx())
                val barH = if (bar.value <= 0.0) 3.dp.toPx() else full * progress.value.coerceAtLeast(0.02f)
                val x = i * slot + (slot - barW) / 2f
                val fill = when {
                    bar.value <= 0.0 -> grid
                    i == selected || bar.highlight -> color
                    else -> muted
                }
                drawRoundRect(fill, topLeft = Offset(x, h - barH), size = Size(barW, barH), cornerRadius = radius)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            bars.forEachIndexed { i, bar ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelEvery.coerceAtLeast(1) == 0) {
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}
