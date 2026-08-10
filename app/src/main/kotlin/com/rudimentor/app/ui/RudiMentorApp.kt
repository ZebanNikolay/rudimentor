package com.rudimentor.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rudimentor.app.BuildConfig
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.ui.component.MenuCard
import com.rudimentor.app.ui.component.RudiMentorLogo
import com.rudimentor.app.ui.metronome.MetronomeActions
import com.rudimentor.app.ui.metronome.MetronomeScreen
import com.rudimentor.app.ui.miclab.MicLabScreen
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/** Top-level destinations. Added to as new sections come online. */
private enum class Screen {
    Menu,
    Metronome,
    MicLab,
}

@Composable
fun RudiMentorApp(
    buildInfo: BuildInfo,
    settings: AppSettings,
    actions: MetronomeActions,
) {
    var screenName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    val screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.Menu

    AnimatedContent(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            Screen.Menu -> MainMenu(
                buildInfo = buildInfo,
                onOpenMetronome = { screenName = Screen.Metronome.name },
                onOpenMicLab = { screenName = Screen.MicLab.name },
            )
            Screen.Metronome -> MetronomeScreen(
                settings = settings,
                buildInfo = buildInfo,
                actions = actions,
                onBack = { screenName = Screen.Menu.name },
            )
            Screen.MicLab -> MicLabScreen(
                buildInfo = buildInfo,
                onBack = { screenName = Screen.Menu.name },
            )
        }
    }
}

@Composable
private fun MainMenu(
    buildInfo: BuildInfo,
    onOpenMetronome: () -> Unit,
    onOpenMicLab: () -> Unit,
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
                title = stringResource(R.string.menu_metronome),
                letter = stringResource(R.string.menu_metronome_letter),
                enabled = true,
                onClick = onOpenMetronome,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = stringResource(R.string.menu_levels),
                letter = stringResource(R.string.menu_levels_letter),
                enabled = false,
                onClick = {},
            )
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                MenuCard(
                    title = "Mic Lab · dev",
                    letter = "D",
                    enabled = true,
                    onClick = onOpenMicLab,
                )
            }

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
