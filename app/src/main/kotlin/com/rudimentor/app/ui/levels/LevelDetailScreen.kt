package com.rudimentor.app.ui.levels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelProgress
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankProgress
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One level at the difficulty chosen on the map (decision 111). The screen no longer picks a
 * rank or a tempo: the rank is global, and its target BPM belongs to the course data, so the
 * learner sees what the level asks for instead of a stepper that can contradict it.
 */
@Composable
fun LevelDetailScreen(
    level: Level,
    family: Family,
    rank: PracticeRank,
    progress: LevelProgress,
    onBack: () -> Unit,
    onStartPractice: (Level, PracticeRank, Int) -> Unit,
) {
    val target = level.target(rank)
    val rankProgress = progress.forRank(rank)
    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AppToolbar(
                // The code, not the bare number: `01` repeats across tracks, so every level
                // read `LEVEL 01` in the header (decision 131).
                title = stringResource(R.string.level_detail_title, level.displayCode),
                onBack = onBack,
                rightContent = {
                    Text(
                        text = stringResource(
                            R.string.level_detail_rank_meta,
                            rank.displayName.uppercase(),
                            target.hitsPerBeat,
                        ),
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
                    text = level.headline(family, rank),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                    color = RudiColors.Text,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = level.blurb(family),
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

                TargetTempo(
                    bpm = target.bpm,
                    stars = rankProgress.clampedStars,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.level_detail_best_bpm),
                        value = rankProgress.bestBpm?.toString()
                            ?: stringResource(R.string.level_detail_no_score),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.level_detail_best_accuracy),
                        value = rankProgress.bestAccuracy
                            ?.let {
                                stringResource(
                                    R.string.level_detail_accuracy_value,
                                    (it * 100f).roundToInt(),
                                )
                            }
                            ?: stringResource(R.string.level_detail_no_score),
                        modifier = Modifier.weight(1f),
                    )
                }

                LevelNotes(level = level, rankProgress = rankProgress, approximated = target.approximated)
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
                        text = executionLabel(level, target),
                        style = RudiTextStyles.Timer,
                        color = RudiColors.Text,
                    )
                }
                LevelPlayButton(
                    onClick = { onStartPractice(level, rank, target.bpm) },
                    contentDescription = stringResource(
                        if (level.playable) {
                            R.string.level_detail_start
                        } else {
                            R.string.level_detail_preview_only
                        },
                    ),
                    active = level.playable,
                )
            }
        }
    }
}

/**
 * Beats or minutes — a lesson is measured one way or the other, never both. A timed lesson
 * states a floor (`≥ 5 min`) because the attempt ends on the next full sticking cycle, and a
 * ramp lesson counts every pass of its plan.
 */
@Composable
private fun executionLabel(level: Level, target: RankTarget): String {
    val durationSeconds = level.durationSeconds
    return if (durationSeconds != null) {
        val minutes = ceil(durationSeconds / 60f).toInt()
        stringResource(R.string.level_detail_execution_timed, minutes, target.hitsPerBeat)
    } else {
        stringResource(
            R.string.level_detail_execution,
            level.beatCount * target.attemptRepeats,
            target.hitsPerBeat,
        )
    }
}

/** The tempo the level asks for at the selected rank, plus the stars earned at that rank. */
@Composable
private fun TargetTempo(
    bpm: Int,
    stars: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "★".repeat(stars) + "☆".repeat(RankProgress.MAX_STARS - stars),
            modifier = Modifier.semantics {
                contentDescription = "$stars of ${RankProgress.MAX_STARS} stars"
            },
            style = RudiTextStyles.Timer,
            color = STAR_COLOR,
            letterSpacing = 3.sp,
        )
    }
}

/** Everything the learner should know before pressing play, and nothing else. */
@Composable
private fun LevelNotes(
    level: Level,
    rankProgress: RankProgress,
    approximated: Boolean,
) {
    val notes = buildList {
        if (!level.playable) add(stringResource(R.string.level_detail_preview_body))
        if (approximated) add(stringResource(R.string.level_detail_approximation_body))
        if (rankProgress.completed) add(stringResource(R.string.level_detail_completed_body))
    }
    if (notes.isEmpty()) return
    Spacer(modifier = Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
    }
}

private val STAR_COLOR = Color(0xFFE0A94A)
