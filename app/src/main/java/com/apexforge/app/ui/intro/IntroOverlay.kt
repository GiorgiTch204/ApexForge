package com.apexforge.app.ui.intro

import android.provider.Settings
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexforge.app.ui.theme.ApexPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.random.Random

private data class Particle(val x: Float, val y: Float, val radius: Float, val laps: Int, val phase: Float)

/**
 * Branded intro shown once per cold start, on top of the already-composed app.
 *
 * [progress] is the real initialisation progress (preferences loaded, exercise library ready,
 * starter routines ready) and the bar only ever shows that value, smoothed so it doesn't jump.
 * Nothing waits artificially: the intro leaves as soon as the brand reveal (0.9s) has played and the
 * app is ready. With system animations turned off it is skipped entirely.
 */
@Composable
fun IntroOverlay(
    progress: Float,
    ready: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val reveal = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val exit = remember { Animatable(0f) }
    val barProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(if (animationsEnabled) 450 else 0, easing = FastOutSlowInEasing),
        label = "introBar",
    )
    val onDone by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        if (animationsEnabled) reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { reveal.value }.first { it >= 1f }
        snapshotFlow { barProgress }.first { it >= 0.99f }
        if (animationsEnabled) {
            delay(120)
            exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
        onDone()
    }

    val particles = remember {
        val random = Random(7)
        List(26) {
            Particle(random.nextFloat(), random.nextFloat(), 1f + random.nextFloat() * 1.8f, 1 + random.nextInt(2), random.nextFloat())
        }
    }
    val drift by rememberInfiniteTransition(label = "intro").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - exit.value
                val scale = 1f + 0.06f * exit.value
                scaleX = scale
                scaleY = scale
            }
            .background(ApexPalette.Black)
            .pointerInput(Unit) { /* swallow touches while the intro is visible */ }
            .semantics { contentDescription = "ApexForge is loading" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Atmosphere: a soft lime glow that grows with the reveal.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(ApexPalette.Lime.copy(alpha = 0.16f * reveal.value), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.width * (0.35f + 0.5f * reveal.value),
                ),
            )
            particles.forEach { p ->
                val y = (((p.y - drift * p.laps) % 1f) + 1f) % 1f
                val twinkle = 0.25f + 0.35f * ((kotlin.math.sin((drift * p.laps + p.phase) * 2f * Math.PI.toFloat()) + 1f) / 2f)
                drawCircle(
                    color = ApexPalette.Lime.copy(alpha = twinkle * reveal.value),
                    radius = p.radius.dp.toPx(),
                    center = Offset(p.x * size.width, y * size.height),
                )
            }
            // Light sweep across the wordmark band.
            val bandTop = size.height * 0.42f - 40.dp.toPx()
            val sweepX = size.width * (-0.3f + 1.6f * reveal.value)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                    startX = sweepX - 120.dp.toPx(),
                    endX = sweepX + 120.dp.toPx(),
                ),
                topLeft = Offset(0f, bandTop),
                size = Size(size.width, 80.dp.toPx()),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = ApexPalette.TextHigh)) { append("APEX") }
                    withStyle(SpanStyle(color = ApexPalette.Lime)) { append("FORGE") }
                },
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 18.dp.toPx()
                },
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (14f - 8f * reveal.value).sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "TRAIN  ·  TRACK  ·  TRANSFORM",
                modifier = Modifier.graphicsLayer { alpha = ((reveal.value - 0.4f) / 0.6f).coerceIn(0f, 1f) },
                color = ApexPalette.TextMid,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(48.dp))
            Canvas(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .graphicsLayer { alpha = reveal.value },
            ) {
                val barH = 3.dp.toPx()
                val y = (size.height - barH) / 2f
                val radius = CornerRadius(barH / 2f)
                drawRoundRect(ApexPalette.Outline, Offset(0f, y), Size(size.width, barH), radius)
                val fill = size.width * barProgress
                if (fill > 0f) {
                    // Glow first, crisp bar on top.
                    drawRoundRect(
                        ApexPalette.Lime.copy(alpha = 0.22f),
                        Offset(0f, y - 4.dp.toPx()),
                        Size(fill, barH + 8.dp.toPx()),
                        CornerRadius(6.dp.toPx()),
                    )
                    drawRoundRect(ApexPalette.Lime, Offset(0f, y), Size(fill, barH), radius)
                }
            }
        }
    }
}
