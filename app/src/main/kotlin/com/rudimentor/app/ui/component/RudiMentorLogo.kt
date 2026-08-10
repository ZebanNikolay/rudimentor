package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.SpaceGrotesk

/**
 * The wordmark: "rudi" spelled with four square pads, "mentor" underneath.
 *
 * Everything scales from a single module [padSize] (S in the brandbook, reference S = 24 dp):
 * gap S/6, word 2.167·S, word pulled up 0.583·S and left 0.125·S, letter 0.42·S.
 */
object LogoGeometry {
    const val GAP_RATIO = 1f / 6f
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

    // Explicit placement: the word is pinned to the bottom of the pad row and then pulled
    // up by the negative lead, so nothing in the parent layout can undo the overlap.
    Layout(
        modifier = modifier,
        content = {
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
                style = TextStyle(
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = wordSize.sp,
                    lineHeight = wordSize.sp,
                    letterSpacing = LogoGeometry.Tracking,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val padRow = measurables[0].measure(loose)
        val word = measurables[1].measure(loose)

        val leadPx = lead.roundToPx()
        val shiftPx = shift.roundToPx()
        val wordTop = padRow.height + leadPx
        val width = maxOf(padRow.width, word.width + shiftPx.coerceAtLeast(0))
        val height = maxOf(padRow.height, wordTop + word.height)

        layout(width, height) {
            padRow.place(0, 0)
            word.place(shiftPx, wordTop)
        }
    }
}
