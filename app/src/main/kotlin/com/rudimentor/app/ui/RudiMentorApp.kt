package com.rudimentor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.BuildInfo
import com.rudimentor.app.audio.BeatPattern
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Metronome

private enum class Screen {
    Menu,
    Metronome,
    PrototypeLab,
}

@Composable
fun RudiMentorApp(
    buildInfo: BuildInfo,
    prototypeLabEnabled: Boolean,
) {
    var screenName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    var selectedStyleName by rememberSaveable { mutableStateOf(BeatIndicatorStyle.Default.name) }
    val savedScreen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.Menu
    val screen = if (savedScreen == Screen.PrototypeLab && !prototypeLabEnabled) Screen.Menu else savedScreen
    val selectedStyle = BeatIndicatorStyle.fromSavedValue(selectedStyleName)

    AnimatedContent(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            Screen.Menu -> MainMenu(
                buildInfo = buildInfo,
                prototypeLabEnabled = prototypeLabEnabled,
                onOpenMetronome = { screenName = Screen.Metronome.name },
                onOpenPrototypeLab = { screenName = Screen.PrototypeLab.name },
            )
            Screen.Metronome -> MetronomeScreen(
                indicatorStyle = selectedStyle,
                onBack = { screenName = Screen.Menu.name },
            )
            Screen.PrototypeLab -> PrototypeLabScreen(
                selectedStyle = selectedStyle,
                onStyleSelected = { selectedStyleName = it.name },
                onBack = { screenName = Screen.Menu.name },
            )
        }
    }
}

@Composable
private fun MainMenu(
    buildInfo: BuildInfo,
    prototypeLabEnabled: Boolean,
    onOpenMetronome: () -> Unit,
    onOpenPrototypeLab: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = "RUDI",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
            )
            Text(
                text = "MENTOR",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                text = "Build time. Build control.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))
            MenuCard(
                title = "Metronome",
                subtitle = "Build your beat pattern",
                icon = Icons.Rounded.Timer,
                enabled = true,
                onClick = onOpenMetronome,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = "Levels",
                subtitle = "Rudiment training · Coming soon",
                icon = Icons.Rounded.Lock,
                enabled = false,
                onClick = {},
            )
            if (prototypeLabEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                MenuCard(
                    title = "Prototype Lab",
                    subtitle = "Compare beat indicators · Dev only",
                    icon = Icons.Rounded.Science,
                    enabled = true,
                    onClick = onOpenPrototypeLab,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "START WITH THE BEAT",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildInfo.displayLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.16f else 0.05f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open $title",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetronomeScreen(
    indicatorStyle: BeatIndicatorStyle,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val metronome = remember(scope) { Metronome(scope) }
    var bpm by remember { mutableIntStateOf(Bpm.DEFAULT) }
    var pattern by remember { mutableStateOf(BeatPattern.default()) }
    var showSticking by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    fun stop() {
        metronome.stop()
        isRunning = false
        tick = 0
    }

    fun updateBpm(value: Int) {
        bpm = Bpm.clamp(value)
        metronome.setBpm(bpm)
    }

    BackHandler {
        stop()
        onBack()
    }
    DisposableEffect(metronome) {
        onDispose { metronome.stop() }
    }
    LaunchedEffect(metronome) {
        metronome.ticks.collect { tick = it }
    }
    LaunchedEffect(pattern) {
        metronome.setPattern(pattern)
    }

    if (showSettings) {
        MetronomeSettingsSheet(
            bpm = bpm,
            showSticking = showSticking,
            onBpmChange = ::updateBpm,
            onShowStickingChange = { showSticking = it },
            onDismiss = { showSettings = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "METRONOME",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            stop()
                            onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to main menu",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PatternEditor(
                pattern = pattern,
                indicatorStyle = indicatorStyle,
                activeBeat = if (isRunning && tick > 0) ((tick - 1) % pattern.size).toInt() else -1,
                showSticking = showSticking,
                onToggleAccent = { index -> pattern = pattern.toggleAccent(index) },
                onToggleHand = { index -> pattern = pattern.toggleHand(index) },
                onRemoveBeat = { pattern = pattern.removeBeat() },
                onAddBeat = { pattern = pattern.addBeat() },
            )

            Spacer(modifier = Modifier.weight(1f))
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Button(
                onClick = {
                    if (isRunning) {
                        stop()
                    } else {
                        metronome.setBpm(bpm)
                        metronome.setPattern(pattern)
                        isRunning = metronome.start()
                        errorMessage = if (isRunning) null else "Could not open the audio output."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isRunning) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                ),
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "STOP" else "START",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SETTINGS",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$bpm BPM · ${if (showSticking) "R/L" else "ABSTRACT"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PatternEditor(
    pattern: BeatPattern,
    indicatorStyle: BeatIndicatorStyle,
    activeBeat: Int,
    showSticking: Boolean,
    onToggleAccent: (Int) -> Unit,
    onToggleHand: (Int) -> Unit,
    onRemoveBeat: () -> Unit,
    onAddBeat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "YOUR PATTERN",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "${pattern.size} beats",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        IconButton(
            onClick = onRemoveBeat,
            enabled = pattern.size > BeatPattern.MIN_BEATS,
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Remove beat")
        }
        IconButton(
            onClick = onAddBeat,
            enabled = pattern.size < BeatPattern.MAX_BEATS,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add beat")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pattern.accents.forEachIndexed { index, isAccent ->
            if (index == 4) {
                Spacer(modifier = Modifier.width(16.dp))
            } else if (index > 0) {
                Spacer(modifier = Modifier.width(5.dp))
            }
            val isActive = index == activeBeat
            val hand = pattern.hands[index]
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                BeatIndicator(
                    style = indicatorStyle,
                    beatNumber = index + 1,
                    isAccent = isAccent,
                    hand = hand,
                    showSticking = showSticking,
                    isActive = isActive,
                    onClick = {
                        if (showSticking) onToggleHand(index) else onToggleAccent(index)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .aspectRatio(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetronomeSettingsSheet(
    bpm: Int,
    showSticking: Boolean,
    onBpmChange: (Int) -> Unit,
    onShowStickingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "SETTINGS",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            TempoControl(bpm = bpm, onBpmChange = onBpmChange)
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))
            StickingSetting(
                checked = showSticking,
                onCheckedChange = onShowStickingChange,
            )
        }
    }
}

@Composable
private fun TempoControl(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TEMPO",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "Beats per minute",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        Text(
            text = bpm.toString(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            lineHeight = 48.sp,
        )
    }

    Slider(
        value = bpm.toFloat(),
        onValueChange = { onBpmChange(it.toInt()) },
        valueRange = Bpm.MIN.toFloat()..Bpm.MAX.toFloat(),
        steps = Bpm.MAX - Bpm.MIN - 1,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Bpm.QUICK_STEPS.forEach { delta ->
            FilledTonalButton(
                onClick = { onBpmChange(Bpm.adjust(bpm, delta)) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = if (delta > 0) "+$delta" else "$delta",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StickingSetting(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("R/L sticking", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.semantics { contentDescription = "Show right and left sticking" },
            )
        }
    }
}
