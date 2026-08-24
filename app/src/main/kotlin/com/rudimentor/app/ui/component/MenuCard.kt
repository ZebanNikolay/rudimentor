package com.rudimentor.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * A tile on the main menu: a large tappable card with a square pad on the left and
 * the destination title next to it. The pad is always the container (decision 139);
 * inside it sits either a Material Design Icons glyph (decisions 127 and 128) or,
 * for debug tiles, a hand letter. Its lit and tone states carry whether the
 * destination is open, exactly as they do for a letter.
 */
@Composable
fun MenuCard(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    letter: String? = null,
) {
    val shape = RoundedCornerShape(RudiDimens.CardCorner)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .alpha(if (enabled) 1f else 0.7f)
            .background(color = RudiColors.SurfaceAlt, shape = shape)
            .border(width = RudiDimens.PadBorder, color = RudiColors.Line, shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pad(
                size = 44.dp,
                shape = PadShape.Square,
                tone = if (enabled) PadTone.Accent else PadTone.Normal,
                lit = enabled,
                letter = letter,
                iconRes = iconRes,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = if (enabled) RudiColors.Text else RudiColors.Muted,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
