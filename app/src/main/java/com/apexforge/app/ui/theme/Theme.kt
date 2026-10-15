package com.apexforge.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ApexForge visual identity
 *  - Charcoal black canvas with one confident accent: electric lime.
 *  - Ice blue is reserved for water, gold for records. Nothing else glows.
 *  - Bold, tightly tracked headings; calm 14-16sp body text; 12sp is the smallest size used.
 *  - Spacing scale: 4 / 8 / 12 / 16 / 20 / 24 / 32. Touch targets are at least 48dp.
 *  - Motion: springs for presses (120ms feel), 600-900ms eases for data reveals, nothing loops except water.
 */
object ApexPalette {
    val Black = Color(0xFF0A0B0D)
    val Surface = Color(0xFF121418)
    val SurfaceHigh = Color(0xFF1A1D23)
    val SurfaceHighest = Color(0xFF232830)
    val Outline = Color(0xFF2A2F38)
    val OutlineSoft = Color(0xFF20242B)
    val Lime = Color(0xFFC6FF3D)
    val LimeContainer = Color(0xFF263A06)
    val Ink = Color(0xFF0A0B0D)
    val TextHigh = Color(0xFFF4F6F8)
    val TextMid = Color(0xFFA0A8B4)
    val Ice = Color(0xFF5BC8FF)
    val Gold = Color(0xFFFFC247)
    val Danger = Color(0xFFFF6B6B)
}

@Immutable
data class ApexExtras(
    val water: Color,
    val gold: Color,
    val danger: Color,
    val chartGrid: Color,
    val glow: Color,
)

private val DarkExtras = ApexExtras(
    water = ApexPalette.Ice,
    gold = ApexPalette.Gold,
    danger = ApexPalette.Danger,
    chartGrid = Color(0xFF262B33),
    glow = ApexPalette.Lime.copy(alpha = 0.18f),
)

private val LightExtras = ApexExtras(
    water = Color(0xFF0A7BB5),
    gold = Color(0xFFA86A00),
    danger = Color(0xFFC62828),
    chartGrid = Color(0xFFDDE0D3),
    glow = Color(0xFF3C6E00).copy(alpha = 0.12f),
)

private val DarkScheme = darkColorScheme(
    primary = ApexPalette.Lime,
    onPrimary = ApexPalette.Ink,
    primaryContainer = ApexPalette.LimeContainer,
    onPrimaryContainer = ApexPalette.Lime,
    secondary = ApexPalette.Ice,
    onSecondary = ApexPalette.Ink,
    tertiary = ApexPalette.Gold,
    onTertiary = ApexPalette.Ink,
    background = ApexPalette.Black,
    onBackground = ApexPalette.TextHigh,
    surface = ApexPalette.Surface,
    onSurface = ApexPalette.TextHigh,
    surfaceVariant = ApexPalette.SurfaceHigh,
    onSurfaceVariant = ApexPalette.TextMid,
    surfaceContainerLowest = ApexPalette.Black,
    surfaceContainerLow = ApexPalette.Surface,
    surfaceContainer = ApexPalette.SurfaceHigh,
    surfaceContainerHigh = ApexPalette.SurfaceHighest,
    surfaceContainerHighest = ApexPalette.SurfaceHighest,
    outline = ApexPalette.Outline,
    outlineVariant = ApexPalette.OutlineSoft,
    error = ApexPalette.Danger,
    onError = ApexPalette.Ink,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3C6E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F5A5),
    onPrimaryContainer = Color(0xFF142400),
    secondary = Color(0xFF0A7BB5),
    onSecondary = Color.White,
    tertiary = Color(0xFFA86A00),
    onTertiary = Color.White,
    background = Color(0xFFF4F5EF),
    onBackground = Color(0xFF14160F),
    surface = Color.White,
    onSurface = Color(0xFF14160F),
    surfaceVariant = Color(0xFFECEEE4),
    onSurfaceVariant = Color(0xFF565C4B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9F3),
    surfaceContainer = Color(0xFFECEEE4),
    surfaceContainerHigh = Color(0xFFE4E7DA),
    surfaceContainerHighest = Color(0xFFDDE0D3),
    outline = Color(0xFFD0D5C2),
    outlineVariant = Color(0xFFE2E5D8),
    error = Color(0xFFC62828),
    onError = Color.White,
)

private val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Sans = FontFamily.SansSerif

private val ApexTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)

/** Light wrapper so UI code can request haptics without caring about the user's setting. */
class ApexHaptics(private val feedback: HapticFeedback?) {
    fun tick() {
        feedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        feedback?.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

val LocalApexExtras = staticCompositionLocalOf { DarkExtras }
val LocalHaptics = compositionLocalOf { ApexHaptics(null) }

val MaterialTheme.apex: ApexExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalApexExtras.current

@Composable
fun ApexTheme(
    darkTheme: Boolean,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val feedback = LocalHapticFeedback.current
    val haptics = remember(feedback, hapticsEnabled) { ApexHaptics(if (hapticsEnabled) feedback else null) }
    CompositionLocalProvider(
        LocalApexExtras provides (if (darkTheme) DarkExtras else LightExtras),
        LocalHaptics provides haptics,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ApexTypography,
            shapes = ApexShapes,
            content = content,
        )
    }
}
