package com.apexforge.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexforge.app.ui.theme.LocalHaptics

/** Spring-based press feedback used by every tappable surface. */
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun ApexCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.pressScale(interaction),
            shape = shape,
            color = containerColor,
            border = border,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

enum class ButtonStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun ApexButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val container: Color
    val content: Color
    val border: BorderStroke?
    when (style) {
        ButtonStyle.Primary -> { container = colors.primary; content = colors.onPrimary; border = null }
        ButtonStyle.Secondary -> { container = colors.surfaceVariant; content = colors.onSurface; border = BorderStroke(1.dp, colors.outline) }
        ButtonStyle.Ghost -> { container = Color.Transparent; content = colors.primary; border = null }
        ButtonStyle.Danger -> { container = Color.Transparent; content = colors.error; border = BorderStroke(1.dp, colors.error.copy(alpha = 0.6f)) }
    }
    Surface(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = border,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (compact) 44.dp else 52.dp)
                .padding(horizontal = if (compact) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ApexIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current
    IconButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.4f))
    }
}

@Composable
fun ApexChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.semantics { role = Role.Tab },
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Equal-width option switcher for two to four choices. */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline, MaterialTheme.shapes.medium)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .clickable(role = Role.Tab) {
                        haptics.tick()
                        onSelect(option)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ApexIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    ApexCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            ApexButton(actionLabel, onAction)
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ApexDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun apexFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)

@Composable
fun ApexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    leadingIcon: ImageVector? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        shape = MaterialTheme.shapes.medium,
        colors = apexFieldColors(),
    )
}

private fun formatInput(value: Double, decimal: Boolean): String = when {
    value <= 0.0 -> ""
    decimal && value % 1.0 != 0.0 -> String.format(java.util.Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    else -> value.toLong().toString()
}

private fun parseInput(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: 0.0

private fun isValidPartial(text: String, decimal: Boolean): Boolean =
    if (decimal) Regex("^\\d{0,4}([.,]\\d?)?$").matches(text) else Regex("^\\d{0,4}$").matches(text)

/**
 * Large numeric entry used for weights and reps. Keeps its own text so partial input like "12." works,
 * and re-syncs when the underlying value changes from elsewhere (for example "use previous").
 */
@Composable
fun NumberInput(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    enabled: Boolean = true,
    placeholder: String = "0",
    contentDescription: String = "Number",
) {
    var text by remember { mutableStateOf(formatInput(value, decimal)) }
    LaunchedEffect(value) {
        if (parseInput(text) != value) text = formatInput(value, decimal)
    }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = { new ->
            if (isValidPartial(new, decimal)) {
                text = new
                onValueChange(parseInput(new))
            }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
}

/** Minus / value / plus control for sets, reps and rest. */
@Composable
fun Stepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ApexIconButton(Icons.Rounded.Remove, "Decrease $label", onMinus)
            Text(
                valueText,
                modifier = Modifier.widthIn(min = 44.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            ApexIconButton(Icons.Rounded.Add, "Increase $label", onPlus)
        }
    }
}

@Composable
fun SpacerH(height: Dp) = Spacer(Modifier.height(height))
