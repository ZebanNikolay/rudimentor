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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudimentor.app.BuildConfig
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.R
import com.rudimentor.app.audio.AudioOutputMonitor
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelCourse
import com.rudimentor.app.data.levels.LearningProgress
import com.rudimentor.app.data.levels.LevelsUiState
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.component.MenuCard
import com.rudimentor.app.ui.component.RudiMentorLogo
import com.rudimentor.app.ui.dev.DevScreen
import com.rudimentor.app.ui.levels.LevelDetailScreen
import com.rudimentor.app.ui.levels.LevelsScreen
import com.rudimentor.app.ui.metronome.MetronomeActions
import com.rudimentor.app.ui.metronome.MetronomeScreen
import com.rudimentor.app.ui.miclab.MicLabScreen
import com.rudimentor.app.ui.practice.PracticeResult
import com.rudimentor.app.ui.practice.PracticeResultScreen
import com.rudimentor.app.ui.practice.PracticeScreen
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import com.rudimentor.app.util.DevLog
import kotlin.math.roundToInt

/** Top-level destinations. Added to as new sections come online. */
private enum class Screen {
    Menu,
    Levels,
    LevelDetail,
    Practice,
    PracticeResult,
    Metronome,
    Dev,
    MicLab,
}

@Composable
fun RudiMentorApp(
    buildInfo: BuildInfo,
    settings: AppSettings,
    course: LevelCourse,
    learningProgress: LearningProgress,
    levelsUi: LevelsUiState,
    actions: MetronomeActions,
    onSelectTab: (String) -> Unit,
    onSelectRank: (PracticeRank) -> Unit,
    onClickAudible: (Boolean) -> Unit,
    onClickFollowsHeadphones: (Boolean) -> Unit,
    onInputLatencyMs: (Float) -> Unit,
    onAttemptFinished: (Level, PracticeRank, Int, PracticeResult) -> Unit,
) {
    // The click follows the audio output until the learner overrides it by hand, so
    // the effective value is decided here, once, for every screen that plays it
    // (decision 114).
    val context = LocalContext.current
    val headphonesFlow = remember(context) { AudioOutputMonitor.connectedFlow(context) }
    val headphonesConnected by headphonesFlow.collectAsStateWithLifecycle(
        initialValue = AudioOutputMonitor.isConnected(context),
    )
    val clickAudible = settings.clickAudibleWith(headphonesConnected)

    var screenName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    var selectedLevelId by rememberSaveable { mutableStateOf<String?>(null) }
    var metronomeBackTargetName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    var practiceRankName by rememberSaveable { mutableStateOf(PracticeRank.Practice.name) }
    var practiceBpm by rememberSaveable { mutableIntStateOf(0) }
    // The result lives for as long as the result screen does: an attempt is never
    // restored across process death, it is replayed instead.
    var practiceResult by remember { mutableStateOf<PracticeResult?>(null) }
    var practiceRunId by rememberSaveable { mutableIntStateOf(0) }
    val practiceRank = PracticeRank.entries.firstOrNull { it.name == practiceRankName }
        ?: PracticeRank.Practice
    val screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.Menu
    // The difficulty is chosen once for the whole course, and the open tab is the one the
    // learner left the map on — or the last map they have earned.
    val rank = levelsUi.rank
    val activeTabId = levelsUi.familyId
        ?: course.tabs.lastOrNull { it.available && learningProgress.isTabUnlocked(it) }?.id
        ?: course.tabs.first().id

    // The navigation trail is the first thing a field report needs: every screen
    // change is in the log the developer screen can share.
    LaunchedEffect(screen, selectedLevelId) {
        DevLog.log("nav", "screen=${screen.name} level=${selectedLevelId ?: "-"}")
    }

    // One stage for the whole practice flow, so the attempt and its result do not
    // hand the orientation back and forth between themselves.
    LandscapeStage(
        landscape = screen == Screen.Practice || screen == Screen.PracticeResult,
        keepScreenOn = screen == Screen.Practice,
    )

    AnimatedContent(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            Screen.Menu -> MainMenu(
                buildInfo = buildInfo,
                onOpenMetronome = {
                    metronomeBackTargetName = Screen.Menu.name
                    screenName = Screen.Metronome.name
                },
                onOpenLevels = { screenName = Screen.Levels.name },
                onOpenDev = { screenName = Screen.Dev.name },
            )
            Screen.Levels -> LevelsScreen(
                course = course,
                progress = learningProgress,
                rank = rank,
                activeTabId = activeTabId,
                onSelectTab = onSelectTab,
                onSelectRank = onSelectRank,
                onBack = { screenName = Screen.Menu.name },
                onOpenLevel = { levelId ->
                    selectedLevelId = levelId
                    screenName = Screen.LevelDetail.name
                },
            )
            Screen.LevelDetail -> {
                val level = selectedLevelId?.let(course::level)
                val family = selectedLevelId?.let(course::family)
                if (level == null || family == null) {
                    LaunchedEffect(Unit) {
                        if (screen != Screen.LevelDetail) return@LaunchedEffect
                        DevLog.error("nav", "level detail without a level, back to the map")
                        screenName = Screen.Levels.name
                    }
                } else {
                    LevelDetailScreen(
                        level = level,
                        family = family,
                        rank = rank,
                        progress = learningProgress.forLevel(level.id),
                        onBack = { screenName = Screen.Levels.name },
                        // The level owns tempo and rank of the attempt only: the
                        // metronome grid is the user's own and is never overwritten
                        // by entering a level (decision 102).
                        onStartPractice = { _, rank, bpm ->
                            practiceRankName = rank.name
                            practiceBpm = bpm
                            practiceRunId += 1
                            screenName = Screen.Practice.name
                        },
                    )
                }
            }
            Screen.Practice -> {
                val level = selectedLevelId?.let(course::level)
                val family = selectedLevelId?.let(course::family)
                if (level == null || family == null) {
                    // Nothing to practise: fall back to the map instead of a blank
                    // screen. Only while this really is the current screen -- during
                    // a transition the outgoing screen must not steer navigation.
                    LaunchedEffect(Unit) {
                        if (screen != Screen.Practice) return@LaunchedEffect
                        DevLog.error("nav", "practice without a level, back to the map")
                        screenName = Screen.Levels.name
                    }
                } else {
                    key(practiceRunId) {
                        PracticeScreen(
                            level = level,
                            family = family,
                            rank = practiceRank,
                            bpm = practiceBpm,
                            clickAudible = clickAudible,
                            latencyMs = settings.inputLatencyMs,
                            onExit = { screenName = Screen.LevelDetail.name },
                            onFinished = { result ->
                                DevLog.log(
                                    "practice",
                                    "finished ${level.id} rank=${practiceRank.name} " +
                                        "bpm=$practiceBpm " +
                                        "accuracy=${(result.accuracy * 100f).roundToInt()}% " +
                                        "stars=${result.stars} passed=${result.passed}",
                                )
                                onAttemptFinished(level, practiceRank, practiceBpm, result)
                                practiceResult = result
                                screenName = Screen.PracticeResult.name
                            },
                        )
                    }
                }
            }
            Screen.PracticeResult -> {
                val level = selectedLevelId?.let(course::level)
                val family = selectedLevelId?.let(course::family)
                val result = practiceResult
                if (level == null || family == null || result == null) {
                    LaunchedEffect(Unit) {
                        if (screen != Screen.PracticeResult) return@LaunchedEffect
                        DevLog.error("nav", "result without level/result, back to the map")
                        screenName = Screen.Levels.name
                    }
                } else {
                    // The next level is the next required one on the same map.
                    val nextLevel = course.catalog(family.id)?.levels
                        ?.filter { it.row > level.row && it.column.required }
                        ?.minByOrNull(Level::row)
                    PracticeResultScreen(
                        level = level,
                        family = family,
                        rank = practiceRank,
                        bpm = practiceBpm,
                        result = result,
                        buildInfo = buildInfo,
                        clickAudible = clickAudible,
                        onClickAudible = onClickAudible,
                        clickFollowsHeadphones = settings.clickFollowsHeadphones,
                        onClickFollowsHeadphones = onClickFollowsHeadphones,
                        latencyMs = settings.inputLatencyMs,
                        onLatencyMs = onInputLatencyMs,
                        onRetry = {
                            practiceRunId += 1
                            screenName = Screen.Practice.name
                        },
                        onNextLevel = nextLevel?.let { next ->
                            {
                                selectedLevelId = next.id
                                screenName = Screen.LevelDetail.name
                            }
                        },
                        onToMap = { screenName = Screen.Levels.name },
                    )
                }
            }
            Screen.Metronome -> MetronomeScreen(
                settings = settings,
                buildInfo = buildInfo,
                actions = actions,
                onBack = { screenName = metronomeBackTargetName },
            )
            Screen.Dev -> DevScreen(
                buildInfo = buildInfo,
                onBack = { screenName = Screen.Menu.name },
                onOpenMicLab = { screenName = Screen.MicLab.name },
            )
            Screen.MicLab -> MicLabScreen(
                buildInfo = buildInfo,
                onBack = { screenName = Screen.Dev.name },
            )
        }
    }
}

@Composable
private fun MainMenu(
    buildInfo: BuildInfo,
    onOpenMetronome: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenDev: () -> Unit,
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
                iconRes = R.drawable.ic_menu_metronome,
                enabled = true,
                onClick = onOpenMetronome,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = stringResource(R.string.menu_levels),
                iconRes = R.drawable.ic_menu_levels,
                enabled = true,
                onClick = onOpenLevels,
            )
            // Settings and About have their icons and their place in the menu, but no
            // screens yet: they stay grey and untappable until those screens land.
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = stringResource(R.string.menu_settings),
                iconRes = R.drawable.ic_menu_settings,
                enabled = false,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = stringResource(R.string.menu_about),
                iconRes = R.drawable.ic_menu_info,
                enabled = false,
                onClick = {},
            )
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                MenuCard(
                    title = "Developer · dev",
                    letter = "D",
                    enabled = true,
                    onClick = onOpenDev,
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
