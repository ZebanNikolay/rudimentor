package com.rudimentor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Metronome

private enum class Screen {
    Menu,
    Metronome,
}

@Composable
fun RudiMentorApp() {
    var screen by remember { mutableStateOf(Screen.Menu) }

    AnimatedContent(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            Screen.Menu -> MainMenu(onOpenMetronome = { screen = Screen.Metronome })
            Screen.Metronome -> MetronomeScreen(onBack = { screen = Screen.Menu })
        }
    }
}

@Composable
private fun MainMenu(onOpenMetronome: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
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

            Spacer(modifier = Modifier.height(52.dp))
            MenuCard(
                title = "Metronome",
                subtitle = "Lock in your pulse",
                icon = Icons.Rounded.Timer,
                enabled = true,
                onClick = onOpenMetronome,
            )
            Spacer(modifier = Modifier.height(16.dp))
            MenuCard(
                title = "Levels",
                subtitle = "Rudiment training · Coming soon",
                icon = Icons.Rounded.Lock,
                enabled = false,
                onClick = {},
            )

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
            .height(124.dp)
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

@Composable
private fun MetronomeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val metronome = remember(scope) { Metronome(scope) }
    var bpm by remember { mutableIntStateOf(Bpm.DEFAULT) }
    var isRunning by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    stop()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to main menu")
                }
                Text(
                    text = "METRONOME",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(54.dp))
            Text(
                text = bpm.toString(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-5).sp,
                lineHeight = 100.sp,
            )
            Text(
                text = "BEATS PER MINUTE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(44.dp))
            BeatIndicator(tick = tick, isRunning = isRunning)
            Spacer(modifier = Modifier.height(44.dp))

            Slider(
                value = bpm.toFloat(),
                onValueChange = { updateBpm(it.toInt()) },
                valueRange = Bpm.MIN.toFloat()..Bpm.MAX.toFloat(),
                steps = Bpm.MAX - Bpm.MIN - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${Bpm.MIN}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("${Bpm.MAX}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(-5, -1, 1, 5).forEach { delta ->
                    FilledTonalButton(
                        onClick = { updateBpm(Bpm.adjust(bpm, delta)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = if (delta > 0) "+$delta" else "$delta",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

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
                        isRunning = metronome.start()
                        errorMessage = if (isRunning) null else "Could not open the audio output."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
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
        }
    }
}

@Composable
private fun BeatIndicator(tick: Long, isRunning: Boolean) {
    val activeBeat = if (tick > 0) ((tick - 1) % 4).toInt() else -1
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val isActive = isRunning && index == activeBeat
            key(tick, index) {
                var expanded by remember { mutableStateOf(isActive) }
                LaunchedEffect(isActive) {
                    if (isActive) expanded = false
                }
                val scale by animateFloatAsState(
                    targetValue = if (expanded) 1.4f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
                    label = "beat pulse",
                )
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .size(if (index == 0) 18.dp else 14.dp)
                        .background(
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) Color.Transparent else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
