package com.rudimentor.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.component.RudiMentorLogo
import com.rudimentor.app.ui.metronome.MetronomeScreen
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens
import com.rudimentor.app.ui.theme.RudiTextStyles

private enum class Screen {
    Menu,
    Metronome,
}

@Composable
fun RudiMentorApp(
    buildInfo: BuildInfo,
    settings: AppSettings,
    onCycleBeat: (Int) -> Unit,
    onToggleHand: (Int) -> Unit,
    onAddBeat: () -> Unit,
    onRemoveBeat: () -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: () -> Unit,
    onSelectRow: (Int) -> Unit,
    onBpmDelta: (Int) -> Unit,
    onShowLettersChange: (Boolean) -> Unit,
) {
    var screenName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    val screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.Menu

    AnimatedContent(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            Screen.Menu -> MainMenu(
                buildInfo = buildInfo,
                onOpenMetronome = { screenName = Screen.Metronome.name },
            )
            Screen.Metronome -> MetronomeScreen(
                settings = settings,
                buildInfo = buildInfo,
                onCycleBeat = onCycleBeat,
                onToggleHand = onToggleHand,
                onAddBeat = onAddBeat,
                onRemoveBeat = onRemoveBeat,
                onAddRow = onAddRow,
                onRemoveRow = onRemoveRow,
                onSelectRow = onSelectRow,
                onBpmDelta = onBpmDelta,
                onShowLettersChange = onShowLettersChange,
                onBack = { screenName = Screen.Menu.name },
            )
        }
    }
}

@Composable
private fun MainMenu(
    buildInfo: BuildInfo,
    onOpenMetronome: () -> Unit,
) {
    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 22.dp, vertical = 26.dp),
        ) {
            RudiMentorLogo(padSize = 26.dp)

            Spacer(modifier = Modifier.height(40.dp))
            MenuCard(
                title = "Metronome",
                letter = "M",
                enabled = true,
                onClick = onOpenMetronome,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = "Levels",
                letter = "L",
                enabled = false,
                onClick = {},
            )

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = RudiColors.Line)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = buildInfo.displayLabel,
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    letter: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(RudiDimens.CardCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(color = RudiColors.SurfaceAlt, shape = shape)
            .border(width = RudiDimens.PadBorder, color = RudiColors.Line, shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pad(
                size = 44.dp,
                shape = PadShape.Square,
                tone = if (enabled) PadTone.Accent else PadTone.Normal,
                lit = enabled,
                letter = letter,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = RudiColors.Text,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
