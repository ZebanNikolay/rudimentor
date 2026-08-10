package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The shared toolbar for secondary screens: a back button on the left, the
 * screen title in the Rubric style next to it, and a free slot on the right
 * for a secondary metric (timer, streak, and so on). The logo is not part of
 * this toolbar -- only the home screen renders it.
 */
@Composable
fun AppToolbar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rightContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = RudiTextStyles.Rubric,
                color = RudiColors.Muted,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = rightContent,
        )
    }
}
