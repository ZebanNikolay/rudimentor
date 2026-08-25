package com.rudimentor.app.ui.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.describeLevel
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * Debug-only reading of the course data behind the level you are standing on.
 *
 * The level screen shows what a learner needs; this shows what the package says, so a level
 * that behaves unexpectedly can be explained without a computer. Copy puts the same text on
 * the clipboard.
 *
 * Strings are hard-coded English, the same rule the other dev screens follow.
 */
@Composable
fun LevelDataSheet(
    level: Level,
    family: Family,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val dump = remember(level.id) { describeLevel(level, family) }

    BackHandler(onBack = onClose)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(title = "LEVEL DATA", onBack = onClose)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = level.displayCode,
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = dump,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                color = RudiColors.Text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RudiButton(
                    text = "COPY",
                    onClick = { clipboard.setText(AnnotatedString(dump)) },
                    modifier = Modifier.weight(1f),
                    style = RudiButtonStyle.Secondary,
                )
                RudiButton(
                    text = "CLOSE",
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    style = RudiButtonStyle.Ghost,
                )
            }
        }
    }
}
