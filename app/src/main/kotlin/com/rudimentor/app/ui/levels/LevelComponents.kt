package com.rudimentor.app.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelModifier
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.ui.component.TransportButton
import com.rudimentor.app.ui.component.TransportSize
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * The sticking map of the level, as text (decision 153). The first draft drew the pattern
 * twice — a grid of pads and the same letters underneath — and the grid took a third of the
 * screen without adding anything. What was missing is structure, so every block now states
 * its pattern, how many notes it is at this rank and how many times the pattern runs.
 */
@Composable
internal fun StickingMap(
    level: Level,
    target: RankTarget,
    modifier: Modifier = Modifier,
) {
    val blocks = level.stickingBlocks(target)
    if (blocks.isEmpty()) return
    val passes = level.attemptPasses(target)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (blocks.size > 1) {
                    Text(
                        text = stringResource(
                            R.string.level_detail_phase_label,
                            block.index + 1,
                            block.beats,
                        ).uppercase(),
                        style = RudiTextStyles.RowNumber,
                        color = RudiColors.Muted,
                        letterSpacing = 1.4.sp,
                    )
                }
                Text(
                    text = block.sticking,
                    color = RudiColors.Text,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    letterSpacing = 3.sp,
                    lineHeight = 24.sp,
                )
                Text(
                    text = blockSize(block),
                    style = MaterialTheme.typography.bodySmall,
                    color = RudiColors.RowNumber,
                )
            }
        }
        chainLine(blockCount = blocks.size, passes = passes)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
    }
}

/** `32 sixteenth notes · pattern ×8`, the density named the way a drummer counts it. */
@Composable
private fun blockSize(block: StickingBlock): String {
    val density = when (block.hitsPerBeat) {
        1 -> R.string.level_detail_density_quarters
        2 -> R.string.level_detail_density_eighths
        3 -> R.string.level_detail_density_eighth_triplets
        4 -> R.string.level_detail_density_sixteenths
        6 -> R.string.level_detail_density_sixteenth_triplets
        8 -> R.string.level_detail_density_thirty_seconds
        else -> null
    }
    val notes = if (density == null) {
        stringResource(R.string.level_detail_block_notes, block.notes)
    } else {
        stringResource(R.string.level_detail_block_notes_named, block.notes, stringResource(density))
    }
    val cycles = block.cycles ?: return notes
    return stringResource(R.string.level_detail_block_cycles, notes, cycles)
}

/** How the blocks add up to one attempt, when there is more than one pass to state. */
@Composable
private fun chainLine(blockCount: Int, passes: Int): String? = when {
    passes <= 1 -> null
    blockCount > 1 -> stringResource(R.string.level_detail_sticking_chain, blockCount, passes)
    else -> stringResource(R.string.level_detail_phase_repeats, passes)
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

/**
 * One fact about the level or about the learner's best run at it, boxed. [trailing] fills the
 * empty space to the right of the value: the best-run card puts the earned stars and the crown
 * there (decision 153), where they read as part of the result instead of as marks floating on a
 * pad of their own.
 */
@Composable
internal fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: (@Composable () -> Unit)? = null,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                color = RudiColors.Text,
                modifier = Modifier.weight(1f, fill = trailing != null),
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
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
