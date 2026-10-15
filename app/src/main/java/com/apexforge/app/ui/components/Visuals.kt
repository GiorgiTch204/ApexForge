package com.apexforge.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apexforge.app.domain.Category
import com.apexforge.app.ui.theme.ApexPalette
import com.apexforge.app.ui.theme.apex
import kotlin.math.PI
import kotlin.math.sin

fun categoryColor(category: Category): Color = when (category) {
    Category.CHEST, Category.SHOULDERS, Category.TRICEPS -> Color(0xFFFF8A4C)
    Category.BACK, Category.BICEPS, Category.FOREARMS -> Color(0xFF5B8CFF)
    Category.LEGS, Category.QUADS, Category.HAMSTRINGS, Category.GLUTES, Category.CALVES -> ApexPalette.Lime
    Category.CORE -> Color(0xFFB07CFF)
    Category.FULL_BODY, Category.FUNCTIONAL -> ApexPalette.Gold
    Category.CARDIO -> Color(0xFFFF5C7A)
    Category.BODYWEIGHT -> ApexPalette.Ice
    Category.MOBILITY -> Color(0xFF3DDBB5)
}

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.CARDIO -> Icons.Rounded.DirectionsRun
    Category.MOBILITY -> Icons.Rounded.SelfImprovement
    Category.BODYWEIGHT -> Icons.Rounded.Accessibility
    Category.CORE -> Icons.Rounded.Bolt
    Category.FULL_BODY -> Icons.Rounded.Favorite
    else -> Icons.Rounded.FitnessCenter
}

/**
 * Original, code-drawn illustration used for every exercise (no external image links to break).
 * A licensed photo can be attached later through ExerciseEntity.imageAsset without touching the UI.
 */
@Composable
fun ExerciseArt(
    category: Category,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    val tint = categoryColor(category)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.32f), ApexPalette.Surface.copy(alpha = 0.9f)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = tint.copy(alpha = 0.10f),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = 1.5.dp.toPx(),
                )
                x += step
            }
        }
        Icon(categoryIcon(category), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Circular glass that fills with an animated wave as the day's water goal is approached. */
@Composable
fun WaterGlass(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val level by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "waterLevel")
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    val water = MaterialTheme.apex.water
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val surfaceY = h * (1f - level)
            val amplitude = if (level <= 0.01f) 0f else 7.dp.toPx() * (if (level >= 0.98f) 0.3f else 1f)
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, surfaceY)
                var x = 0f
                while (x <= w) {
                    lineTo(x, surfaceY + amplitude * sin((x / w) * 2f * PI.toFloat() * 1.5f + phase))
                    x += 4f
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(water.copy(alpha = 0.85f), water.copy(alpha = 0.45f)), startY = surfaceY, endY = h))
        }
        content()
    }
}
