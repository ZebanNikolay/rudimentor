package com.rudimentor.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The shared base for the brandbook's small square buttons: a pad-shaped
 * square with a Line stroke, ripple in the Text color, `clip(shape)` before
 * `clickable` (otherwise ripple leaks past the rounded corners), and a glyph
 * slot in the middle. Used as the foundation for BackButton, StepperKey, and
 * TempoKey.
 */
@Composable
fun SquareIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    corner: Dp = RudiDimens.StepperButtonCorner,
    background: Color = RudiColors.SurfaceAlt,
    border: Color = RudiColors.Line,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            // Clip before clickable so the ripple follows the rounded shape.
            .clip(shape)
            .background(color = background, shape = shape)
            .border(width = RudiDimens.PadBorder, color = border, shape = shape)
            .clickable(
                enabled = enabled,
                indication = ripple(color = RudiColors.Text),
                interactionSource = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * The back button: the same 32 dp square with the stock Material arrow in it
 * (decision 102). Lives in the shared component so that AppToolbar does not
 * depend on the metronome screen.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SquareIconButton(
        onClick = onClick,
        contentDescription = stringResource(R.string.toolbar_back),
        modifier = modifier,
        size = 32.dp,
        corner = RudiDimens.StepperButtonCorner,
        background = RudiColors.SurfaceAlt,
        border = RudiColors.Line,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = RudiColors.Text,
            modifier = Modifier.size(18.dp),
        )
    }
}
