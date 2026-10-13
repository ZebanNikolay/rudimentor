package com.rudimentor.app.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.rudimentor.app.ui.component.TransportSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * The pattern is the Material FAB menu: the main action is the round button in the corner,
 * the other two are small round buttons stacked above it on the same axis, and every button
 * has its name to its left. Pills with the icon first read as a different control from the
 * Play button and left the icons out of line (decision 232).
 *
 * It is never collapsed -- three choices are few enough to read at once,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
            )
            Spacer(modifier = Modifier.width(LABEL_GAP))
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
    // A small round button in the column of the big one, its name to its left: the label and
    // the circle are one target, and every circle of the menu sits on one vertical axis with
    // the Play button (decision 232).
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(SMALL_SIZE / 2))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = RudiColors.Text,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(modifier = Modifier.width(LABEL_GAP))
        Box(modifier = Modifier.width(TransportSize.Small.buttonSize), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(SMALL_SIZE)
                    .clip(CircleShape)
                    .background(RudiColors.Surface, CircleShape)
                    .border(1.dp, RudiColors.Line, CircleShape),
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

private val SMALL_SIZE = TransportSize.Tiny.buttonSize
private val LABEL_GAP = 12.dp
private const val DISABLED_ALPHA = 0.4f
