package com.rudimentor.app.ui.levels

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelCatalog
import com.rudimentor.app.data.levels.LevelColumn
import com.rudimentor.app.data.levels.LevelNodeState
import com.rudimentor.app.data.levels.LevelRole
import com.rudimentor.app.data.levels.LevelTier
import com.rudimentor.app.data.levels.LearningProgress
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles
import kotlin.math.roundToInt

@Composable
fun LevelsScreen(
    catalog: LevelCatalog,
    progress: LearningProgress,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
) {
    var selectedTierIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTier = catalog.tiers[selectedTierIndex.coerceIn(catalog.tiers.indices)]
    val selectedTierUnlocked = progress.isTierUnlocked(catalog, selectedTierIndex)
    BackHandler(onBack = onBack)

    Scaffold(containerColor = RudiColors.Bg) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AppToolbar(
                title = stringResource(R.string.levels_title),
                onBack = onBack,
                rightContent = {
                    Text(
                        text = stringResource(R.string.levels_streak, progress.streakDays),
                        style = RudiTextStyles.RowNumber,
                        color = RudiColors.Muted,
                    )
                },
            )
            Spacer(modifier = Modifier.height(14.dp))
            TierSelector(
                tiers = catalog.tiers,
                selectedIndex = selectedTierIndex,
                unlockedIndices = catalog.tiers.indices.filterTo(mutableSetOf()) {
                    progress.isTierUnlocked(catalog, it)
                },
                onSelect = { selectedTierIndex = it },
            )
            Spacer(modifier = Modifier.height(10.dp))
            MapLegend()
            Spacer(modifier = Modifier.height(4.dp))

            if (selectedTier.levels.isEmpty()) {
                LockedTier(
                    tier = selectedTier,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LevelMap(
                    tier = selectedTier,
                    progress = progress,
                    tierUnlocked = selectedTierUnlocked,
                    onOpenLevel = onOpenLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            val currentLevel = progress.currentLevel(selectedTier, selectedTierUnlocked)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            when {
                                currentLevel != null -> R.string.levels_next_level
                                selectedTierUnlocked && selectedTier.levels.isNotEmpty() -> R.string.levels_tier_complete
                                else -> R.string.levels_tier_locked
                            },
                        ).uppercase(),
                        style = RudiTextStyles.RowNumber,
                        color = RudiColors.Muted,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = currentLevel?.let { "${it.id} · ${it.pattern.joinToString("") { step -> step.label }}" }
                            ?: if (selectedTierUnlocked && selectedTier.levels.isNotEmpty()) {
                                stringResource(R.string.levels_required_path_complete)
                            } else {
                                stringResource(
                                    R.string.levels_complete_previous,
                                    previousTierId(catalog, selectedTierIndex),
                                )
                            },
                        style = RudiTextStyles.Timer,
                        color = if (currentLevel == null) RudiColors.RowNumber else RudiColors.Text,
                    )
                }
                LevelPlayButton(
                    onClick = { currentLevel?.let { onOpenLevel(it.id) } },
                    contentDescription = stringResource(R.string.levels_open_next),
                    active = currentLevel != null,
                )
            }
        }
    }
}

@Composable
private fun TierSelector(
    tiers: List<LevelTier>,
    selectedIndex: Int,
    unlockedIndices: Set<Int>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tiers.forEachIndexed { index, tier ->
            val selected = index == selectedIndex
            val unlocked = index in unlockedIndices
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        color = if (selected) RudiColors.Brick.copy(alpha = 0.13f) else RudiColors.Surface,
                        shape = shape,
                    )
                    .drawBehind {
                        drawRoundRect(
                            color = when {
                                selected -> RudiColors.Brick
                                unlocked -> RudiColors.Line
                                else -> RudiColors.PadMuteBorder
                            },
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = if (unlocked || selected) null else {
                                    PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                                },
                            ),
                        )
                    }
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.Tab
                        contentDescription = "Tier ${tier.id}, ${tier.name}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tier.id,
                    color = when {
                        selected -> RudiColors.BrickLit
                        unlocked -> RudiColors.Text
                        else -> RudiColors.RowNumber
                    },
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun MapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.levels_map_columns).uppercase(),
            modifier = Modifier.weight(1f),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            letterSpacing = 0.8.sp,
        )
        LegendHand(shape = PadShape.Square, label = stringResource(R.string.levels_right_lead))
        LegendHand(shape = PadShape.Round, label = stringResource(R.string.levels_left_lead))
    }
}

@Composable
private fun LegendHand(shape: PadShape, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pad(size = 11.dp, shape = shape, tone = PadTone.Normal, showLetter = false)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label.uppercase(),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun LockedTier(
    tier: LevelTier,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.alpha(0.7f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Pad(
            size = 54.dp,
            shape = PadShape.Square,
            tone = PadTone.Mute,
            letter = tier.id,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.levels_locked_title, tier.name),
            style = MaterialTheme.typography.titleMedium,
            color = RudiColors.Text,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.levels_locked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LevelMap(
    tier: LevelTier,
    progress: LearningProgress,
    tierUnlocked: Boolean,
    onOpenLevel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val maxRow = tier.levels.maxOf(Level::row)
    val mapHeight = MAP_VERTICAL_PADDING * 2 + MAP_ROW_HEIGHT * (maxRow + 1)
    val currentRow = progress.currentLevel(tier, tierUnlocked)?.row ?: 0
    LaunchedEffect(scrollState.maxValue, scrollState.viewportSize, tier.id) {
        if (scrollState.maxValue == 0) return@LaunchedEffect
        val currentY = with(density) {
            (mapHeight - MAP_VERTICAL_PADDING - MAP_ROW_HEIGHT * currentRow - NODE_SIZE).roundToPx()
        }
        scrollState.scrollTo(
            (currentY - scrollState.viewportSize / 2).coerceIn(0, scrollState.maxValue),
        )
    }

    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    0f to RudiColors.Bg,
                    0.32f to RudiColors.Bg,
                    0.5f to RudiColors.SurfaceAlt.copy(alpha = 0.3f),
                    0.68f to RudiColors.Bg,
                    1f to RudiColors.Bg,
                ),
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            val centerX = maxWidth / 2
            val levelsByRow = remember(tier) { tier.levels.groupBy(Level::row) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .drawBehind {
                        val centerPx = size.width / 2f
                        val nodePx = NODE_SIZE.toPx()
                        val gateHeightPx = GATE_HEIGHT.toPx()
                        val rowPx = MAP_ROW_HEIGHT.toPx()
                        val bottomPx = MAP_VERTICAL_PADDING.toPx()
                        val centerLevels = tier.levels
                            .filter { it.column == LevelColumn.Center }
                            .sortedBy(Level::row)

                        centerLevels.zipWithNext().forEach { (lower, upper) ->
                            val lowerHeight = if (lower.role == LevelRole.Checkpoint) gateHeightPx else nodePx
                            val upperHeight = if (upper.role == LevelRole.Checkpoint) gateHeightPx else nodePx
                            val lowerTop = size.height - bottomPx - lower.row * rowPx - lowerHeight
                            val upperTop = size.height - bottomPx - upper.row * rowPx - upperHeight
                            val passed = progress.stateOf(lower, tier, tierUnlocked) == LevelNodeState.Completed
                            drawLine(
                                color = if (passed) RudiColors.Brick else RudiColors.Line,
                                start = Offset(centerPx, lowerTop),
                                end = Offset(centerPx, upperTop + upperHeight),
                                strokeWidth = (if (passed) 2.dp else 1.5.dp).toPx(),
                            )
                            drawCircle(
                                color = if (passed) RudiColors.BrickLit else RudiColors.RowNumber,
                                radius = (if (passed) 2.6.dp else 2.dp).toPx(),
                                center = Offset(centerPx, lowerTop),
                            )
                        }

                        levelsByRow.forEach { (row, rowLevels) ->
                            val center = rowLevels.single { it.column == LevelColumn.Center }
                            rowLevels.filter { it.column != LevelColumn.Center }.forEach { side ->
                                val direction = if (side.column == LevelColumn.Left) -1f else 1f
                                val y = size.height - bottomPx - row * rowPx - nodePx / 2f
                                val startX = centerPx + direction * nodePx / 2f
                                val endX = centerPx + direction * (MAP_COLUMN_WIDTH.toPx() - nodePx / 2f)
                                val passed = progress.stateOf(center, tier, tierUnlocked) == LevelNodeState.Completed
                                drawLine(
                                    color = if (passed) RudiColors.Brick else RudiColors.Line,
                                    start = Offset(startX, y),
                                    end = Offset(endX, y),
                                    strokeWidth = (if (passed) 2.dp else 1.5.dp).toPx(),
                                    pathEffect = if (passed) null else {
                                        PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                                    },
                                )
                            }
                        }
                    },
            ) {
                tier.levels.forEach { level ->
                    val nodeWidth = if (level.role == LevelRole.Checkpoint) GATE_WIDTH else NODE_SIZE
                    val nodeHeight = if (level.role == LevelRole.Checkpoint) GATE_HEIGHT else NODE_SIZE
                    val x = centerX + when (level.column) {
                        LevelColumn.Left -> -MAP_COLUMN_WIDTH
                        LevelColumn.Center -> 0.dp
                        LevelColumn.Right -> MAP_COLUMN_WIDTH
                    } - nodeWidth / 2
                    val y = mapHeight - MAP_VERTICAL_PADDING - MAP_ROW_HEIGHT * level.row - nodeHeight
                    LevelMapNode(
                        level = level,
                        state = progress.stateOf(level, tier, tierUnlocked),
                        onClick = { onOpenLevel(level.id) },
                        modifier = Modifier.absoluteOffset(x = x, y = y),
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelMapNode(
    level: Level,
    state: LevelNodeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state != LevelNodeState.Locked
    val pulseTransition = rememberInfiniteTransition(label = "currentLevelPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "currentLevelGlow",
    )
    val description = "${level.id}, ${level.title}, ${state.name.lowercase()}"

    if (level.role == LevelRole.Checkpoint) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = modifier
                .size(GATE_WIDTH, GATE_HEIGHT)
                .background(
                    if (state == LevelNodeState.Completed) RudiColors.Brick else RudiColors.Surface,
                    shape,
                )
                .drawBehind {
                    drawRoundRect(
                        color = when (state) {
                            LevelNodeState.Completed -> RudiColors.BrickLit
                            LevelNodeState.Current, LevelNodeState.Available -> RudiColors.Brick
                            LevelNodeState.Locked -> RudiColors.PadMuteBorder
                        },
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = if (state == LevelNodeState.Locked) {
                                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                            } else {
                                null
                            },
                        ),
                    )
                }
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = level.title.uppercase(),
                style = RudiTextStyles.RowNumber,
                color = if (state == LevelNodeState.Completed) RudiColors.Text else RudiColors.Muted,
                letterSpacing = 0.8.sp,
            )
        }
        return
    }

    Box(
        modifier = modifier
            .size(NODE_SIZE)
            .then(
                if (state == LevelNodeState.Current) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to RudiColors.BrickLit.copy(alpha = pulse),
                                1f to androidx.compose.ui.graphics.Color.Transparent,
                                radius = size.minDimension * 0.8f,
                            ),
                            radius = size.minDimension * 0.8f,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Pad(
            size = NODE_SIZE,
            shape = level.leadHand.padShape,
            tone = when (state) {
                LevelNodeState.Completed -> PadTone.Normal
                LevelNodeState.Current, LevelNodeState.Available -> PadTone.Accent
                LevelNodeState.Locked -> PadTone.Mute
            },
            lit = state == LevelNodeState.Completed,
            letter = level.displayNumber.toIntOrNull()?.toString() ?: level.displayNumber,
            pressed = false,
            letterFraction = 0.32f,
        )
    }
}

private fun previousTierId(catalog: LevelCatalog, selectedIndex: Int): String =
    catalog.tiers[(selectedIndex - 1).coerceAtLeast(0)].id

private val NODE_SIZE = 46.dp
private val GATE_WIDTH = 152.dp
private val GATE_HEIGHT = 38.dp
private val MAP_ROW_HEIGHT = 78.dp
private val MAP_COLUMN_WIDTH = 74.dp
private val MAP_VERTICAL_PADDING = 30.dp
