package com.rudimentor.app.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiMentorLogo
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

private const val REPO_URL = "https://github.com/ZebanNikolay/rudimentor"

/**
 * The About screen: what the app is, how to reach its author, what it honestly does
 * and does not do with the device, and the licenses of everything it borrows.
 *
 * The screen has no settings and no switches -- it is a page to read. Its only two
 * actions are a mail app and a browser; both fall back to the clipboard when the
 * device has neither.
 */
@Composable
fun AboutScreen(
    buildInfo: BuildInfo,
    onBack: () -> Unit,
) {
    var openLicense by remember { mutableStateOf<LicenseEntry?>(null) }
    val entry = openLicense
    if (entry != null) {
        LicenseTextScreen(entry = entry, onBack = { openLicense = null })
        return
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    var notice by remember { mutableStateOf<String?>(null) }

    val feedbackPrompt = stringResource(R.string.about_feedback_prompt)
    val noMailApp = stringResource(R.string.about_no_mail_app)
    val noBrowser = stringResource(R.string.about_no_browser)
    val versionCopied = stringResource(R.string.about_version_copied)

    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AppToolbar(title = stringResource(R.string.about_title), onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
            ) {
                Header(
                    buildInfo = buildInfo,
                    onCopyVersion = {
                        clipboard.setText(AnnotatedString(buildInfo.displayLabel))
                        notice = versionCopied
                    },
                )

                Spacer(modifier = Modifier.height(18.dp))
                AboutSection(title = stringResource(R.string.about_feedback_title)) {
                    Body(stringResource(R.string.about_feedback_body))
                    Spacer(modifier = Modifier.height(12.dp))
                    RudiButton(
                        text = stringResource(R.string.about_feedback_button),
                        onClick = {
                            val sent = FeedbackMail.send(context, buildInfo, feedbackPrompt)
                            if (!sent) {
                                clipboard.setText(AnnotatedString(FeedbackMail.ADDRESS))
                                notice = noMailApp
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Body(FeedbackMail.ADDRESS)
                }

                Spacer(modifier = Modifier.height(14.dp))
                AboutSection(title = stringResource(R.string.about_honest_title)) {
                    Bullets(
                        stringResource(R.string.about_honest_no_business),
                        stringResource(R.string.about_honest_mic),
                        stringResource(R.string.about_honest_on_device),
                        stringResource(R.string.about_honest_author),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                AboutSection(title = stringResource(R.string.about_credits_title)) {
                    Body(stringResource(R.string.about_credits_body))
                }

                Spacer(modifier = Modifier.height(14.dp))
                AboutSection(title = stringResource(R.string.about_open_source_title)) {
                    Body(stringResource(R.string.about_open_source_note))
                    Spacer(modifier = Modifier.height(10.dp))
                    AboutLicenses.entries.forEach { row ->
                        LicenseRow(row = row, onClick = { openLicense = row })
                    }
                    LicenseRow(
                        row = AboutLicenses.notice,
                        onClick = { openLicense = AboutLicenses.notice },
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                AboutSection(title = stringResource(R.string.about_care_title)) {
                    Bullets(
                        stringResource(R.string.about_care_hearing),
                        stringResource(R.string.about_care_hands),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Footer(
                    onOpenRepo = {
                        if (!openLink(context, REPO_URL)) {
                            clipboard.setText(AnnotatedString(REPO_URL))
                            notice = noBrowser
                        }
                    },
                )

                val message = notice
                if (message != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = RudiColors.BrickBright,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(
    buildInfo: BuildInfo,
    onCopyVersion: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RudiMentorLogo(padSize = 22.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Body(stringResource(R.string.about_tagline))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildInfo.displayLabel,
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onCopyVersion)
                .semantics { role = Role.Button }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun Footer(onOpenRepo: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val repoLabel = stringResource(R.string.about_repo)
        Text(
            text = repoLabel,
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.BrickBright,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenRepo)
                .semantics {
                    role = Role.Button
                    contentDescription = repoLabel
                }
                .padding(vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_warranty),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
    }
}

/** A titled card: the Rubric label above a Surface block, as on the other screens. */
@Composable
private fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = RudiTextStyles.Rubric, color = RudiColors.Muted)
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RudiDimens.CardCorner))
                .background(RudiColors.SurfaceAlt)
                .border(1.dp, RudiColors.Line, RoundedCornerShape(RudiDimens.CardCorner))
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = RudiColors.Muted,
    )
}

@Composable
private fun Bullets(vararg lines: String) {
    lines.forEachIndexed { index, line ->
        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "·",
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.BrickBright,
            )
            Text(
                text = "  $line",
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.Muted,
            )
        }
    }
}

@Composable
private fun LicenseRow(
    row: LicenseEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = row.component,
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Text(
            text = row.license,
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
    }
}
