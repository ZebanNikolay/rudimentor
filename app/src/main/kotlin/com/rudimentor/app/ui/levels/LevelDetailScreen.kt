package com.rudimentor.app.ui.levels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelProgress
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.SquareIconButton
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

@Composable
fun LevelDetailScreen(
    level: Level,
    progress: LevelProgress,
    onBack: () -> Unit,
    onStartPractice: (Level, Int) -> Unit,
) {
    var selectedRankName by rememberSaveable(level.id) { mutableStateOf(RankChoice.Practice.name) }
    var customBpm by rememberSaveable(level.id) {
        mutableIntStateOf(level.target(PracticeRank.Practice).bpm)
    }
    val selectedRank = RankChoice.entries.single { it.name == selectedRankName }
    val selectedTarget = level.target(selectedRank.rank ?: PracticeRank.Practice)
    val bpm = if (selectedRank == RankChoice.Custom) customBpm else selectedTarget.bpm
    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AppToolbar(
                title = stringResource(R.string.level_detail_title, level.id),
                onBack = onBack,
                rightContent = {
                    Text(
                        text = stringResource(R.string.level_detail_rank_meta, selectedRank.displayName, bpm),
                        style = RudiTextStyles.RowNumber,
                        color = RudiColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = level.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                    color = RudiColors.Text,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = level.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RudiColors.Muted,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LevelTags(level = level)

                PatternPreview(
                    level = level,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                )

                BpmPicker(
                    bpm = bpm,
                    custom = selectedRank == RankChoice.Custom,
                    canDecrease = customBpm > CUSTOM_BPM_MIN,
                    canIncrease = customBpm < CUSTOM_BPM_MAX,
                    onDecrease = { customBpm = (customBpm - CUSTOM_BPM_STEP).coerceAtLeast(CUSTOM_BPM_MIN) },
                    onIncrease = { customBpm = (customBpm + CUSTOM_BPM_STEP).coerceAtMost(CUSTOM_BPM_MAX) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                )

                RankGrid(
                    level = level,
                    progress = progress,
                    selected = selectedRank,
                    customBpm = customBpm,
                    onSelect = { selectedRankName = it.name },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.level_detail_best_bpm),
                        value = progress.bestBpm?.toString() ?: stringResource(R.string.level_detail_no_score),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.level_detail_best_score),
                        value = progress.bestScore?.let { stringResource(R.string.level_detail_score_value, it) }
                            ?: stringResource(R.string.level_detail_no_score),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!level.supportsBeatGrid) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.level_detail_unison_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = RudiColors.Muted,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.level_detail_mode).uppercase(),
                        style = RudiTextStyles.RowNumber,
                        color = RudiColors.Muted,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.level_detail_repetitions,
                            selectedTarget.repetitions,
                            selectedTarget.repetitions,
                        ),
                        style = RudiTextStyles.Timer,
                        color = RudiColors.Text,
                    )
                }
                LevelPlayButton(
                    onClick = { onStartPractice(level, bpm) },
                    contentDescription = stringResource(
                        if (level.supportsBeatGrid) {
                            R.string.level_detail_start
                        } else {
                            R.string.level_detail_unison_pending
                        },
                    ),
                    active = level.supportsBeatGrid,
                )
            }
        }
    }
}

@Composable
private fun BpmPicker(
    bpm: Int,
    custom: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (custom) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BpmStepButton(
                    label = "−",
                    contentDescription = stringResource(R.string.level_detail_slower),
                    enabled = canDecrease,
                    onClick = onDecrease,
                )
                BpmStepButton(
                    label = "+",
                    contentDescription = stringResource(R.string.level_detail_faster),
                    enabled = canIncrease,
                    onClick = onIncrease,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = bpm.toString(),
                style = RudiTextStyles.BpmValue.copy(fontSize = 44.sp),
                color = RudiColors.Text,
            )
            Text(
                text = stringResource(R.string.level_detail_bpm).uppercase(),
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
                letterSpacing = 1.8.sp,
            )
        }
    }
}

@Composable
private fun BpmStepButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SquareIconButton(
        onClick = onClick,
        contentDescription = contentDescription,
        size = 44.dp,
        corner = 13.dp,
        background = RudiColors.Surface,
        border = RudiColors.Line,
        enabled = enabled,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = RudiColors.Text,
        )
    }
}

@Composable
private fun RankGrid(
    level: Level,
    progress: LevelProgress,
    selected: RankChoice,
    customBpm: Int,
    onSelect: (RankChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RankChoice.entries.filter { it != RankChoice.Custom }.chunked(2).forEach { rowRanks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowRanks.forEach { rank ->
                    RankOption(
                        rank = rank,
                        selected = selected == rank,
                        trailing = rankStars(progress, rank),
                        onClick = { onSelect(rank) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        RankOption(
            rank = RankChoice.Custom,
            selected = selected == RankChoice.Custom,
            trailing = if (selected == RankChoice.Custom) {
                stringResource(R.string.level_detail_custom_value, customBpm)
            } else {
                stringResource(R.string.level_detail_custom_hint)
            },
            onClick = { onSelect(RankChoice.Custom) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RankOption(
    rank: RankChoice,
    selected: Boolean,
    trailing: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(46.dp)
            .background(
                if (selected) RudiColors.Brick.copy(alpha = 0.12f) else RudiColors.Surface,
                shape,
            )
            .border(1.dp, if (selected) RudiColors.Brick else RudiColors.Line, shape)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(30.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = RudiColors.BrickLit,
                unselectedColor = RudiColors.RowNumber,
            ),
        )
        Text(
            text = rank.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = RudiColors.Text,
            maxLines = 1,
        )
        Text(
            text = trailing,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (rank == RankChoice.Custom) RudiColors.Muted else STAR_COLOR,
            maxLines = 1,
        )
    }
}

private fun rankStars(progress: LevelProgress, rank: RankChoice): String {
    val stars = progress.stars(checkNotNull(rank.rank))
    return "★".repeat(stars) + "☆".repeat(MAX_RANK_STARS - stars)
}

private enum class RankChoice(
    val displayName: String,
    val rank: PracticeRank?,
) {
    Practice("Practice", PracticeRank.Practice),
    Groove("Groove", PracticeRank.Groove),
    Stage("Stage", PracticeRank.Stage),
    Rockstar("Rockstar", PracticeRank.Rockstar),
    Custom("Custom", null),
}

private val STAR_COLOR = androidx.compose.ui.graphics.Color(0xFFE0A94A)
private const val MAX_RANK_STARS = 3
private const val CUSTOM_BPM_MIN = 40
private const val CUSTOM_BPM_MAX = 250
private const val CUSTOM_BPM_STEP = 5
