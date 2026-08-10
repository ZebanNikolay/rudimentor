package com.rudimentor.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight

/** Pad shape encodes the hand: right is a rounded square, left is a circle. */
enum class PadShape {
    Square,
    Round,
}

/** The three editable states of a beat. */
enum class PadTone {
    Normal,
    Accent,
    Mute,
}

/** Dark app surface, or the light variant kept for external materials. */
enum class PadVariant {
    Dark,
    Light,
}

/**
 * The single building block of the whole visual language: a drum-machine pad.
 *
 * The same composable renders metronome beats, the logo and the launcher icon,
 * so border weight, inner shadow and LED dot never drift apart.
 */
@Composable
fun Pad(
    size: Dp,
    shape: PadShape,
    tone: PadTone,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    letter: String? = null,
    showLetter: Boolean = true,
    pressed: Boolean = false,
    letterFraction: Float = RudiDimens.PAD_LETTER_FRACTION,
    variant: PadVariant = PadVariant.Dark,
) {
    val light = variant == PadVariant.Light
    val round = shape == PadShape.Round
    val muted = tone == PadTone.Mute

    val body: Color = when {
        muted -> Color.Transparent
        lit && tone == PadTone.Accent -> RudiColors.PadAccentLit
        lit -> RudiColors.Brick
        light -> RudiColors.LightPadBody
        else -> RudiColors.Surface
    }
    val border: Color = when {
        muted && lit -> RudiColors.PadMuteLitBorder
        muted -> RudiColors.PadMuteBorder
        lit -> RudiColors.BrickLit
        tone == PadTone.Accent -> RudiColors.Brick
        light -> RudiColors.LightPadLine
        else -> RudiColors.Line
    }
    val led: Color = when {
        muted -> RudiColors.PadLedMute
        lit -> RudiColors.PadLedLit
        tone == PadTone.Accent -> RudiColors.Brick
        round -> RudiColors.PadLedRound
        else -> RudiColors.PadLed
    }
    val letterColor: Color = when {
        muted && lit -> RudiColors.PadMuteLitLetter
        muted -> RudiColors.PadLetterMute
        lit -> RudiColors.PadLetterLit
        tone == PadTone.Accent -> RudiColors.PadLetterAccent
        light -> RudiColors.LightPadLetter
        else -> RudiColors.PadLetter
    }
    val padAlpha = when {
        muted && lit -> 0.85f
        muted -> 0.55f
        else -> 1f
    }

    val composeShape: Shape = if (round) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(percent = (RudiDimens.PAD_CORNER_FRACTION * 100).toInt())
    }

    val glow: Modifier = if (lit && !muted) {
        Modifier.shadow(
            elevation = if (tone == PadTone.Accent) 18.dp else 14.dp,
            shape = composeShape,
            clip = false,
            ambientColor = RudiColors.BrickLit,
            spotColor = RudiColors.BrickLit,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(glow)
            .alpha(padAlpha)
            .then(
                if (pressed) {
                    Modifier.drawBehind {
                        val inset = 3.dp.toPx()
                        val stroke = 2.dp.toPx()
                        val radius = if (round) {
                            (this.size.height + inset * 2) / 2f
                        } else {
                            (this.size.width + inset * 2) * RudiDimens.PAD_CORNER_FRACTION
                        }
                        drawRoundRect(
                            color = RudiColors.BrickLit,
                            topLeft = Offset(-inset, -inset),
                            size = Size(
                                this.size.width + inset * 2,
                                this.size.height + inset * 2,
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = stroke),
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val boxSize = this.size
            val side = boxSize.minDimension
            val radius = if (round) side / 2f else side * RudiDimens.PAD_CORNER_FRACTION
            val corner = CornerRadius(radius, radius)
            val strokeWidth = RudiDimens.PadBorder.toPx()

            if (body != Color.Transparent) {
                drawRoundRect(color = body, cornerRadius = corner)
            }

            // Inner shadow from the top edge — the "recessed key" cue.
            if (!muted) {
                val outline = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = boxSize.width,
                            bottom = boxSize.height,
                            cornerRadius = corner,
                        ),
                    )
                }
                clipPath(outline) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = if (lit) 0.22f else 0.45f),
                            0.32f to Color.Transparent,
                        ),
                    )
                }
            }

            drawRoundRect(
                color = border,
                cornerRadius = corner,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = if (muted) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(side * 0.12f, side * 0.09f),
                            0f,
                        )
                    } else {
                        null
                    },
                ),
            )

            val ledRadius = side * RudiDimens.PAD_LED_FRACTION / 2f
            val topFraction = if (round) {
                RudiDimens.PAD_LED_TOP_ROUND
            } else {
                RudiDimens.PAD_LED_TOP_SQUARE
            }
            val rightFraction = if (round) {
                RudiDimens.PAD_LED_RIGHT_ROUND
            } else {
                RudiDimens.PAD_LED_RIGHT_SQUARE
            }
            drawCircle(
                color = led,
                radius = maxOf(ledRadius, 1.5f),
                center = Offset(
                    x = boxSize.width - rightFraction * side - ledRadius,
                    y = topFraction * side + ledRadius,
                ),
            )
        }

        if (showLetter && !letter.isNullOrEmpty()) {
            Text(
                text = letter,
                color = letterColor,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = maxOf(9f, size.value * letterFraction).sp,
                textDecoration = if (muted) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
    }
}

/** Unclipped rectangle helper kept for previews that need a plain body. */
internal val PadPreviewShape: Shape = RectangleShape
