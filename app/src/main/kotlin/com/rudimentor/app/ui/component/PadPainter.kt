package com.rudimentor.app.ui.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The pad face as plain drawing instructions.
 *
 * The [Pad] composable and the practice track both need the exact same key --
 * body, inner shadow, outline and LED -- but the track draws dozens of pads per
 * frame inside one Canvas and cannot afford a composable per note. So the paint
 * logic lives here and has exactly one owner: whoever changes the pad changes it
 * for the logo, the metronome grid and the practice track at once.
 */
internal data class PadPalette(
    val body: Color,
    val border: Color,
    val led: Color,
    val letter: Color,
    val alpha: Float,
)

internal fun padPalette(
    round: Boolean,
    tone: PadTone,
    lit: Boolean,
    light: Boolean = false,
): PadPalette {
    val muted = tone == PadTone.Mute
    return PadPalette(
        body = when {
            muted -> Color.Transparent
            lit && tone == PadTone.Accent -> RudiColors.PadAccentLit
            lit -> RudiColors.Brick
            light -> RudiColors.LightPadBody
            else -> RudiColors.Surface
        },
        border = when {
            muted && lit -> RudiColors.PadMuteLitBorder
            muted -> RudiColors.PadMuteBorder
            lit -> RudiColors.BrickLit
            tone == PadTone.Accent -> RudiColors.Brick
            light -> RudiColors.LightPadLine
            else -> RudiColors.Line
        },
        led = when {
            muted -> RudiColors.PadLedMute
            lit -> RudiColors.PadLedLit
            tone == PadTone.Accent -> RudiColors.Brick
            round -> RudiColors.PadLedRound
            else -> RudiColors.PadLed
        },
        letter = when {
            muted && lit -> RudiColors.PadMuteLitLetter
            muted -> RudiColors.PadLetterMute
            lit -> RudiColors.PadLetterLit
            tone == PadTone.Accent -> RudiColors.PadLetterAccent
            light -> RudiColors.LightPadLetter
            else -> RudiColors.PadLetter
        },
        alpha = when {
            muted && lit -> 0.85f
            muted -> 0.55f
            else -> 1f
        },
    )
}

/**
 * Draws one pad face with its top-left corner at [topLeft] and side [side].
 *
 * [alpha] multiplies every colour, which is how the practice track fades a
 * missed note out while it falls. It does not include [PadPalette.alpha]: the
 * [Pad] composable applies that one to the whole layer, so callers that draw
 * straight into a Canvas have to fold it into [alpha] themselves.
 */
internal fun DrawScope.drawPadFace(
    topLeft: Offset,
    side: Float,
    round: Boolean,
    tone: PadTone,
    palette: PadPalette,
    lit: Boolean,
    strokeWidth: Float,
    light: Boolean = false,
    alpha: Float = 1f,
) {
    if (side <= 0f) return
    val muted = tone == PadTone.Mute
    val total = alpha.coerceIn(0f, 1f)
    val radius = if (round) side / 2f else side * RudiDimens.PAD_CORNER_FRACTION
    val corner = CornerRadius(radius, radius)

    translate(left = topLeft.x, top = topLeft.y) {
        if (palette.body != Color.Transparent) {
            drawRoundRect(
                color = palette.body.copy(alpha = palette.body.alpha * total),
                size = Size(side, side),
                cornerRadius = corner,
            )
        }

        // Inner shadow along the whole outline -- the "recessed key" cue. A lit pad
        // has none: it reads as a flat glowing surface.
        if (!muted && !lit) {
            val outline = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = side,
                        bottom = side,
                        cornerRadius = corner,
                    ),
                )
            }
            val depth = side * 0.14f
            val steps = 10
            val step = depth / steps
            val topBias = side * 0.035f
            val maxAlpha = if (light) 0.055f else 0.17f
            clipPath(outline) {
                for (i in 0 until steps) {
                    val t = i / steps.toFloat()
                    val inset = i * step
                    val shadowAlpha = maxAlpha * (1f - t) * (1f - t) * total
                    val innerRadius = (radius - inset).coerceAtLeast(0f)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = shadowAlpha),
                        topLeft = Offset(inset, inset + topBias),
                        size = Size(side - inset * 2f, side - inset * 2f),
                        cornerRadius = CornerRadius(innerRadius, innerRadius),
                        style = Stroke(width = step * 1.6f),
                    )
                }
            }
        }

        drawRoundRect(
            color = palette.border.copy(alpha = palette.border.alpha * total),
            size = Size(side, side),
            cornerRadius = corner,
            style = Stroke(
                width = strokeWidth,
                pathEffect = if (muted) {
                    PathEffect.dashPathEffect(floatArrayOf(side * 0.12f, side * 0.09f), 0f)
                } else {
                    null
                },
            ),
        )

        val ledRadius = side * RudiDimens.PAD_LED_FRACTION / 2f
        val topFraction = if (round) RudiDimens.PAD_LED_TOP_ROUND else RudiDimens.PAD_LED_TOP_SQUARE
        val rightFraction =
            if (round) RudiDimens.PAD_LED_RIGHT_ROUND else RudiDimens.PAD_LED_RIGHT_SQUARE
        drawCircle(
            color = palette.led.copy(alpha = palette.led.alpha * total),
            radius = maxOf(ledRadius, 1.5f),
            center = Offset(
                x = side - rightFraction * side - ledRadius,
                y = topFraction * side + ledRadius,
            ),
        )
    }
}
