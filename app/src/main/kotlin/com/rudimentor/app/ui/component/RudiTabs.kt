package com.rudimentor.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The Material 3 tab row in brandbook clothes.
 *
 * Material owns the parts that are easy to get wrong by hand: it splits the width into equal
 * slots (decision 117), reports `Role.Tab` and the selected state to accessibility services,
 * and animates the selection. What it must not own is the look, so the indicator line and the
 * divider are switched off and every tab draws itself as a bordered card instead.
 */
@Composable
fun RudiTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        contentColor = RudiColors.Text,
        indicator = {},
        divider = {},
        tabs = tabs,
    )
}

/**
 * One tab: a title and, under it, an optional second line — the family figure spelled in R/L
 * letters (decision 120). Families have no icons, so the letters carry the meaning; they come
 * from the family model, never from this composable.
 *
 * [description] replaces the two text lines for screen readers, because letters must be spoken
 * as words ("right left right left"), not as an alphabet soup.
 */
@Composable
fun RudiTab(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TAB_CORNER)
    val titleColor = when {
        selected -> RudiColors.Brick
        enabled -> RudiColors.Muted
        else -> RudiColors.RowNumber
    }
    // The second line never shouts louder than the title: it repeats the title colour when the
    // tab is selected and stays a step darker than it otherwise.
    val subtitleColor = if (selected) titleColor else RudiColors.RowNumber
    Tab(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(horizontal = TAB_GAP / 2)
            .clip(shape),
        selectedContentColor = titleColor,
        unselectedContentColor = titleColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (selected) RudiColors.SurfaceAlt else Color.Transparent,
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = if (selected) RudiColors.Brick else RudiColors.Line,
                    shape = shape,
                )
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = RudiTextStyles.TabTitle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = RudiTextStyles.TabSticking,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Material fills the row edge to edge, so the gap between tabs is padding inside each slot. */
private val TAB_GAP = 6.dp
private val TAB_CORNER = 10.dp
