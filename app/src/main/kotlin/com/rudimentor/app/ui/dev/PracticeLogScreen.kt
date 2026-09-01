package com.rudimentor.app.ui.dev

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rudimentor.app.telemetry.PracticeLogStore
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.util.AppLog
import java.io.File

/**
 * Debug-only journal of practice attempts: every run that started the audio engine,
 * newest first, with its short summary and both log files behind a share button.
 *
 * The screen reads the summaries only -- the JSONL body is never parsed on the phone,
 * it is there to be re-scored on a computer (decision 133).
 *
 * Strings are hard-coded English, the same rule the other dev screens follow.
 */
@Composable
fun PracticeLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var revision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val entries = remember(revision) { PracticeLogStore.list(context) }

    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(title = "PRACTICE LOGS", onBack = onBack)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${entries.size} attempts · keeps the last " +
                    "${PracticeLogStore.MAX_ATTEMPTS}",
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = "No attempts logged yet. Play a level and come back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RudiColors.Muted,
                        )
                    }
                }
                items(entries, key = { it.name }) { entry ->
                    AttemptCard(
                        entry = entry,
                        open = expanded == entry.name,
                        onToggle = {
                            expanded = if (expanded == entry.name) null else entry.name
                        },
                        onCopy = {
                            clipboard.setText(AnnotatedString(entry.summary))
                            notice = "Summary copied."
                        },
                        onCopyAll = {
                            clipboard.setText(
                                AnnotatedString(PracticeLogStore.combinedText(entry)),
                            )
                            notice = "Summary and events copied."
                        },
                        onShare = { notice = shareAttempt(context, entry) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = "Refresh",
                    onClick = { revision += 1 },
                    style = RudiButtonStyle.Secondary,
                )
                RudiButton(
                    text = "Clear all",
                    onClick = {
                        PracticeLogStore.clear(context)
                        expanded = null
                        notice = "Cleared."
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
private fun AttemptCard(
    entry: PracticeLogStore.Entry,
    open: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RudiColors.Surface)
            .border(1.dp, RudiColors.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = entry.name,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            // Collapsed the card shows the verdict line; open it shows the whole summary.
            text = if (open) entry.summary else entry.title,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = RudiColors.Text,
        )
        if (open) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RudiButton(
                        text = "Copy",
                        onClick = onCopy,
                        style = RudiButtonStyle.Secondary,
                    )
                    RudiButton(text = "Share", onClick = onShare)
                }
                // Copying the whole log escapes the share sheet entirely, which is the
                // reliable route when a file manager mangles the attachment.
                RudiButton(
                    text = "Copy all",
                    onClick = onCopyAll,
                    style = RudiButtonStyle.Ghost,
                )
            }
        }
    }
}

/**
 * Writes the summary and the event body into one cached file and opens the system share
 * sheet with it. A single `ACTION_SEND` is used on purpose: file managers and chat apps
 * routinely drop or corrupt one half of a two-file share (decision 154).
 */
private fun shareAttempt(context: Context, entry: PracticeLogStore.Entry): String {
    val file = PracticeLogStore.exportForSharing(context, entry) ?: return "Nothing to share."
    val uri = uriFor(context, file) ?: return "Could not share the file."
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, entry.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Send practice log"))
    return "Shared ${file.name}."
}

private fun uriFor(context: Context, file: File): Uri? = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.onFailure { error ->
    AppLog.error("telemetry", "share failed", error)
}.getOrNull()
