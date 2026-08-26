package com.rudimentor.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors

/**
 * What the learner earned at a level, as a row of marks: three stars, the earned ones filled,
 * plus the crown when the run was a full combo.
 *
 * The map draws the same marks on the pad of the node (decision 126), but a pad on a detail
 * screen carries no meaning of its own — a circle with two stars in it read as a puzzle
 * (decision 153). On a screen the marks belong next to the result they describe, so they are
 * drawn on their own here, from the same paths the pad uses.
 */
@Composable
fun ResultMarks(
    stars: Int,
    crown: Boolean,
    modifier: Modifier = Modifier,
    markSize: Dp = 14.dp,
    contentDescription: String? = null,
) {
    val filled = stars.coerceIn(0, MARK_STARS)
    Row(
        modifier = if (contentDescription == null) {
            modifier
        } else {
            modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until MARK_STARS) {
            val color = if (index < filled) RudiColors.PadStar else RudiColors.PadStarOff
            Canvas(modifier = Modifier.size(markSize)) {
                drawPath(path = padStarPath(size.minDimension), color = color)
            }
        }
        if (crown) {
            Spacer(modifier = Modifier.width(4.dp))
            Canvas(modifier = Modifier.size(markSize)) {
                drawPath(path = padCrownPath(size.minDimension), color = RudiColors.PadCrown)
            }
        }
    }
}

private const val MARK_STARS = 3
