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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.BuildConfig
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelProgress
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankProgress
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.data.levels.WeakStrategy
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.dev.LevelDataSheet
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One level at the difficulty chosen on the map (decision 111). The screen no longer picks a
 * rank or a tempo: the rank is global, and its target BPM belongs to the course data, so the
 * learner sees what the level asks for instead of a stepper that can contradict it.
 *
 * What it shows is what the course data actually holds about this level (decision 152): the
 * name of the exercise, the sticking of every block, the target tempo, the size of one
 * attempt, the best run at this rank, and the notes that explain a level that behaves
 * differently from its neighbours. The dropped "best BPM" metric belonged to the first draft,
 * where the learner chose the tempo by hand.
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
    // Debug builds can read the course data behind the level they are standing on: the
    // screen itself hides the fields that explain unexpected behaviour (decision 145).
    var showLevelData by remember(level.id) { mutableStateOf(false) }
    if (BuildConfig.DEBUG && showLevelData) {
        LevelDataSheet(level = level, family = family, onClose = { showLevelData = false })
        return
    }
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
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    // The exercise, named: every level used to be titled after its family
                    // and read `Single Strokes` (decision 152).
                    text = level.title(family),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                    color = RudiColors.Text,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = level.subtitle(family).uppercase(),
                        style = RudiTextStyles.RowNumber,
                        color = RudiColors.Muted,
                        letterSpacing = 1.4.sp,
                    )
                    if (!level.playable) {
                        Spacer(modifier = Modifier.width(8.dp))
                        LevelTag(stringResource(R.string.level_detail_preview_tag))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = level.purpose(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RudiColors.Muted,
                )

                PatternPreview(
                    level = level,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                )

                TargetTempo(
                    bpm = target.bpm,
                    stars = rankProgress.clampedStars,
                    crown = rankProgress.crown,
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
                        label = stringResource(R.string.level_detail_attempt),
                        value = attemptValue(level, target),
                        caption = attemptCaption(level, target),
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
                        caption = stringResource(
                            if (rankProgress.completed) {
                                R.string.level_detail_cleared
                            } else {
                                R.string.level_detail_not_cleared
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                LevelPlan(level = level, target = target)
                LevelNotes(level = level, target = target, rankProgress = rankProgress)

                if (BuildConfig.DEBUG) {
                    RudiButton(
                        text = "DEBUG · LEVEL DATA",
                        onClick = { showLevelData = true },
                        modifier = Modifier.padding(top = 18.dp),
                        style = RudiButtonStyle.Secondary,
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

/** How much playing one attempt is: notes for a counted lesson, minutes for a timed one. */
@Composable
private fun attemptValue(level: Level, target: RankTarget): String {
    val durationSeconds = level.durationSeconds
    return if (durationSeconds != null) {
        stringResource(R.string.level_detail_attempt_timed, ceil(durationSeconds / 60f).toInt())
    } else {
        stringResource(R.string.level_detail_attempt_notes, level.noteCount(target))
    }
}

/** How long the attempt runs at the tempo of this rank, every ramp phase counted. */
@Composable
private fun attemptCaption(level: Level, target: RankTarget): String? {
    if (level.durationSeconds != null) return null
    val seconds = level.attemptSeconds(target)
    if (seconds <= 0) return null
    return stringResource(R.string.level_detail_attempt_duration, formatSeconds(seconds))
}

/**
 * The tempo the level asks for at the selected rank, plus what the learner earned at that
 * rank: stars as pad LEDs and the crown, drawn the way the map node draws them (decision 126)
 * instead of the gold text stars of the first draft.
 */
@Composable
private fun TargetTempo(
    bpm: Int,
    stars: Int,
    crown: Boolean,
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
        Spacer(modifier = Modifier.height(12.dp))
        Pad(
            size = 44.dp,
            shape = PadShape.Round,
            tone = PadTone.Normal,
            showLetter = false,
            stars = stars,
            crown = crown,
            modifier = Modifier.semantics {
                contentDescription = buildString {
                    append("$stars of ${RankProgress.MAX_STARS} stars")
                    if (crown) append(", crown earned")
                }
            },
        )
    }
}

/**
 * What this level does to the attempt, named: the kind of lesson it is, and the plan it
 * changes mid-attempt when it has one. A tempo ramp and a subdivision switch look like a
 * plain level on this screen otherwise, which is exactly what made SS-01 unreadable
 * (decision 148).
 */
@Composable
private fun LevelPlan(level: Level, target: RankTarget) {
    val kind = stringResource(
        when (level.type) {
            LevelType.Steady -> R.string.level_detail_kind_steady
            LevelType.Isolation -> R.string.level_detail_kind_isolation
            LevelType.Unison -> R.string.level_detail_kind_unison
            LevelType.Transition -> R.string.level_detail_kind_transition
            LevelType.SubdivisionSwitch -> R.string.level_detail_kind_subdivision
            LevelType.TempoRamp -> R.string.level_detail_kind_tempo_ramp
            LevelType.Dynamics -> R.string.level_detail_kind_dynamics
        },
    )
    val ramp = target.tempoRampPlan
    val subdivision = target.subdivisionPlan
    val plan = when {
        ramp != null && ramp.phases.isNotEmpty() -> {
            val tempos = ramp.phases.joinToString(ARROW) { it.bpm.toString() }
            val phaseBeats = ramp.phases.map { it.beatCount }.distinct().singleOrNull()
            if (phaseBeats != null) {
                stringResource(
                    R.string.level_detail_plan_tempo_ramp,
                    tempos,
                    phaseBeats,
                    ramp.repeatCount,
                )
            } else {
                stringResource(
                    R.string.level_detail_plan_tempo_ramp_uneven,
                    tempos,
                    ramp.repeatCount,
                )
            }
        }
        subdivision != null && subdivision.hitsPerBeat.isNotEmpty() -> stringResource(
            R.string.level_detail_plan_subdivision,
            subdivision.hitsPerBeat.joinToString(ARROW),
            subdivision.blockBeats,
        )
        level.phased -> stringResource(
            R.string.level_detail_plan_phases,
            level.phases.size,
            level.phaseRepeats,
        )
        else -> null
    }
    Spacer(modifier = Modifier.height(20.dp))
    SectionLabel(stringResource(R.string.level_detail_plan))
    Text(text = kind, style = RudiTextStyles.Timer, color = RudiColors.Text)
    if (plan != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = plan,
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.Muted,
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    SectionLabel(stringResource(R.string.level_detail_technique))
    Text(
        text = level.techniqueLine(),
        style = MaterialTheme.typography.bodySmall,
        color = RudiColors.Muted,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = RudiTextStyles.RowNumber,
        color = RudiColors.Muted,
        letterSpacing = 1.6.sp,
    )
    Spacer(modifier = Modifier.height(3.dp))
}

/**
 * Everything the learner should know before pressing play, and nothing else: why a level is
 * previewed, why its tempo steps back from the level before it, why its note density jumps,
 * which hand it asks for, and whether this rank is already passed. Those fields used to be
 * readable only in the debug sheet.
 */
@Composable
private fun LevelNotes(
    level: Level,
    target: RankTarget,
    rankProgress: RankProgress,
) {
    val weakFocus = level.lesson.weakFocus
    val notes = buildList {
        if (!level.playable) add(stringResource(R.string.level_detail_preview_body))
        if (weakFocus != null) {
            val hand = weakFocus.authoredWeakHand.storageName
            add(
                stringResource(
                    when (weakFocus.strategy) {
                        WeakStrategy.WeakOnly -> R.string.level_detail_note_weak_only
                        WeakStrategy.WeakLead -> R.string.level_detail_note_weak_lead
                    },
                    hand,
                ),
            )
        }
        if (level.lesson.midCycleSwitch) add(stringResource(R.string.level_detail_note_mid_cycle))
        level.lesson.intentionalRollback?.let {
            add(stringResource(R.string.level_detail_note_rollback, it))
        }
        target.densityException?.let {
            add(stringResource(R.string.level_detail_note_density, it))
        }
        if (rankProgress.completed) add(stringResource(R.string.level_detail_completed_body))
    }
    if (notes.isEmpty()) return
    Spacer(modifier = Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.level_detail_notes))
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

private const val ARROW = " \u2192 "
