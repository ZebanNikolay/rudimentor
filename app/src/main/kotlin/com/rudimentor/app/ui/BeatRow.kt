package com.rudimentor.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rudimentor.app.audio.BeatPattern

@Composable
fun BeatRow(
    pattern: BeatPattern,
    style: BeatIndicatorStyle,
    mode: com.rudimentor.app.data.PatternMode,
    activeBeat: Int,
    onBeatClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    beatIntervalMs: Int = 500,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pattern.accents.forEachIndexed { index, isAccent ->
            if (index == 4) {
                Spacer(Modifier.width(16.dp))
            } else if (index > 0) {
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                BeatIndicator(
                    style = style,
                    beatNumber = index + 1,
                    isAccent = isAccent,
                    hand = pattern.hands[index],
                    showSticking = mode == com.rudimentor.app.data.PatternMode.RightLeft,
                    isActive = index == activeBeat,
                    beatIntervalMs = beatIntervalMs,
                    onClick = { onBeatClick(index) },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}
