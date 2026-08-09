package com.rudimentor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.PatternMode
import com.rudimentor.app.ui.theme.PaletteId
import kotlinx.coroutines.delay

private const val PrototypeBpm = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeLabScreen(
    settings: AppSettings,
    onStyleSelected: (BeatIndicatorStyle) -> Unit,
    onPaletteSelected: (PaletteId) -> Unit,
    onModeSelected: (PatternMode) -> Unit,
    onToggleBeat: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(true) }
    var activeBeat by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)
    LaunchedEffect(isPlaying, settings.pattern.size) {
        while (isPlaying) {
            delay(60_000L / PrototypeBpm)
            activeBeat = (activeBeat + 1) % settings.pattern.size
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PROTOTYPE LAB", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to main menu")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SOURCE-BACKED INDICATORS", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(
                        "Ten numbered research shortlist variants share one clock and your persisted pattern. Palette, style, mode, and every beat survive restarts.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PatternMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.mode == mode,
                                onClick = { onModeSelected(mode) },
                                label = { Text(if (mode == PatternMode.RightLeft) "R/L" else "ABSTRACT") },
                            )
                        }
                    }
                    Text("DEVELOPER PALETTE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    PaletteSwitcher(settings.paletteId, onPaletteSelected)
                    Button(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.fillMaxWidth().height(52.dp).semantics {
                            role = Role.Button
                            contentDescription = if (isPlaying) "Pause all indicator animations" else "Play all indicator animations"
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (isPlaying) "PAUSE ALL" else "PLAY ALL", fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(BeatIndicatorStyle.entries, key = BeatIndicatorStyle::number) { style ->
                PrototypeCard(
                    style = style,
                    settings = settings,
                    selected = style == settings.trackerStyle,
                    activeBeat = activeBeat,
                    onToggleBeat = onToggleBeat,
                    onSelect = { onStyleSelected(style) },
                )
            }
        }
    }
}

@Composable
private fun PaletteSwitcher(selected: PaletteId, onSelect: (PaletteId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PaletteId.entries.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { palette ->
                    FilterChip(
                        selected = palette == selected,
                        onClick = { onSelect(palette) },
                        label = { Text(palette.code) },
                        modifier = Modifier.weight(1f).height(48.dp).semantics {
                            contentDescription = "${palette.code} ${palette.displayName} palette"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrototypeCard(
    style: BeatIndicatorStyle,
    settings: AppSettings,
    selected: Boolean,
    activeBeat: Int,
    onToggleBeat: (Int) -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = style.number.toString(),
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
                Column(Modifier.weight(1f)) {
                    Text(style.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(style.source, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            BeatRow(
                pattern = settings.pattern,
                style = style,
                mode = settings.mode,
                activeBeat = activeBeat,
                onBeatClick = onToggleBeat,
            )
            Button(
                onClick = onSelect,
                enabled = !selected,
                modifier = Modifier.fillMaxWidth().height(48.dp).semantics {
                    role = Role.Button
                    contentDescription = if (selected) "Variant ${style.number} selected" else "Use variant ${style.number} in Metronome"
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (selected) "SELECTED" else "USE VARIANT ${style.number}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
