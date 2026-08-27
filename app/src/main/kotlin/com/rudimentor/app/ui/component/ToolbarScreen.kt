package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The frame every secondary screen uses: the toolbar pinned at the top, the content
 * scrolling underneath it.
 *
 * The rule this component exists to enforce (decision 163): the toolbar must never sit
 * inside the scrolling area. Three screens had grown a single `Column` that carried both
 * `verticalScroll` and the toolbar, so the back button slid off the top as soon as the
 * content moved. Here the scroll modifier can only ever land on the inner column, which
 * takes the leftover height via `weight(1f)`.
 *
 * Screens with their own scrolling body (a `LazyColumn`, a horizontal pager, a fixed
 * layout) pass `scrollable = false` and own the remaining space themselves.
 */
@Composable
fun ToolbarScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    toolbarGap: Int = 16,
    toolbar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(containerColor = RudiColors.Bg) { insets ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(insets)
                .padding(contentPadding),
        ) {
            toolbar()
            Spacer(modifier = Modifier.height(toolbarGap.dp))
            val body = Modifier
                .fillMaxWidth()
                .weight(1f)
            Column(
                modifier = if (scrollable) body.verticalScroll(rememberScrollState()) else body,
                content = content,
            )
        }
    }
}
