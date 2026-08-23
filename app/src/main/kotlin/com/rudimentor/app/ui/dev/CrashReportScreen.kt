package com.rudimentor.app.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.util.DevLog
import java.io.File

/**
 * Startup-safe crash report. Shown instead of the app when the previous launch died
 * or when this launch cannot even build its data, so a field report can leave the
 * device without a cable: the trace is selectable, copyable and shareable.
 *
 * English on purpose, like the other developer surfaces.
 */
@Composable
fun CrashReportScreen(
    title: String,
    buildLabel: String,
    report: String,
    onContinue: (() -> Unit)?,
) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(text = title, style = RudiTextStyles.RowNumber, color = RudiColors.Text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildLabel,
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
            Spacer(modifier = Modifier.height(12.dp))

            SelectionContainer(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RudiColors.Surface)
                        .border(1.dp, RudiColors.Line, RoundedCornerShape(10.dp))
                        .verticalScroll(scroll)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = report,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = RudiColors.Text,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RudiButton(
                    text = "Copy",
                    onClick = { notice = copyReport(context, "$buildLabel\n$report") },
                )
                RudiButton(
                    text = "Share",
                    onClick = { notice = shareCrashLog(context) },
                    style = RudiButtonStyle.Secondary,
                )
                onContinue?.let { continueToApp ->
                    RudiButton(
                        text = "Continue",
                        onClick = continueToApp,
                        style = RudiButtonStyle.Ghost,
                    )
                }
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

/** Puts the whole report on the clipboard, for pasting into a chat right away. */
private fun copyReport(context: Context, text: String): String {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return "No clipboard on this device."
    clipboard.setPrimaryClip(ClipData.newPlainText("RudiMentor crash", text))
    return "Copied to the clipboard."
}

/** Same route as the developer screen: export a copy, then open the share sheet. */
private fun shareCrashLog(context: Context): String {
    val exported = DevLog.exportTo(File(context.cacheDir, "logs"))
        ?: return "Nothing to share yet."
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
    }.getOrElse { error ->
        return "Could not share: ${error.message}"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, exported.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Send crash log"))
    return "Shared ${exported.name} (${exported.length() / 1024} KB)."
}
