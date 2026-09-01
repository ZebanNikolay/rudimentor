package com.rudimentor.app.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.util.AppLog

/**
 * The diagnostic log, exactly as a feedback mail would carry it.
 *
 * Nothing is attached to a letter that cannot be read here first, and the text is the
 * same call the mail uses -- so what the screen shows and what leaves the device are
 * one string, not two that can drift apart. Monospace, because the log is columns of
 * timestamps.
 */
@Composable
fun DiagnosticLogScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val text = remember { AppLog.diagnosticText() }
    val empty = stringResource(R.string.about_log_empty)
    val copied = stringResource(R.string.about_log_copied)
    val scroll = rememberScrollState()
    var notice by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(title = stringResource(R.string.about_log_title), onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RudiColors.SurfaceAlt)
                    .border(1.dp, RudiColors.Line, RoundedCornerShape(14.dp))
                    .verticalScroll(scroll)
                    .padding(14.dp),
            ) {
                Text(
                    text = text.ifBlank { empty },
                    color = RudiColors.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            RudiButton(
                text = stringResource(R.string.about_log_copy),
                enabled = text.isNotBlank(),
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    notice = copied
                },
            )
            val message = notice
            if (message != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.BrickBright,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
