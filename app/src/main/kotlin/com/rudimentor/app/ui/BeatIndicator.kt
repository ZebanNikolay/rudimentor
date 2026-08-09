package com.rudimentor.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.rudimentor.app.audio.Hand
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val MaximumPulseMs = 200

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
    beatIntervalMs: Int = 500,
) {
    val pulseDuration = min(MaximumPulseMs, (beatIntervalMs * 0.4f).toInt())
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = pulseDuration),
        label = "${style.name} active beat",
    )
    val colors = MaterialTheme.colorScheme
    val description = buildString {
        append("Beat $beatNumber, ")
        if (showSticking) {
            append(if (hand == Hand.Right) "right hand" else "left hand")
        } else {
            append(if (isAccent) "accent" else "normal")
        }
        if (isActive) append(", active")
    }

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = description
                stateDescription = if (showSticking) hand.label else if (isAccent) "Accent" else "Normal"
                selected = isActive
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pulseScale = 1f + activeProgress * 0.08f
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
        ) {
            drawIndicator(
                style = style,
                beatNumber = beatNumber,
                isAccent = isAccent,
                hand = hand,
                showSticking = showSticking,
                activeProgress = activeProgress,
                right = colors.primary,
                onRight = colors.onPrimary,
                left = colors.tertiary,
                onLeft = colors.onTertiary,
                neutral = colors.surfaceContainerHighest,
                onNeutral = colors.onSurface,
                outline = colors.outline,
                background = colors.surface,
            )
        }
        if (showSticking) {
            val contentColor = when {
                hand == Hand.Right -> colors.onPrimary
                else -> colors.onTertiary
            }
            Text(
                text = hand.label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun DrawScope.drawIndicator(
    style: BeatIndicatorStyle,
    beatNumber: Int,
    isAccent: Boolean,
    hand: Hand,
    showSticking: Boolean,
    activeProgress: Float,
    right: Color,
    onRight: Color,
    left: Color,
    onLeft: Color,
    neutral: Color,
    onNeutral: Color,
    outline: Color,
    background: Color,
) {
    val side = min(size.width, size.height)
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = side * 0.30f
    val stateColor = when {
        !showSticking && isAccent -> right
        !showSticking -> neutral
        hand == Hand.Right -> right
        else -> left
    }
    val direction = if (hand == Hand.Right) 1f else -1f
    val stroke = Stroke(side * 0.055f, cap = StrokeCap.Round)

    when (style) {
        BeatIndicatorStyle.RoundSquare -> {
            if (showSticking && hand == Hand.Left) {
                drawCircle(stateColor, radius, center)
            } else {
                drawRoundRect(
                    stateColor,
                    Offset(center.x - radius, center.y - radius),
                    Size(radius * 2f, radius * 2f),
                    CornerRadius(side * 0.09f),
                )
            }
            if (!showSticking && !isAccent) drawCircle(outline, radius * 0.22f, center)
        }
        BeatIndicatorStyle.HalfFilledDot -> {
            drawCircle(background, radius, center)
            drawCircle(outline, radius, center, style = stroke)
            if (!showSticking && isAccent) {
                drawCircle(stateColor, radius * 0.84f, center)
            } else if (showSticking) {
                drawArc(
                    stateColor,
                    startAngle = if (hand == Hand.Right) -90f else 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                )
            }
        }
        BeatIndicatorStyle.CaretChip -> {
            drawRoundRect(
                stateColor,
                Offset(center.x - radius, center.y - radius * 0.72f),
                Size(radius * 2f, radius * 1.44f),
                CornerRadius(side * 0.08f),
            )
            drawPath(chevronPath(center + Offset(direction * activeProgress * side * 0.04f, 0f), radius * 0.48f, direction), if (hand == Hand.Right) onRight else onLeft, style = stroke)
        }
        BeatIndicatorStyle.PolygonMorph -> {
            val square = RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.18f))
            val triangle = RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.18f))
            val morph = if (hand == Hand.Right) Morph(square, triangle) else Morph(triangle, square)
            val path = morphPath(morph, activeProgress * 0.38f)
            translate(center.x, center.y) {
                scale(radius, radius) { drawPath(path, stateColor) }
            }
        }
        BeatIndicatorStyle.WavyTrack -> {
            if (showSticking && hand == Hand.Left) {
                val path = Path().apply {
                    moveTo(center.x - radius, center.y)
                    repeat(16) { step ->
                        val fraction = (step + 1) / 16f
                        lineTo(
                            center.x - radius + radius * 2f * fraction,
                            center.y + sin(fraction * PI.toFloat() * 4f) * radius * 0.28f,
                        )
                    }
                }
                drawPath(path, stateColor, style = stroke)
            } else {
                drawLine(stateColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), stroke.width, StrokeCap.Round)
            }
            if (activeProgress > 0f) drawCircle(stateColor, side * 0.06f, Offset(center.x - radius + radius * 2f * activeProgress, center.y))
        }
        BeatIndicatorStyle.RingSweep -> {
            drawCircle(outline, radius, center, style = stroke)
            drawArc(
                stateColor,
                startAngle = -90f,
                sweepAngle = direction * (28f + 332f * activeProgress),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = stroke,
            )
        }
        BeatIndicatorStyle.AlphaStep -> {
            val alpha = if (isAccent || activeProgress > 0f) 0.88f else 0.42f
            if (showSticking && hand == Hand.Left) drawCircle(stateColor.copy(alpha = alpha), radius, center)
            else drawRoundRect(stateColor.copy(alpha = alpha), Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), CornerRadius(side * 0.06f))
            drawCircle(outline, radius * 1.05f, center, style = Stroke(side * 0.035f))
        }
        BeatIndicatorStyle.SpikeColumn -> {
            repeat(3) { spike ->
                val x = center.x + (spike - 1) * side * 0.12f
                val spikeHeight = radius * (0.55f + activeProgress * (1f - spike * 0.15f))
                drawLine(stateColor, Offset(x, center.y), Offset(x, center.y - direction * spikeHeight), side * 0.07f, StrokeCap.Round)
            }
        }
        BeatIndicatorStyle.IndicatorGlyph -> {
            drawCircle(background, radius, center)
            drawCircle(outline, radius, center, style = stroke)
            if (showSticking) drawPath(trianglePath(center, radius * 0.58f, direction), stateColor)
            else drawCircle(stateColor, if (isAccent) radius * 0.52f else radius * 0.28f, center)
            if (activeProgress > 0f) drawCircle(stateColor, radius * (0.72f + activeProgress * 0.35f), center, style = Stroke(side * 0.035f))
        }
        BeatIndicatorStyle.NumberedRail -> {
            val topRounded = hand == Hand.Right || !showSticking
            drawRoundRect(
                stateColor,
                Offset(center.x - radius * 0.42f, center.y - radius),
                Size(radius * (0.84f + activeProgress * 0.18f), radius * 2f),
                CornerRadius(if (topRounded) radius * 0.42f else radius * 0.12f),
            )
            drawLine(onNeutral, Offset(center.x - side * 0.06f, center.y), Offset(center.x + side * 0.06f, center.y), side * 0.04f)
            repeat(beatNumber.coerceAtMost(8)) { marker ->
                val markerX = center.x - side * 0.14f + marker * side * 0.04f
                drawCircle(onNeutral, side * 0.012f, Offset(markerX, center.y + side * 0.13f))
            }
        }
    }
}

private fun morphPath(morph: Morph, progress: Float): Path {
    val cubics = morph.asCubics(progress)
    if (cubics.isEmpty()) return Path()
    return Path().apply {
        moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
        cubics.forEach { cubic ->
            cubicTo(
                cubic.control0X,
                cubic.control0Y,
                cubic.control1X,
                cubic.control1Y,
                cubic.anchor1X,
                cubic.anchor1Y,
            )
        }
        close()
    }
}

private fun chevronPath(center: Offset, radius: Float, direction: Float): Path = Path().apply {
    moveTo(center.x - direction * radius, center.y - radius)
    lineTo(center.x + direction * radius, center.y)
    lineTo(center.x - direction * radius, center.y + radius)
}

private fun trianglePath(center: Offset, radius: Float, direction: Float): Path = Path().apply {
    moveTo(center.x + direction * radius, center.y)
    lineTo(center.x - direction * radius, center.y - radius)
    lineTo(center.x - direction * radius, center.y + radius)
    close()
}
