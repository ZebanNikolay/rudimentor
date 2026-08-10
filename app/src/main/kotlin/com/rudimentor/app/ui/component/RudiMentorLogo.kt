package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.SpaceGrotesk

/**
 * The wordmark: "rudi" spelled with four square pads, "mentor" underneath.
 *
 * Everything scales from a single module [padSize] (S in the brandbook, reference S = 24 dp):
 * gap S/12, word 2.167·S, word shifted up 0.583·S and left 0.125·S, letter 0.42·S.
 */
object LogoGeometry {
    const val GAP_RATIO = 1f / 12f
    const val WORD_RATIO = 2.167f
    const val LEAD_RATIO = -0.583f
    const val SHIFT_RATIO = -0.125f
    const val LETTER_RATIO = 0.42f
    val Tracking = (-0.005f).em

    val Pads: List<Pair<String, PadTone>> = listOf(
        "R" to PadTone.Normal, // rendered lit
        "U" to PadTone.Accent,
        "D" to PadTone.Accent,
        "I" to PadTone.Accent,
    )

    fun padRowWidth(padSize: Dp): Dp = padSize * 4 + padSize * GAP_RATIO * 3
}

@Composable
fun RudiMentorLogo(
    modifier: Modifier = Modifier,
    padSize: Dp = 24.dp,
    variant: PadVariant = PadVariant.Dark,
) {
    val gap = padSize * LogoGeometry.GAP_RATIO
    val wordSize = padSize.value * LogoGeometry.WORD_RATIO
    val lead = padSize * LogoGeometry.LEAD_RATIO
    val shift = padSize * LogoGeometry.SHIFT_RATIO

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoGeometry.Pads.forEachIndexed { index, (letter, tone) ->
                Pad(
                    size = padSize,
                    shape = PadShape.Square,
                    tone = tone,
                    lit = index == 0,
                    letter = letter,
                    letterFraction = LogoGeometry.LETTER_RATIO,
                    variant = variant,
                )
            }
        }
        Text(
            text = "mentor",
            color = if (variant == PadVariant.Light) RudiColors.LightWord else RudiColors.Text,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = wordSize.sp,
            letterSpacing = LogoGeometry.Tracking,
            // line-height 1 with trimmed font padding, so the negative lead lands exactly
            // where the brandbook puts it and "t" climbs into the pad row.
            lineHeight = 1f.em,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            // The word overlaps the pad row, so it is shifted and must not add its own height.
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val dx = shift.roundToPx()
                val dy = lead.roundToPx()
                val height = (placeable.height + dy).coerceAtLeast(0)
                layout(placeable.width, height) { placeable.place(dx, dy) }
            },
        )
    }
}
