package com.rudimentor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.rudimentor.app.audio.BeatPattern
import kotlinx.coroutines.delay

private const val PrototypeBpm = 120
private const val PrototypeBeats = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeLabScreen(
    selectedStyle: BeatIndicatorStyle,
    onStyleSelected: (BeatIndicatorStyle) -> Unit,
    onBack: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(true) }
    var activeBeat by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(60_000L / PrototypeBpm)
            activeBeat = (activeBeat + 1) % PrototypeBeats
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PROTOTYPE LAB",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to main menu")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "BEAT INDICATORS",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "All 10 variants share a $PrototypeBpm BPM clock and a 4 + 3 pattern. Tap beats to compare normal/accent or R/L states.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .semantics {
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
            items(BeatIndicatorStyle.entries, key = BeatIndicatorStyle::name) { style ->
                PrototypeCard(
                    style = style,
                    selected = style == selectedStyle,
                    activeBeat = activeBeat,
                    onSelect = { onStyleSelected(style) },
                )
            }
        }
    }
}

@Composable
private fun PrototypeCard(
    style: BeatIndicatorStyle,
    selected: Boolean,
    activeBeat: Int,
    onSelect: () -> Unit,
) {
    var pattern by remember(style) {
        mutableStateOf(
            generateSequence(BeatPattern.default()) { it.addBeat() }
                .first { it.size == PrototypeBeats },
        )
    }
    var showSticking by remember(style) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(style.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (selected) "Selected for Metronome" else "Prototype",
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                OutlinedButton(
                    onClick = { showSticking = !showSticking },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (showSticking) "R/L" else "ABSTRACT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pattern.accents.forEachIndexed { index, isAccent ->
                    if (index == 4) Spacer(Modifier.size(18.dp)) else if (index > 0) Spacer(Modifier.size(4.dp))
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        BeatIndicator(
                            style = style,
                            beatNumber = index + 1,
                            isAccent = isAccent,
                            hand = pattern.hands[index],
                            showSticking = showSticking,
                            isActive = index == activeBeat,
                            onClick = {
                                pattern = if (showSticking) pattern.toggleHand(index) else pattern.toggleAccent(index)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSelect,
                enabled = !selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (selected) {
                            "${style.displayName} selected for Metronome"
                        } else {
                            "Use ${style.displayName} in Metronome"
                        }
                    },
                shape = RoundedCornerShape(16.dp),
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (selected) "SELECTED" else "USE IN METRONOME", fontWeight = FontWeight.Bold)
            }
        }
    }
}
