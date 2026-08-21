package com.rudimentor.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The settings drawer of the landscape screens: the shared [SettingsHandle] glued
 * to the trailing edge. Opening it slides the screen to the left instead of
 * covering it, because in landscape a bottom sheet would eat the whole track
 * (decision 88).
 *
 * The panel is one solid plate that runs from edge to edge and is only rounded on
 * the side it slides out of. Window insets are kept inside the plate: padding the
 * drawer itself left gaps around the panel and the shifted screen showed through
 * them (decision 102).
 *
 * Shared by the practice screen and the result screen -- same handle, same panel.
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
        val panelShape = RoundedCornerShape(topStart = PANEL_CORNER, bottomStart = PANEL_CORNER)

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
                .clip(panelShape)
                .background(color = RudiColors.SurfaceAlt, shape = panelShape)
                .border(width = 1.dp, color = RudiColors.Line, shape = panelShape)
                .stageSafePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Top,
            content = panel,
        )

        SettingsHandle(
            open = open,
            edge = SettingsHandleEdge.End,
            onClick = { onOpenChange(!open) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = -panelWidthPx * progress },
        )
    }
}

private const val PANEL_FRACTION = 0.46f
private const val SHIFT_FRACTION = 0.42f
private val PANEL_CORNER = 18.dp
