package com.rudimentor.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rudimentor.app.data.OutputProfile
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
import com.rudimentor.app.util.AppLog
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
    /** The accuracy record as it stood before the attempt that is being judged. */
    var practiceBestBefore by remember { mutableStateOf<Float?>(null) }
    var practiceRunId by rememberSaveable { mutableIntStateOf(0) }
    // The settings the learner is editing. It lives here and not on the settings screen
    // so that walking into the calibration screen and back does not throw it away
    // (decision 154). Null means nobody is editing anything.
    var settingsDraft by remember { mutableStateOf<SettingsDraft?>(null) }
    // Which step the sound check opens on. A pair of headphones nobody has measured needs
    // only its own step; everything else walks the whole thing.
    var soundCheckStartStep by remember { mutableStateOf(SoundCheckStep.Pad) }
    // The output the map has already offered the headphones step for: the offer is made once
    // per output and never again in this process (decision 193).
    var promptedOutputKey by rememberSaveable { mutableStateOf<String?>(null) }
    // The output the dialog is open for, or null when nothing is being asked (decision 194).
    var newOutputPrompt by remember { mutableStateOf<OutputDevice?>(null) }
    // Set when the walk was entered for a freshly connected output: the loudness step was
    // measured long ago and repeating it would be half a minute of holding a stick for nothing
    // (decision 194).
    var soundCheckHeadphonesOnly by remember { mutableStateOf(false) }
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
    //
    // It offers and never takes over (decision 194). Walking a player into a measuring screen
    // without a word looked like a broken app: the check opened on its second step, said
    // nothing about new headphones, and -- while the output stayed unbound -- every Back and
    // every Finish landed back in it, so the only way out was killing the app. So the map asks
    // in a dialog that names what happened and what the measurement costs, and Later is a real
    // answer: the same output is never asked about twice, and the practice screen already warns
    // about an unmeasured output on its own.
    LaunchedEffect(screen, unknownOutput, headphonesConnected) {
        if (screen != Screen.Levels) return@LaunchedEffect
        val device = currentOutput ?: return@LaunchedEffect
        if (!unknownOutput || !headphonesConnected || !settings.soundCheckDone) {
            return@LaunchedEffect
        }
        if (promptedOutputKey == device.key) return@LaunchedEffect
        promptedOutputKey = device.key
        newOutputPrompt = device
    }

    // Which audio path is open explains most timing reports, so the route change
    // itself is an event: a stranger's log shows the switch, not just the result.
    LaunchedEffect(currentOutput?.key) {
        val device = currentOutput ?: return@LaunchedEffect
        AppLog.event(
            "audio",
            "output ${device.kind.name} \"${OutputProfile.cleanName(device.name)}\"",
        )
    }

    // The navigation trail is the first thing a field report needs: every screen
    // change is in the log the developer screen can share.
    LaunchedEffect(screen, selectedLevelId) {
        AppLog.trace("nav") { "screen=${screen.name} level=${selectedLevelId ?: "-"}" }
    }

    // One stage for the whole practice flow, so the attempt and its result do not
    // hand the orientation back and forth between themselves.
    LandscapeStage(
        landscape = screen == Screen.Practice || screen == Screen.PracticeResult,
        // Any round that is playing without touching the phone: the screen timing out takes
        // the audio streams with it (decision 158). The sound check runs the same round and
        // was missing from this list, so the display lock killed step 2 for anybody with a
        // short lock timeout (decision 175). The mic lab is a round too -- it just lives on
        // the developer screen.
        keepScreenOn = screen == Screen.Practice ||
            screen == Screen.Calibration ||
            screen == Screen.SoundCheck ||
            screen == Screen.MicLab,
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
            Screen.Levels -> {
            LevelsScreen(
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
                    soundCheckHeadphonesOnly = false
                    screenName = Screen.SoundCheck.name
                },
                soundCheckDone = settings.soundCheckDone,
                clickSounds = clickAudible,
                soundCheckPlateHidden = settings.soundCheckPlateHidden,
                onHideSoundCheckPlate = onHideSoundCheckPlate,
            )
            // Asked, not done to the player (decision 194).
            newOutputPrompt?.let { device ->
                NewOutputDialog(
                    deviceName = OutputProfile.cleanName(device.name)
                        .ifBlank { device.kind.fallbackName },
                    onMeasure = {
                        newOutputPrompt = null
                        // Binding the output is what makes the answer stick: an output with no
                        // profile of its own would be asked about again on the next map visit,
                        // and the measurement would have nowhere to be written (decision 194).
                        onApplyDraft(
                            SettingsDraft.from(settings)
                                .withAddedProfile(device, System.currentTimeMillis()),
                        )
                        soundCheckStartStep = SoundCheckStep.Headphones
                        soundCheckHeadphonesOnly = true
                        screenName = Screen.SoundCheck.name
                    },
                    onLater = { newOutputPrompt = null },
                )
            }
            }
            Screen.LevelDetail -> {
                val level = selectedLevelId?.let(course::level)
                val family = selectedLevelId?.let(course::family)
                if (level == null || family == null) {
                    LaunchedEffect(Unit) {
                        if (screen != Screen.LevelDetail) return@LaunchedEffect
                        AppLog.error("nav", "level detail without a level, back to the map")
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
                        AppLog.error("nav", "practice without a level, back to the map")
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
                            micLatencyMs = settings.micLatencyMs,
                            calibrationSkewMs = settings.selectedProfile.calibrationSkewMs,
                            micThresholdLevel = settings.micThresholdLevel,
                            headphonesConnected = headphonesConnected,
                            showOffsetMs = settings.showOffsetMs,
                            buildInfo = buildInfo,
                            unknownOutput = unknownOutput,
                            onExit = { screenName = Screen.LevelDetail.name },
                            onFinished = { result ->
                                AppLog.trace("practice") {
                                    "finished ${level.id} rank=${practiceRank.name} " +
                                        "bpm=$practiceBpm " +
                                        "accuracy=${(result.accuracy * 100f).roundToInt()}% " +
                                        "stars=${result.stars} passed=${result.passed}"
                                }
                                // Read the record before the attempt is stored: the store
                                // keeps the better of the two, so after the write there is
                                // nothing left to compare against (decision 202).
                                practiceBestBefore = learningProgress
                                    .forLevel(level.id, practiceRank).bestAccuracy
                                onAttemptFinished(level, practiceRank, result)
                                // The attempt measured the round trip of this very run while
                                // it was judging; that number goes back into the output
                                // profile, so the next run does not repeat the same
                                // systematic lateness (decision 167).
                                val current = settingsDraft ?: SettingsDraft.from(settings)
                                val tuned = current.withLatencyBias(result.latencyBiasMs)
                                if (tuned != current) {
                                    AppLog.event(
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
                        AppLog.error("nav", "result without level/result, back to the map")
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
                        previousBest = practiceBestBefore,
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
                        AppLog.error("nav", "settings without a draft, back to the menu")
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
                onApply = { measuredMs, measuredSkewMs, micThreshold, measuredMicMs ->
                    // The screen stores as it measures, and stays open: a finished round is
                    // the act of choosing the number, so there is nothing left to confirm.
                    // A dev.40 attempt played with the previous 123 ms because a fresh
                    // 190 ms measurement waited for a Save that never came (decision 166).
                    val applied = (settingsDraft ?: SettingsDraft.from(settings))
                        .withMicThreshold(micThreshold)
                        .withMicLatency(measuredMicMs)
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
                headphonesOnly = soundCheckHeadphonesOnly,
                onNoHeadphones = {
                    // Said on the headphones step: no pair at all. The click stops sounding on
                    // this output, which is the same state the speaker branch starts in
                    // (decision 186).
                    onApplyDraft(SettingsDraft.from(settings).withClickAudible(false))
                },
                onApply = { measuredMs, measuredSkewMs, micThreshold, measuredMicMs ->
                    val applied = SettingsDraft.from(settings)
                        .withMicThreshold(micThreshold)
                        .withMicLatency(measuredMicMs)
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

/**
 * What the map asks when headphones nobody has measured are connected.
 *
 * The check used to simply open on its own, on its second step, with no line anywhere saying
 * why -- and it read as the app breaking rather than as an offer. A dialog costs one tap and
 * says the three things the player needs: which output is new, that its click delay is a fact
 * about that pair alone, and what saying yes costs (decision 194).
 */
@Composable
private fun NewOutputDialog(
    deviceName: String,
    onMeasure: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = RudiColors.Surface,
        title = {
            Text(
                text = stringResource(R.string.new_output_title),
                style = RudiTextStyles.Rubric,
                color = RudiColors.Text,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.new_output_body, deviceName),
                style = MaterialTheme.typography.bodyMedium,
                color = RudiColors.Text,
            )
        },
        confirmButton = {
            TextButton(onClick = onMeasure) {
                Text(text = stringResource(R.string.new_output_measure))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(text = stringResource(R.string.new_output_later))
            }
        },
    )
}
