package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The Material 3 secondary tab row in brandbook colours — and nothing else.
 *
 * The look is the stock one: bare text with the selected tab underlined in the accent colour.
 * No card, no border, no rounded corners, no background behind a tab. Material owns the parts
 * that are easy to get wrong by hand: equal slots for a small fixed tab set (decision 117), the
 * animated indicator, and the `Role.Tab` plus selected state it reports to accessibility.
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
        contentColor = RudiColors.Brick,
        // The one piece of decoration: the accent underline under the selected tab.
        indicator = { tabPositions ->
            tabPositions.getOrNull(selectedTabIndex)?.let { position ->
                with(TabRowDefaults) {
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(position),
                        height = 2.dp,
                        color = RudiColors.Brick,
                    )
                }
            }
        },
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
        modifier = modifier,
        selectedContentColor = titleColor,
        unselectedContentColor = titleColor,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 8.dp)
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
