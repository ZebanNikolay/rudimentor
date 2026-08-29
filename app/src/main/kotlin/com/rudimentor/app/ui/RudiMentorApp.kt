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
import com.rudimentor.app.data.OutputDevice
import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelCourse
import com.rudimentor.app.data.levels.LearningProgress
import com.rudimentor.app.data.levels.LevelsUiState
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.ui.about.AboutScreen
import com.rudimentor.app.ui.component.MenuCard
import com.rudimentor.app.ui.component.RudiMentorLogo
import com.rudimentor.app.ui.dev.DevScreen
import com.rudimentor.app.ui.dev.PracticeLogScreen
import com.rudimentor.app.ui.levels.LevelDetailScreen
import com.rudimentor.app.ui.levels.LevelsScreen
import com.rudimentor.app.ui.metronome.MetronomeActions
import com.rudimentor.app.ui.metronome.MetronomeScreen
import com.rudimentor.app.ui.miclab.MicLabScreen
import com.rudimentor.app.ui.practice.PracticeResult
import com.rudimentor.app.ui.practice.PracticeResultScreen
import com.rudimentor.app.ui.practice.PracticeScreen
import com.rudimentor.app.ui.settings.CalibrationScreen
import com.rudimentor.app.ui.settings.SettingsScreen
import com.rudimentor.app.ui.soundcheck.SoundCheckScreen
import com.rudimentor.app.ui.soundcheck.SoundCheckStep
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
    Settings,
    Calibration,
    SoundCheck,
    About,
    Dev,
    MicLab,
    PracticeLog,
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
    onApplyDraft: (SettingsDraft) -> Unit,
    onOutputChanged: (OutputDevice) -> Unit,
    onAttemptFinished: (Level, PracticeRank, PracticeResult) -> Unit,
    /** Marks the sound-check node as walked, so the map stops asking for it. */
    onSoundCheckDone: () -> Unit,
    /** Closes the plate above the level map that calls for the sound check. */
    onHideSoundCheckPlate: () -> Unit,
) {
    val context = LocalContext.current
    val headphonesFlow = remember(context) { AudioOutputMonitor.connectedFlow(context) }
    val headphonesConnected by headphonesFlow.collectAsStateWithLifecycle(
        initialValue = AudioOutputMonitor.isConnected(context),
    )
    // The click belongs to the output in force, and the settings mirror that profile, so
    // the value is simply read here (decision 172).
    val clickAudible = settings.clickAudible

    // Which output the sound is going through, so the latency saved for those headphones is
    // the one in force. An output nobody saved leaves the current latency alone and warns
    // instead, because guessing a delay is worse than saying it is unknown (decision 161).
    val outputFlow = remember(context) { AudioOutputMonitor.deviceFlow(context) }
    val currentOutput by outputFlow.collectAsStateWithLifecycle(
        initialValue = AudioOutputMonitor.currentDevice(context),
    )
    LaunchedEffect(currentOutput) {
        currentOutput?.let(onOutputChanged)
    }
    val unknownOutput = currentOutput != null && settings.profileFor(currentOutput!!) == null

    var screenName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    var selectedLevelId by rememberSaveable { mutableStateOf<String?>(null) }
    var metronomeBackTargetName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    var practiceRankName by rememberSaveable { mutableStateOf(PracticeRank.Practice.name) }
    var practiceBpm by rememberSaveable { mutableIntStateOf(0) }
    // The result lives for as long as the result screen does: an attempt is never
    // restored across process death, it is replayed instead.
    var practiceResult by remember { mutableStateOf<PracticeResult?>(null) }
    var practiceRunId by rememberSaveable { mutableIntStateOf(0) }
    // The settings the learner is editing. It lives here and not on the settings screen
    // so that walking into the calibration screen and back does not throw it away
    // (decision 154). Null means nobody is editing anything.
    var settingsDraft by remember { mutableStateOf<SettingsDraft?>(null) }
    // Which step the sound check opens on. A pair of headphones nobody has measured needs
    // only its own step; everything else walks the whole thing.
    var soundCheckStartStep by remember { mutableStateOf(SoundCheckStep.Pad) }
    val practiceRank = PracticeRank.entries.firstOrNull { it.name == practiceRankName }
        ?: PracticeRank.Practice
    val screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.Menu
    // The difficulty is chosen once for the whole course, and the open tab is the one the
    // learner left the map on — or the last map they have earned.
    val rank = levelsUi.rank
    val activeTabId = levelsUi.familyId
        ?: course.tabs.lastOrNull { it.available && learningProgress.isTabUnlocked(it) }?.id
        ?: course.tabs.first().id

    // A pair of headphones nobody has measured is the one thing that silently ruins every
    // attempt afterwards, so the map offers its own step instead of waiting for the player to
    // notice a bad score. Only from the map, and only once the check has been walked before:
    // pulling somebody out of an attempt would be worse than the wrong number (decision 169).
    LaunchedEffect(screen, unknownOutput, headphonesConnected) {
        if (screen != Screen.Levels) return@LaunchedEffect
        if (!unknownOutput || !headphonesConnected || !settings.soundCheckDone) {
            return@LaunchedEffect
        }
        soundCheckStartStep = SoundCheckStep.Headphones
        screenName = Screen.SoundCheck.name
    }

    // The navigation trail is the first thing a field report needs: every screen
    // change is in the log the developer screen can share.
    LaunchedEffect(screen, selectedLevelId) {
        DevLog.log("nav", "screen=${screen.name} level=${selectedLevelId ?: "-"}")
    }

    // One stage for the whole practice flow, so the attempt and its result do not
    // hand the orientation back and forth between themselves.
    LandscapeStage(
        landscape = screen == Screen.Practice || screen == Screen.PracticeResult,
        // Any round that is half a minute of playing without touching the phone: the screen
        // timing out takes the audio streams with it (decision 158). The sound check runs the
        // same round and was missing from this list, so the display lock killed step 2 for
        // anybody with a short lock timeout (decision 175).
        keepScreenOn = screen == Screen.Practice ||
            screen == Screen.Calibration ||
            screen == Screen.SoundCheck,
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
                onOpenAbout = { screenName = Screen.About.name },
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
                onOpenSettings = {
                    settingsDraft = SettingsDraft.from(settings)
                    screenName = Screen.Settings.name
                },
                onOpenSoundCheck = {
                    // Asked for by hand: walk the whole thing, whatever was measured before.
                    soundCheckStartStep = SoundCheckStep.Pad
                    screenName = Screen.SoundCheck.name
                },
                soundCheckDone = settings.soundCheckDone,
                clickSounds = clickAudible,
                soundCheckPlateHidden = settings.soundCheckPlateHidden,
                onHideSoundCheckPlate = onHideSoundCheckPlate,
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
                            latencyCalibrated = settings.latencyCalibrated,
                            calibrationSkewMs = settings.selectedProfile.calibrationSkewMs,
                            micThresholdLevel = settings.micThresholdLevel,
                            headphonesConnected = headphonesConnected,
                            showOffsetMs = settings.showOffsetMs,
                            buildInfo = buildInfo,
                            unknownOutput = unknownOutput,
                            onExit = { screenName = Screen.LevelDetail.name },
                            onFinished = { result ->
                                DevLog.log(
                                    "practice",
                                    "finished ${level.id} rank=${practiceRank.name} " +
                                        "bpm=$practiceBpm " +
                                        "accuracy=${(result.accuracy * 100f).roundToInt()}% " +
                                        "stars=${result.stars} passed=${result.passed}",
                                )
                                onAttemptFinished(level, practiceRank, result)
                                // The attempt measured the round trip of this very run while
                                // it was judging; that number goes back into the output
                                // profile, so the next run does not repeat the same
                                // systematic lateness (decision 167).
                                val current = settingsDraft ?: SettingsDraft.from(settings)
                                val tuned = current.withLatencyBias(result.latencyBiasMs)
                                if (tuned != current) {
                                    DevLog.log(
                                        "practice",
                                        "self-tuned latency " +
                                            "${current.latencyMs.roundToInt()} -> " +
                                            "${tuned.latencyMs.roundToInt()} ms " +
                                            "(bias ${result.latencyBiasMs.roundToInt()} ms)",
                                    )
                                    if (settingsDraft != null) settingsDraft = tuned
                                    onApplyDraft(tuned)
                                }
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
                        settings = settings,
                        unknownOutput = unknownOutput,
                        onApplyDraft = onApplyDraft,
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
                        onSoundCheck = { screenName = Screen.SoundCheck.name },
                    )
                }
            }
            Screen.Metronome -> MetronomeScreen(
                settings = settings,
                buildInfo = buildInfo,
                actions = actions,
                onBack = { screenName = metronomeBackTargetName },
            )
            Screen.Settings -> {
                val draft = settingsDraft
                if (draft == null) {
                    LaunchedEffect(Unit) {
                        if (screen != Screen.Settings) return@LaunchedEffect
                        DevLog.error("nav", "settings without a draft, back to the menu")
                        screenName = Screen.Menu.name
                    }
                } else {
                    SettingsScreen(
                        draft = draft,
                        currentOutput = currentOutput,
                        buildInfo = buildInfo,
                        // Instant apply: a switch flipped here is a switch changed, with no
                        // Save to remember and nothing to lose on the way out (decision 166).
                        onDraftChange = {
                            settingsDraft = it
                            onApplyDraft(it)
                        },
                        onCalibrate = { screenName = Screen.Calibration.name },
                        onClose = {
                            settingsDraft = null
                            screenName = Screen.Levels.name
                        },
                    )
                }
            }
            Screen.Calibration -> CalibrationScreen(
                latencyMs = settingsDraft?.latencyMs ?: settings.inputLatencyMs,
                latencyCalibrated = settingsDraft?.latencyCalibrated
                    ?: settings.latencyCalibrated,
                micThresholdLevel = settingsDraft?.micThresholdLevel
                    ?: settings.micThresholdLevel,
                profileName = settingsDraft?.selectedProfile?.name
                    ?: settings.selectedProfile.name,
                headphonesConnected = headphonesConnected,
                clickSounds = clickAudible,
                buildInfo = buildInfo,
                onApply = { measuredMs, measuredSkewMs, micThreshold ->
                    // The screen stores as it measures, and stays open: a finished round is
                    // the act of choosing the number, so there is nothing left to confirm.
                    // A dev.40 attempt played with the previous 123 ms because a fresh
                    // 190 ms measurement waited for a Save that never came (decision 166).
                    val applied = (settingsDraft ?: SettingsDraft.from(settings))
                        .withMicThreshold(micThreshold)
                        .let { draft ->
                            if (measuredMs == null) {
                                draft
                            } else {
                                draft.withCalibration(measuredMs, measuredSkewMs)
                            }
                        }
                    settingsDraft = applied
                    onApplyDraft(applied)
                },
                onBack = { screenName = Screen.Settings.name },
            )
            // The first minute of the app, and the place a broken audio path is sent back to.
            // It writes the same two numbers the calibration screen writes, so a measurement
            // taken here is the one every attempt plays with (decision 169).
            Screen.SoundCheck -> SoundCheckScreen(
                profileName = settings.selectedProfile.name,
                micThresholdLevel = settings.micThresholdLevel,
                latencyMs = settings.inputLatencyMs,
                latencyCalibrated = settings.latencyCalibrated,
                headphonesConnected = headphonesConnected,
                clickSounds = clickAudible,
                buildInfo = buildInfo,
                startStep = soundCheckStartStep,
                onApply = { measuredMs, measuredSkewMs, micThreshold ->
                    val applied = SettingsDraft.from(settings)
                        .withMicThreshold(micThreshold)
                        .let { draft ->
                            if (measuredMs == null) {
                                draft
                            } else {
                                draft.withCalibration(measuredMs, measuredSkewMs)
                            }
                        }
                    onApplyDraft(applied)
                },
                onFinished = {
                    onSoundCheckDone()
                    screenName = Screen.Levels.name
                },
                onBack = { screenName = Screen.Levels.name },
            )
            Screen.About -> AboutScreen(
                buildInfo = buildInfo,
                onBack = { screenName = Screen.Menu.name },
            )
            Screen.Dev -> DevScreen(
                buildInfo = buildInfo,
                onBack = { screenName = Screen.Menu.name },
                onOpenMicLab = { screenName = Screen.MicLab.name },
                onOpenPracticeLog = { screenName = Screen.PracticeLog.name },
            )
            Screen.MicLab -> MicLabScreen(
                buildInfo = buildInfo,
                onBack = { screenName = Screen.Dev.name },
            )
            Screen.PracticeLog -> PracticeLogScreen(
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
    onOpenAbout: () -> Unit,
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
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = stringResource(R.string.menu_about),
                iconRes = R.drawable.ic_menu_info,
                enabled = true,
                onClick = onOpenAbout,
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
