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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The full text of one license, read from the assets of the installed app.
 *
 * Monospace and unwrapped-by-paragraph: a license is a legal text, so it is shown
 * as it was written, without reflowing or trimming it.
 */
@Composable
fun LicenseTextScreen(
    entry: LicenseEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val text = remember(entry.assetPath) { readAsset(context, entry.assetPath) }
    val scroll = rememberScrollState()

    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(title = entry.license.uppercase(), onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RudiColors.SurfaceAlt)
                    .border(1.dp, RudiColors.Line, RoundedCornerShape(14.dp))
                    .verticalScroll(scroll)
                    .padding(14.dp),
            ) {
                Text(
                    text = text,
                    color = RudiColors.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private fun readAsset(context: android.content.Context, path: String): String =
    runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrElse { "Could not read $path." }
