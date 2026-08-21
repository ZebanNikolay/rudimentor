package com.rudimentor.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/** Which edge of the screen the handle is glued to. */
enum class SettingsHandleEdge {
    /** Portrait screens: chevron up and the word under it, on the bottom edge. */
    Bottom,

    /** Landscape screens: chevron and the word on its side, on the trailing edge. */
    End,
}

/**
 * The one way into the settings of a screen: a chevron and the word SETTINGS, both
 * in Muted, drawn straight on the background. No plate, no border -- the metronome
 * handle is the reference and the landscape screens use the very same component,
 * only turned on its side (decision 102).
 */
@Composable
fun SettingsHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    edge: SettingsHandleEdge = SettingsHandleEdge.Bottom,
    open: Boolean = false,
) {
    val label = stringResource(R.string.metronome_settings_title)
    val cd = stringResource(R.string.metronome_open_settings)
    val clickable = Modifier.clickable(
        indication = ripple(color = RudiColors.Text),
        interactionSource = null,
        onClick = onClick,
    )
    when (edge) {
        SettingsHandleEdge.Bottom -> Column(
            modifier = modifier
                .fillMaxWidth()
                .then(clickable)
                .padding(top = 8.dp, bottom = 14.dp)
                .semantics { contentDescription = cd },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = RudiColors.Muted,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(modifier = Modifier.height(2.dp))
            HandleLabel(label = label)
        }

        SettingsHandleEdge.End -> Row(
            modifier = modifier
                .then(clickable)
                .padding(horizontal = 6.dp, vertical = 12.dp)
                .semantics { contentDescription = cd },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Icon(
                imageVector = if (open) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = RudiColors.Muted,
                modifier = Modifier.size(ICON_SIZE),
            )
            HandleLabel(label = label, modifier = Modifier.rotateVertical())
        }
    }
}

@Composable
private fun HandleLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = RudiTextStyles.Rubric,
        color = RudiColors.Muted,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
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

private val ICON_SIZE = 18.dp
