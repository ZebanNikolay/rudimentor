package com.rudimentor.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The loudest thing on a screen: a bordered brick panel with the alert mark, a headline and
 * the reason in one sentence.
 *
 * It exists because the quiet caption of decision 173 was not enough. With the click playing
 * out of the speaker the microphone hears the click itself, and the dev.49 speaker log proved
 * how bad that is: 37 of 88 detected strokes were the app's own click, 15 of them took the
 * place of real notes, and the score printed 71 % for a run that had a different shape
 * entirely. A one-line grey-red caption under a switch does not stop that, so the warning is
 * now a panel that cannot be read past -- and the switch stays available, because a strange
 * routing setup is the learner's business, not the app's (decision 192).
 */
@Composable
fun WarningBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RudiColors.BannerWarnFill, RoundedCornerShape(12.dp))
            .border(1.dp, RudiColors.Brick, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = RudiColors.BrickBright,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RudiColors.BrickBright,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Text,
            )
        }
    }
}
