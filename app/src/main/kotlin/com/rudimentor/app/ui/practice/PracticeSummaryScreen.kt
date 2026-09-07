package com.rudimentor.app.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.RudiChip
import com.rudimentor.app.ui.levels.title
import com.rudimentor.app.ui.stageSafePadding
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeSummaryScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    bpm: Int,
    summary: PracticeSummary,
    onPlayLevel: () -> Unit,
    onPracticeAgain: () -> Unit,
    onToMap: () -> Unit,
) {
    BackHandler(onBack = onToMap)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RudiColors.Bg)
            .stageSafePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.practice_summary_title),
                style = MaterialTheme.typography.titleLarge,
                color = RudiColors.Text,
            )
            Spacer(modifier = Modifier.height(10.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val chipsMaxWidth = maxWidth * 0.65f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "${level.title(family)} · ${level.displayCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = RudiColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FlowRow(
                        modifier = Modifier.widthIn(max = chipsMaxWidth),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RudiChip(text = stringResource(R.string.run_mode_practice), accent = true)
                        RudiChip(text = stringResource(R.string.practice_rank, rank.name.uppercase()))
                        RudiChip(text = stringResource(R.string.practice_bpm, bpm))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SummaryMetric(
                    label = stringResource(R.string.practice_summary_duration),
                    value = formatPracticeDuration(summary.durationMs),
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = stringResource(R.string.practice_summary_hits),
                    value = summary.hits.coerceAtLeast(0).toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RudiButton(
                text = stringResource(R.string.practice_result_map),
                onClick = onToMap,
                style = RudiButtonStyle.Ghost,
            )
            RudiButton(
                text = stringResource(R.string.practice_free_again),
                onClick = onPracticeAgain,
                style = RudiButtonStyle.Secondary,
            )
            RudiButton(
                text = stringResource(R.string.practice_play_level),
                onClick = onPlayLevel,
            )
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = RudiTextStyles.Rubric,
            color = RudiColors.Muted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = RudiTextStyles.BpmValue.copy(fontSize = 36.sp, lineHeight = 40.sp),
            color = RudiColors.Text,
        )
    }
}
