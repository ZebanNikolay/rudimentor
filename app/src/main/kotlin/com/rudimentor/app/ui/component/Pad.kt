package com.rudimentor.app.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

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
 * so border weight, inner shadow and LED dot never drift apart. The face itself
 * is painted by [drawPadFace], which the practice track reuses inside its own
 * Canvas.
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
    stars: Int = 0,
    crown: Boolean = false,
) {
    val light = variant == PadVariant.Light
    val round = shape == PadShape.Round
    val muted = tone == PadTone.Mute
    val palette = padPalette(round = round, tone = tone, lit = lit, light = light)

    // A lit pad is a key with the lamp switched on: flat brick fill and an even halo
    // around the whole outline, never a directional drop shadow.
    val glow: Modifier = if (lit && !muted) {
        val strength = if (tone == PadTone.Accent) 0.19f else 0.125f
        Modifier.drawBehind {
            val side = this.size.minDimension
            val radius = side * 1.25f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to RudiColors.BrickLit.copy(alpha = strength),
                    0.42f to RudiColors.BrickLit.copy(alpha = strength * 0.7f),
                    1f to Color.Transparent,
                    center = this.center,
                    radius = radius,
                ),
                radius = radius,
                center = this.center,
            )
        }
    } else {
        Modifier
    }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh),
        label = "padPress",
    )

    Box(
        modifier = modifier
            .size(size)
            .then(glow)
            .alpha(palette.alpha)
            // Press feedback is a small squeeze of the pad itself — no extra outline.
            .scale(pressScale),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawPadFace(
                topLeft = Offset.Zero,
                side = this.size.minDimension,
                round = round,
                tone = tone,
                palette = palette,
                lit = lit,
                strokeWidth = RudiDimens.PadBorder.toPx(),
                light = light,
                // The crown stands exactly where the LED dot would be, so the dot goes.
                showLed = !crown,
            )
            if (stars > 0 || crown) {
                drawPadMarkers(
                    topLeft = Offset.Zero,
                    side = this.size.minDimension,
                    round = round,
                    stars = stars,
                    crown = crown,
                )
            }
        }

        if (showLetter && !letter.isNullOrEmpty()) {
            // Stars take the bottom band of the face, so the letter shrinks a little and
            // moves up to keep the pad balanced instead of colliding with them.
            val crowded = stars > 0
            Text(
                text = letter,
                modifier = if (crowded) {
                    Modifier.offset(y = -size * 0.12f)
                } else {
                    Modifier
                },
                color = palette.letter,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = maxOf(9f, size.value * letterFraction * if (crowded) 0.85f else 1f).sp,
                // A muted pad is told apart by the dashed outline and the dimmed letter
                // only — the letter is never struck through.
            )
        }
    }
}

/** Unclipped rectangle helper kept for previews that need a plain body. */
internal val PadPreviewShape: Shape = RectangleShape
