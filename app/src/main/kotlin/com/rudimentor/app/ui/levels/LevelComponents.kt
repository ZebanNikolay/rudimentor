package com.rudimentor.app.ui.levels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.data.levels.LeadHand
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelModifier
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PatternStep
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

@Composable
internal fun PatternPreview(
    level: Level,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(PATTERN_ROWS) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                level.pattern.forEachIndexed { index, step ->
                    if (index == level.pattern.size / 2) Spacer(modifier = Modifier.width(5.dp))
                    PatternStepPad(step)
                }
            }
        }
        Text(
            text = level.pattern
                .chunked((level.pattern.size / 2).coerceAtLeast(1))
                .joinToString(" · ") { chunk -> chunk.joinToString("") { it.label } },
            color = RudiColors.RowNumber,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 2.2.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PatternStepPad(step: PatternStep) {
    // The package marks accents per lesson, not per step, so every step renders unaccented.
    val tone = PadTone.Normal
    if (step.hands.size == 1) {
        val hand = step.hands.single()
        Pad(
            size = 31.dp,
            shape = if (hand == PatternHand.Left) PadShape.Round else PadShape.Square,
            tone = tone,
            letter = hand.storageName,
            letterFraction = 0.34f,
        )
        return
    }

    Box(modifier = Modifier.size(36.dp)) {
        Pad(
            size = 27.dp,
            shape = PadShape.Square,
            tone = tone,
            letter = PatternHand.Right.storageName,
            letterFraction = 0.3f,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Pad(
            size = 27.dp,
            shape = PadShape.Round,
            tone = tone,
            letter = PatternHand.Left.storageName,
            letterFraction = 0.3f,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
internal fun LevelTags(
    level: Level,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LevelTag(level.type.displayName)
        level.modifiers.forEach { LevelTag(it.displayName) }
        LevelTag(level.technique.strokeStyle.replace('_', ' '))
    }
}

@Composable
private fun LevelTag(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .border(1.dp, RudiColors.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = RudiTextStyles.RowNumber,
        color = RudiColors.Muted,
        letterSpacing = 0.8.sp,
    )
}

@Composable
internal fun LevelPlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    active: Boolean = true,
    showStop: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (active) {
                    val glowRadius = this.size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to RudiColors.BrickLit.copy(alpha = 0.22f),
                            1f to androidx.compose.ui.graphics.Color.Transparent,
                            center = center,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                    )
                    drawRoundRect(
                        color = RudiColors.ButtonShadowLit,
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size = this.size,
                        cornerRadius = CornerRadius(20.dp.toPx()),
                    )
                }
            }
            .clip(shape)
            .background(if (active) RudiColors.Brick else RudiColors.Surface, shape)
            .border(1.dp, if (active) RudiColors.BrickLit else RudiColors.Line, shape)
            .clickable(
                enabled = active,
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val canvasSize = this.size
            if (showStop) {
                val inset = canvasSize.width * 0.18f
                drawRoundRect(
                    color = RudiColors.Text,
                    topLeft = Offset(inset, inset),
                    size = Size(canvasSize.width - inset * 2, canvasSize.height - inset * 2),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            } else {
                val triangle = Path().apply {
                    moveTo(canvasSize.width * 0.2f, 0f)
                    lineTo(canvasSize.width, canvasSize.height / 2f)
                    lineTo(canvasSize.width * 0.2f, canvasSize.height)
                    close()
                }
                drawPath(triangle, color = if (active) RudiColors.Text else RudiColors.RowNumber)
            }
        }
    }
}

@Composable
internal fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(RudiColors.SurfaceAlt, RoundedCornerShape(12.dp))
            .border(RudiDimens.PadBorder, RudiColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            letterSpacing = 1.4.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
            color = RudiColors.Text,
        )
    }
}

internal val LevelType.displayName: String
    get() = when (this) {
        LevelType.Steady -> "Steady"
        LevelType.Isolation -> "Isolation"
        LevelType.Unison -> "Unison"
        LevelType.Transition -> "Transition"
        LevelType.SubdivisionSwitch -> "Subdivision switch"
        LevelType.TempoRamp -> "Tempo ramp"
        LevelType.Dynamics -> "Dynamics"
    }

internal val LevelModifier.displayName: String
    get() = when (this) {
        LevelModifier.Weak -> "Weak"
        LevelModifier.Endurance -> "Endurance"
    }

internal val LeadHand.padShape: PadShape
    get() = if (this == LeadHand.Left) PadShape.Round else PadShape.Square

private const val PATTERN_ROWS = 2
