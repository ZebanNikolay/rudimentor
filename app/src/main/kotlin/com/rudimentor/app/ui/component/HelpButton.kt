package com.rudimentor.app.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * A question mark that opens the explanation of the block it sits in.
 *
 * Every step of the sound check used to carry its instructions as a paragraph above the
 * controls, and the screen turned into a wall of prose nobody reads twice: on the third
 * visit the learner already knows what a step does and only wants the buttons. The help
 * behind an icon keeps the long form for the first visit and gives the screen back to the
 * controls afterwards -- Material's own pattern for optional explanation (decision 174).
 */
@Composable
fun HelpButton(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = RudiColors.Muted,
) {
    var open by remember { mutableStateOf(false) }
    SquareIconButton(
        onClick = { open = true },
        contentDescription = stringResource(R.string.help_open, title),
        modifier = modifier,
        size = 30.dp,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_help),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = RudiColors.Surface,
            title = {
                Text(
                    text = title,
                    style = RudiTextStyles.Rubric,
                    color = RudiColors.Text,
                )
            },
            text = {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RudiColors.Text,
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(text = stringResource(R.string.help_close))
                }
            },
        )
    }
}