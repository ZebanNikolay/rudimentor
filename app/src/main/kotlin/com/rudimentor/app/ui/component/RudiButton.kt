package com.rudimentor.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors

/** The three weights of a text action in the brandbook. */
enum class RudiButtonStyle {
    /** The one action the screen wants: brick fill. */
    Primary,

    /** An equal alternative: Surface fill with a Line stroke. */
    Secondary,

    /** A quiet aside: no fill, no stroke. */
    Ghost,
}

/**
 * The shared text button. Same silhouette as the pads -- rounded square corners,
 * ripple in the Text color, clip before clickable -- so the result screen does
 * not invent a button of its own.
 */
@Composable
fun RudiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RudiButtonStyle = RudiButtonStyle.Primary,
    enabled: Boolean = true,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(BUTTON_CORNER)
    val background = when (style) {
        RudiButtonStyle.Primary -> RudiColors.Brick
        RudiButtonStyle.Secondary -> RudiColors.Surface
        RudiButtonStyle.Ghost -> Color.Transparent
    }
    val border = when (style) {
        RudiButtonStyle.Primary -> RudiColors.BrickLit
        RudiButtonStyle.Secondary -> RudiColors.Line
        RudiButtonStyle.Ghost -> Color.Transparent
    }
    val content = when (style) {
        RudiButtonStyle.Primary -> RudiColors.Text
        RudiButtonStyle.Secondary -> RudiColors.Text
        RudiButtonStyle.Ghost -> RudiColors.Muted
    }
    // A disabled button used to be pixel-identical to a live one: same fill, same border,
    // same text, only the click did nothing. On the calibration screen that read as a
    // broken button rather than as one that is not ready yet (decision 159).
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .clip(shape)
            .background(color = background.copy(alpha = background.alpha * alpha), shape = shape)
            .border(
                width = 1.dp,
                color = border.copy(alpha = border.alpha * alpha),
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) content else RudiColors.Muted,
            maxLines = 1,
        )
    }
}

/** How far a disabled button is faded: clearly dimmer, still legible. */
private const val DISABLED_ALPHA = 0.4f

private val BUTTON_HEIGHT = 44.dp
private val BUTTON_CORNER = 13.dp
