package com.rudimentor.app.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelModifier
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PatternStep
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.component.TransportButton
import com.rudimentor.app.ui.component.TransportSize
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The sticking of the level, drawn as pads. Every block of the attempt is shown, not only the
 * first one: a transition level plays a chain of patterns, and the preview used to draw the
 * first phase twice instead (decision 152). A long pattern wraps at [PATTERN_ROW_STEPS] pads
 * so a 16-step figure fits the screen.
 */
@Composable
internal fun PatternPreview(
    level: Level,
    modifier: Modifier = Modifier,
) {
    val blocks = level.phases.filter { it.steps.isNotEmpty() }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        blocks.forEach { phase ->
            PatternBlock(
                steps = phase.steps,
                label = if (blocks.size > 1) {
                    stringResource(
                        R.string.level_detail_phase_label,
                        phase.index + 1,
                        phase.beatCount,
                    )
                } else {
                    null
                },
            )
        }
        if (blocks.size > 1 && level.phaseRepeats > 1) {
            Text(
                text = stringResource(R.string.level_detail_phase_repeats, level.phaseRepeats),
                style = RudiTextStyles.RowNumber,
                color = RudiColors.RowNumber,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

@Composable
private fun PatternBlock(
    steps: List<PatternStep>,
    label: String?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
                letterSpacing = 1.4.sp,
            )
        }
        steps.chunked(PATTERN_ROW_STEPS).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { step -> PatternStepPad(step) }
            }
        }
        Text(
            text = steps
                .chunked(STICKING_GROUP)
                .joinToString(" · ") { chunk -> chunk.joinToString("") { it.label } },
            color = RudiColors.RowNumber,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 2.2.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PatternStepPad(step: PatternStep) {
    // The package marks accents per lesson, not per step, so every step renders unaccented.
    val tone = PadTone.Normal
    // A rest keeps the slot in the pattern, drawn as a dark pad with no letter.
    if (step.rest) {
        Pad(
            size = 31.dp,
            shape = PadShape.Square,
            tone = PadTone.Mute,
            showLetter = false,
        )
        return
    }
    if (step.hands.size == 1) {
        val hand = step.hands.single()
        Pad(
            size = 31.dp,
            shape = if (hand == PatternHand.Left) PadShape.Round else PadShape.Square,
            tone = tone,
            letter = hand.storageName,
            letterFraction = 0.34f,
        )
        return
    }

    Box(modifier = Modifier.size(36.dp)) {
        Pad(
            size = 27.dp,
            shape = PadShape.Square,
            tone = tone,
            letter = PatternHand.Right.storageName,
            letterFraction = 0.3f,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Pad(
            size = 27.dp,
            shape = PadShape.Round,
            tone = tone,
            letter = PatternHand.Left.storageName,
            letterFraction = 0.3f,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * A level the engine cannot run yet says so above the fold: the play button is disabled and
 * the reason used to live only in the notes at the bottom (decision 131).
 */
@Composable
internal fun LevelTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier
            .border(1.dp, RudiColors.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = RudiTextStyles.RowNumber,
        color = RudiColors.Muted,
        letterSpacing = 0.8.sp,
    )
}

@Composable
internal fun LevelPlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    // One transport button in the app, in its small size (decision 102): the level
    // card used to draw its own copy and the two drifted apart.
    TransportButton(
        playing = false,
        onClick = onClick,
        modifier = modifier,
        size = TransportSize.Small,
        accentIdle = true,
        enabled = active,
        contentDescription = contentDescription,
    )
}

/** One fact about the level or about the learner's best run at it, boxed. */
@Composable
internal fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(
        modifier = modifier
            .background(RudiColors.SurfaceAlt, RoundedCornerShape(12.dp))
            .border(RudiDimens.PadBorder, RudiColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            letterSpacing = 1.4.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
            color = RudiColors.Text,
        )
        if (caption != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.RowNumber,
            )
        }
    }
}

internal val LevelType.displayName: String
    get() = when (this) {
        LevelType.Steady -> "Steady"
        LevelType.Isolation -> "Isolation"
        LevelType.Unison -> "Unison"
        LevelType.Transition -> "Transition"
        LevelType.SubdivisionSwitch -> "Subdivision switch"
        LevelType.TempoRamp -> "Tempo ramp"
        LevelType.Dynamics -> "Dynamics"
    }

internal val LevelModifier.displayName: String
    get() = when (this) {
        LevelModifier.Weak -> "Weak"
        LevelModifier.Endurance -> "Endurance"
    }

private const val PATTERN_ROW_STEPS = 8

private const val STICKING_GROUP = 4
