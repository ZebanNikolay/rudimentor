package com.rudimentor.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.audio.Hand
import kotlin.math.min

@Composable
fun BeatIndicator(
    style: BeatIndicatorStyle,
    beatNumber: Int,
    isAccent: Boolean,
    hand: Hand,
    showSticking: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 750f),
        label = "${style.name} active beat",
    )
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.background
    val description = buildString {
        append("Beat $beatNumber, ")
        append(if (isAccent) "accented" else "normal")
        if (showSticking) append(", ${if (hand == Hand.Right) "right" else "left"} hand")
        if (isActive) append(", active")
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = when (style) {
                    BeatIndicatorStyle.MarchingOctagons -> 1f + activeProgress * 0.12f
                    BeatIndicatorStyle.PracticePad -> 1f + activeProgress * 0.08f
                    else -> 1f
                }
                scaleY = when (style) {
                    BeatIndicatorStyle.MarchingOctagons -> 1f - activeProgress * 0.10f
                    else -> scaleX
                }
                translationY = when (style) {
                    BeatIndicatorStyle.StickCaps -> activeProgress * 7.dp.toPx()
                    BeatIndicatorStyle.RhythmBars -> -activeProgress * 7.dp.toPx()
                    else -> 0f
                }
                rotationZ = if (style == BeatIndicatorStyle.MetronomeDiamonds) activeProgress * 12f else 0f
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                selected = isActive
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIndicator(
                style = style,
                isAccent = isAccent,
                hand = hand,
                activeProgress = activeProgress,
                primary = primary,
                onPrimary = onPrimary,
                surface = surface,
                outline = outline,
                onSurface = onSurface,
                background = background,
            )
        }
        if (showSticking) {
            Text(
                text = hand.label,
                color = if (isAccent) onPrimary else onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun DrawScope.drawIndicator(
    style: BeatIndicatorStyle,
    isAccent: Boolean,
    hand: Hand,
    activeProgress: Float,
    primary: Color,
    onPrimary: Color,
    surface: Color,
    outline: Color,
    onSurface: Color,
    background: Color,
) {
    val side = min(size.width, size.height)
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = side * 0.31f
    val fill = if (isAccent) primary else surface
    val stroke = if (isAccent) primary else outline

    when (style) {
        BeatIndicatorStyle.PracticePad -> {
            drawCircle(fill, radius, center)
            drawCircle(stroke, radius, center, style = Stroke(side * 0.045f))
            if (isAccent) drawCircle(onPrimary, radius * 0.72f, center, style = Stroke(side * 0.035f))
            if (activeProgress > 0f) drawCircle(primary.copy(alpha = 0.34f), radius * (1f + activeProgress * 0.55f), center, style = Stroke(side * 0.04f))
        }
        BeatIndicatorStyle.StepTiles -> {
            drawRoundRect(fill, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), CornerRadius(side * 0.1f))
            drawRoundRect(stroke, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), CornerRadius(side * 0.1f), style = Stroke(side * 0.04f))
            val barWidth = radius * 2f * (0.25f + activeProgress * 0.75f)
            drawRoundRect(primary, Offset(center.x - radius, center.y + radius * 0.68f), Size(barWidth, side * 0.07f), CornerRadius(side * 0.035f))
        }
        BeatIndicatorStyle.MarchingOctagons -> {
            drawPath(polygonPath(center, radius, 8), fill)
            drawPath(polygonPath(center, radius, 8), stroke, style = Stroke(side * 0.04f))
            if (isAccent) drawPath(diamondPath(center, radius * 0.47f), onPrimary, style = Stroke(side * 0.035f))
        }
        BeatIndicatorStyle.MetronomeDiamonds -> {
            val diamondRadius = if (isAccent) radius * 1.08f else radius
            drawPath(diamondPath(center, diamondRadius), fill)
            drawPath(diamondPath(center, diamondRadius), stroke, style = Stroke(side * 0.045f))
        }
        BeatIndicatorStyle.StickCaps -> rotate(if (hand == Hand.Right) 8f else -8f, center) {
            drawRoundRect(
                fill,
                Offset(center.x - radius * if (isAccent) 0.58f else 0.42f, center.y - radius),
                Size(radius * if (isAccent) 1.16f else 0.84f, radius * 2f),
                CornerRadius(radius),
            )
        }
        BeatIndicatorStyle.ConcentricPulse -> {
            drawCircle(fill, radius * 0.28f, center)
            drawCircle(stroke, radius * 0.68f, center, style = Stroke(side * 0.04f))
            if (isAccent) drawCircle(stroke, radius, center, style = Stroke(side * 0.035f))
            if (activeProgress > 0f) drawCircle(primary.copy(alpha = 0.42f), radius * (0.75f + activeProgress * 0.7f), center, style = Stroke(side * 0.045f))
        }
        BeatIndicatorStyle.ChevronFlow -> {
            val direction = if (hand == Hand.Right) 1f else -1f
            drawPath(chevronPath(center, radius, direction, 0f), stroke, style = Stroke(side * 0.1f, cap = StrokeCap.Round))
            if (isAccent) drawPath(chevronPath(center, radius, direction, -direction * radius * 0.48f), stroke, style = Stroke(side * 0.075f, cap = StrokeCap.Round))
            val scanX = center.x - direction * radius + direction * radius * 2f * activeProgress
            drawLine(primary, Offset(scanX, center.y - radius), Offset(scanX, center.y + radius), side * 0.045f, StrokeCap.Round)
        }
        BeatIndicatorStyle.NotchedChips -> {
            drawCircle(fill, radius, center)
            drawCircle(stroke, radius, center, style = Stroke(side * 0.04f))
            drawRect(if (isAccent) onPrimary else background, Offset(center.x - radius * 0.23f, center.y - radius * 1.08f), Size(radius * 0.46f, radius * 0.42f))
            rotate(activeProgress * 150f, center) {
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 1.15f, center.y - radius * 1.15f),
                    size = Size(radius * 2.3f, radius * 2.3f),
                    style = Stroke(side * 0.055f, cap = StrokeCap.Round),
                )
            }
        }
        BeatIndicatorStyle.RhythmBars -> {
            val height = if (isAccent) radius * 2.15f else radius * 1.55f
            drawRoundRect(fill, Offset(center.x - radius * 0.38f, center.y - height / 2f), Size(radius * 0.76f, height), CornerRadius(radius * 0.32f))
            drawRoundRect(stroke, Offset(center.x - radius * 0.38f, center.y - height / 2f), Size(radius * 0.76f, height), CornerRadius(radius * 0.32f), style = Stroke(side * 0.035f))
        }
        BeatIndicatorStyle.HybridGlyphs -> {
            drawCircle(if (isAccent) primary else Color.Transparent, radius, center)
            drawCircle(stroke, radius, center, style = Stroke(side * if (isAccent) 0.065f else 0.04f))
            if (isAccent) drawLine(onPrimary, Offset(center.x + radius * 0.55f, center.y - radius * 0.8f), Offset(center.x + radius * 0.95f, center.y - radius * 1.15f), side * 0.055f, StrokeCap.Round)
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = activeProgress * 350f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.13f, center.y - radius * 1.13f),
                size = Size(radius * 2.26f, radius * 2.26f),
                style = Stroke(side * 0.055f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun polygonPath(center: Offset, radius: Float, sides: Int): Path = Path().apply {
    repeat(sides) { index ->
        val angle = Math.toRadians(-90.0 + index * 360.0 / sides)
        val point = Offset(
            center.x + kotlin.math.cos(angle).toFloat() * radius,
            center.y + kotlin.math.sin(angle).toFloat() * radius,
        )
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun diamondPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius, center.y)
    lineTo(center.x, center.y + radius)
    lineTo(center.x - radius, center.y)
    close()
}

private fun chevronPath(center: Offset, radius: Float, direction: Float, offset: Float): Path = Path().apply {
    moveTo(center.x - direction * radius + offset, center.y - radius)
    lineTo(center.x + direction * radius + offset, center.y)
    lineTo(center.x - direction * radius + offset, center.y + radius)
}
