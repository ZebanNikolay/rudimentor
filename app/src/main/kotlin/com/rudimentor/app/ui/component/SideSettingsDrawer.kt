package com.rudimentor.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The settings drawer of the landscape screens: a chevron glued to the right edge
 * with the word SETTINGS turned on its side beside it. Opening it slides the screen
 * to the left instead of covering it, because in landscape a bottom sheet would
 * eat the whole track (decision 88).
 *
 * Shared by the practice screen and the result screen -- same tab, same panel.
 */
@Composable
fun SideSettingsDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "settingsDrawer",
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val panelWidth = maxWidth * PANEL_FRACTION
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val shiftPx = totalWidthPx * SHIFT_FRACTION * progress

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -shiftPx },
        ) {
            content()
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(panelWidth)
                .graphicsLayer { translationX = panelWidthPx * (1f - progress) }
                .background(RudiColors.SurfaceAlt)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            content = panel,
        )

        SettingsTab(
            open = open,
            onClick = { onOpenChange(!open) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = -panelWidthPx * progress },
        )
    }
}

@Composable
private fun SettingsTab(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    val cd = stringResource(R.string.practice_settings_tab_cd)
    // Two columns, as in the concept: the chevron on the outside, the rotated word
    // beside it. Stacking single letters was unreadable on the device.
    Row(
        modifier = modifier
            .clip(shape)
            .background(color = RudiColors.Surface, shape = shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
            .padding(horizontal = 5.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(modifier = Modifier.width(9.dp).height(14.dp)) {
            val stroke = 1.8.dp.toPx()
            val inset = stroke / 2f
            val tipX = if (open) this.size.width - inset else inset
            val baseX = if (open) inset else this.size.width - inset
            drawLine(
                color = RudiColors.Text,
                start = Offset(baseX, inset),
                end = Offset(tipX, this.size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = RudiColors.Text,
                start = Offset(tipX, this.size.height / 2f),
                end = Offset(baseX, this.size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = stringResource(R.string.practice_settings_title),
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.rotateVertical(),
        )
    }
}

/**
 * Turns a composable a quarter turn clockwise and swaps its measured size, so a
 * one-line label lays out as a narrow vertical column instead of being clipped.
 */
private fun Modifier.rotateVertical(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        )
    )
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        )
    }
}.rotate(90f)

private const val PANEL_FRACTION = 0.46f
private const val SHIFT_FRACTION = 0.42f
