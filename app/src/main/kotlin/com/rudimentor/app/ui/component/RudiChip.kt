package com.rudimentor.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The small uppercase chip of the HUD: rank, tempo, hits per beat, and the rank
 * badge on the result screen. [accent] paints the brick variant used for the
 * rank badge.
 */
@Composable
fun RudiChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val shape = RoundedCornerShape(CHIP_CORNER)
    Text(
        text = text,
        style = RudiTextStyles.Rubric,
        color = if (accent) RudiColors.Text else RudiColors.Muted,
        modifier = modifier
            .background(
                color = if (accent) RudiColors.Brick.copy(alpha = 0.22f) else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (accent) RudiColors.Brick else RudiColors.Line,
                shape = shape,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

private val CHIP_CORNER = 7.dp
