package com.rudimentor.app.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.component.TransportSize
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The three ways into a level, in the Material FAB-menu pattern (decisions 229, 232, 235).
 *
 * Play is the main action: the big round button in the bottom-right corner with its name to
 * its left. It is the only mode that can pass the level. Practice -- the same track without
 * an end -- and Metronome -- the sticking alone, at a tempo of the player's choosing -- are
 * small round buttons stacked above it on the same vertical axis, each with its name to its
 * left.
 *
 * The two are split so the small ones can float. [LevelPlayRow] lives in the bottom strip of
 * the card; [LevelSecondaryModes] is drawn over the scrolling content, the way a FAB menu
 * floats, instead of reserving a block of the screen of its own. Its labels sit on a chip of
 * the background so they stay legible over whatever scrolls beneath.
 *
 * Every label presses its button: the label clicks without a ripple of its own and the
 * ripple shows on the circle -- not across the empty space between them.
 */
@Composable
internal fun LevelSecondaryModes(
    practiceEnabled: Boolean,
    onPractice: () -> Unit,
    metronomeEnabled: Boolean,
    onMetronome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(STACK_GAP),
    ) {
        SecondaryMode(
            icon = painterResource(R.drawable.ic_menu_metronome),
            label = stringResource(R.string.level_mode_metronome),
            enabled = metronomeEnabled,
            onClick = onMetronome,
        )
        SecondaryMode(
            icon = rememberVectorPainter(Icons.Filled.AllInclusive),
            label = stringResource(R.string.practice_free_action),
            enabled = practiceEnabled,
            onClick = onPractice,
        )
    }
}

/** The main action: `Play level` and the transport button, one target with one ripple. */
@Composable
internal fun LevelPlayRow(
    enabled: Boolean,
    description: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // The button already speaks for itself to TalkBack, so the label is not announced twice.
        ModeLabel(
            text = stringResource(R.string.level_mode_play),
            enabled = enabled,
            modifier = Modifier
                .clearAndSetSemantics { }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onPlay,
                ),
        )
        Spacer(modifier = Modifier.width(LABEL_GAP))
        LevelPlayButton(
            onClick = onPlay,
            contentDescription = description,
            active = enabled,
            interactionSource = interaction,
        )
    }
}

@Composable
private fun SecondaryMode(
    icon: Painter,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            // One target for the label and the circle; the ripple is drawn on the circle only.
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeLabel(text = label, enabled = true)
        Spacer(modifier = Modifier.width(LABEL_GAP))
        // A column as wide as the Play button, so every circle shares its axis.
        Box(
            modifier = Modifier.width(TransportSize.Small.buttonSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(SMALL_SIZE)
                    .clip(CircleShape)
                    .background(RudiColors.Surface, CircleShape)
                    .border(1.dp, RudiColors.Line, CircleShape)
                    .indication(interaction, ripple(color = RudiColors.Text)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = RudiColors.BrickLit,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** A mode name on a chip of the background, legible over content that scrolls beneath it. */
@Composable
private fun ModeLabel(text: String, enabled: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (enabled) RudiColors.Text else RudiColors.Muted,
        maxLines = 1,
        modifier = modifier
            .background(RudiColors.Bg.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private val SMALL_SIZE = TransportSize.Tiny.buttonSize
private val LABEL_GAP = 10.dp
private val STACK_GAP = 10.dp
private const val DISABLED_ALPHA = 0.4f
