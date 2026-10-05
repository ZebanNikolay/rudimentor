package com.rudimentor.app.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.rudimentor.app.R
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The three ways into a level, as a menu that is always open (decision 229).
 *
 * The pattern is the Material FAB menu: the main action is the round button in the corner
 * with its name to its left, and the other actions stand above it as labelled pills,
 * right-aligned to it. It is never collapsed -- three choices are few enough to read at once,
 * and a menu behind a tap would hide the very choice the card is about. Two text buttons and
 * a round one in one row did not leave room for a third.
 *
 * Play scores the attempt and is the only one that can pass the level, so it is the one in
 * the transport button. Practice is the same track without an end; Metronome is the sticking
 * alone, on the metronome, at a tempo of the player's choosing.
 */
@Composable
internal fun LevelModeMenu(
    playEnabled: Boolean,
    playDescription: String,
    onPlay: () -> Unit,
    practiceEnabled: Boolean,
    onPractice: () -> Unit,
    metronomeEnabled: Boolean,
    onMetronome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModePill(
            icon = painterResource(R.drawable.ic_menu_metronome),
            label = stringResource(R.string.level_mode_metronome),
            enabled = metronomeEnabled,
            onClick = onMetronome,
        )
        ModePill(
            icon = rememberVectorPainter(Icons.Filled.AllInclusive),
            label = stringResource(R.string.practice_free_action),
            enabled = practiceEnabled,
            onClick = onPractice,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The name of the main action, as on an extended FAB. The button already
            // speaks for itself to TalkBack, so the label is not announced twice.
            Text(
                text = stringResource(R.string.level_mode_play),
                style = MaterialTheme.typography.titleSmall,
                color = if (playEnabled) RudiColors.Text else RudiColors.Muted,
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .clickable(enabled = playEnabled, onClick = onPlay)
                    .padding(vertical = 8.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            LevelPlayButton(
                onClick = onPlay,
                contentDescription = playDescription,
                active = playEnabled,
            )
        }
    }
}

@Composable
private fun ModePill(
    icon: Painter,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(PILL_HEIGHT / 2)
    Row(
        modifier = Modifier
            .height(PILL_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(RudiColors.Surface, shape)
            .border(1.dp, RudiColors.Line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = RudiColors.BrickLit,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = RudiColors.Text,
        )
    }
}

private val PILL_HEIGHT = 40.dp
private const val DISABLED_ALPHA = 0.4f
