package com.rudimentor.app.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
 * The sticking map of the level: one card per block, laid out left to right in reading order
 * with an arrow between them, scrolling sideways when the chain is longer than the screen.
 *
 * The text-only map (decision 160) replaced a pad grid that drew the same letters twice, but a
 * stack of bare three-line groups pinned to the left edge read as one long column: nothing said
 * where a block ended, and four blocks pushed the rest of the screen down. Boxing each block and
 * turning the chain sideways keeps the whole map at the height of one card (decision 165).
 */
@Composable
internal fun StickingMap(
    level: Level,
    target: RankTarget,
    modifier: Modifier = Modifier,
) {
    val blocks = level.stickingBlocks(target)
    if (blocks.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            blocks.forEachIndexed { position, block ->
                if (position > 0) {
                    Text(
                        text = stringResource(R.string.level_detail_block_arrow),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RudiColors.Muted,
                    )
                }
                StickingBlockCard(block = block, numbered = blocks.size > 1)
            }
        }
        chainLine(blockCount = blocks.size, passes = level.attemptPasses(target))?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = RudiColors.Muted,
            )
        }
    }
}

/** One block of the chain, boxed like the metric cards so the eye can tell where it ends. */
@Composable
private fun StickingBlockCard(
    block: StickingBlock,
    numbered: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(RudiColors.SurfaceAlt, RoundedCornerShape(12.dp))
            .border(1.dp, RudiColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (numbered) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // The pattern never wraps: a long sticking runs off the card and the row scrolls,
                // which keeps the letters in one line the way they are read.
                text = block.sticking,
                color = RudiColors.Text,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 3.sp,
                softWrap = false,
                maxLines = 1,
            )
            // The multiplier sits on the pattern rather than in the line below it: it counts
            // the words just written, and a block that plays its group once states nothing
            // at all instead of a bare `×1` (decision 204).
            block.repeats?.takeIf { it > 1 }?.let { repeats ->
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.level_detail_block_repeats, repeats),
                    color = RudiColors.BrickBright,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = blockSize(block),
            style = MaterialTheme.typography.bodySmall,
            color = RudiColors.RowNumber,
        )
    }
}

/**
 * `128 hits · 2 per beat`, counted the way the drummer counts it. Note names (`eighth notes`)
 * said the same thing in a vocabulary that has to be translated back into hits per click, and a
 * block whose density moves reads it as the switch it is: `1→2→1 per beat` (decision 204).
 */
@Composable
private fun blockSize(block: StickingBlock): String {
    val density = block.densities.joinToString("→")
    return stringResource(R.string.level_detail_block_hits, block.notes, density)
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
