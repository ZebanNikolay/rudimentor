package com.rudimentor.app.ui.dev

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.MenuCard
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.util.DevLog
import java.io.File

/**
 * Debug-only tools screen: the on-device log with a share button, plus the entries
 * to the other dev screens.
 *
 * Strings are hard-coded English on purpose -- this screen never ships in a
 * release build, so it stays out of `strings.xml` (same rule as Mic Lab).
 */
@Composable
fun DevScreen(
    buildInfo: BuildInfo,
    onBack: () -> Unit,
    onOpenMicLab: () -> Unit,
) {
    val context = LocalContext.current
    // Bumped by every action so the tail is re-read instead of being cached.
    var revision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val lines = remember(revision) { DevLog.snapshot() }
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    // Follow the tail: the interesting line is always the last one.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(title = "DEVELOPER", onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))

            MenuCard(
                title = "Mic Lab · calibration",
                letter = "M",
                enabled = true,
                onClick = onOpenMicLab,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Log · ${buildInfo.displayLabel}",
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LogView(
                lines = lines,
                listState = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = "Share log",
                    onClick = {
                        notice = shareLog(context)
                        revision += 1
                    },
                )
                RudiButton(
                    text = "Refresh",
                    onClick = { revision += 1 },
                    style = RudiButtonStyle.Secondary,
                )
                RudiButton(
                    text = "Clear",
                    onClick = {
                        DevLog.clear()
                        notice = null
                        revision += 1
                    },
                    style = RudiButtonStyle.Ghost,
                )
            }
            notice?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun LogView(
    lines: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RudiColors.Surface)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (lines.isEmpty()) {
            item {
                Text(
                    text = "Log is empty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.Muted,
                )
            }
        }
        items(lines.size) { index ->
            Text(
                text = lines[index],
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = RudiColors.Text,
            )
        }
    }
}

/** Copies the log into the shared cache folder and opens the system share sheet. */
private fun shareLog(context: android.content.Context): String {
    val exported = DevLog.exportTo(File(context.cacheDir, "logs"))
        ?: return "Nothing to share yet."
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
    }.getOrElse { error ->
        DevLog.error("dev", "share failed", error)
        return "Could not share: ${error.message}"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, exported.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Send log"))
    return "Shared ${exported.name} (${exported.length() / 1024} KB)."
}
