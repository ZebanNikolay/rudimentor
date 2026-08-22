package com.rudimentor.app.ui.levels

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R
import com.rudimentor.app.data.levels.CurriculumTab
import com.rudimentor.app.data.levels.LearningProgress
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelCatalog
import com.rudimentor.app.data.levels.LevelColumn
import com.rudimentor.app.data.levels.LevelCourse
import com.rudimentor.app.data.levels.LevelNodeState
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.UnlockRule
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * All maps of the curriculum behind one tab row (decision 111). The tabs come from the
 * curriculum package, and so do their gates: a locked tab explains what opens it instead of
 * showing a map. Difficulty is chosen once for the whole screen from the toolbar, not per level.
 */
@Composable
fun LevelsScreen(
    course: LevelCourse,
    progress: LearningProgress,
    rank: PracticeRank,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onSelectRank: (PracticeRank) -> Unit,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    var rankDialogVisible by remember { mutableStateOf(false) }

    val activeTab = course.curriculum.tab(activeTabId) ?: course.tabs.first()
    val unlocked = progress.isTabUnlocked(activeTab)
    val catalog = course.catalog(activeTab.id)

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
                    RankButton(rank = rank, onClick = { rankDialogVisible = true })
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            FamilyTabs(
                tabs = course.tabs,
                progress = progress,
                activeTabId = activeTab.id,
                onSelectTab = onSelectTab,
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (catalog == null || !unlocked) {
                LockedMap(
                    tab = activeTab,
                    course = course,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LevelMap(
                    catalog = catalog,
                    progress = progress,
                    rank = rank,
                    onOpenLevel = onOpenLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                NextLevelBar(
                    catalog = catalog,
                    progress = progress,
                    rank = rank,
                    onOpenLevel = onOpenLevel,
                )
            }
        }
    }

    if (rankDialogVisible) {
        RankDialog(
            rank = rank,
            onSelectRank = {
                onSelectRank(it)
                rankDialogVisible = false
            },
            onDismiss = { rankDialogVisible = false },
        )
    }
}

@Composable
private fun NextLevelBar(
    catalog: LevelCatalog,
    progress: LearningProgress,
    rank: PracticeRank,
    onOpenLevel: (String) -> Unit,
) {
    val currentLevel = progress.currentLevel(catalog, rank)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (currentLevel != null) R.string.levels_next_level else R.string.levels_family_complete,
                ).uppercase(),
                style = RudiTextStyles.RowNumber,
                color = RudiColors.Muted,
                letterSpacing = 1.6.sp,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = currentLevel?.headline(catalog.family, rank)
                    ?: stringResource(R.string.levels_required_path_complete),
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

@Composable
private fun FamilyTabs(
    tabs: List<CurriculumTab>,
    progress: LearningProgress,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            val enabled = tab.available && progress.isTabUnlocked(tab)
            val selected = tab.id == activeTabId
            Text(
                text = tab.title.uppercase(),
                modifier = Modifier
                    .background(
                        color = if (selected) RudiColors.SurfaceAlt else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) RudiColors.Brick else RudiColors.Line,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = enabled || !selected) { onSelectTab(tab.id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                style = RudiTextStyles.RowNumber,
                color = when {
                    selected -> RudiColors.Text
                    enabled -> RudiColors.Muted
                    else -> RudiColors.RowNumber
                },
                letterSpacing = 1.2.sp,
            )
        }
    }
}

/** A tab the learner has not earned yet, or one whose map is still being written. */
@Composable
private fun LockedMap(
    tab: CurriculumTab,
    course: LevelCourse,
    modifier: Modifier = Modifier,
) {
    val text = when (val unlock = tab.unlock) {
        UnlockRule.Always -> stringResource(R.string.levels_tab_planned)
        UnlockRule.Never -> stringResource(R.string.levels_tab_planned)
        is UnlockRule.LessonRank -> stringResource(
            R.string.levels_tab_locked,
            course.level(unlock.lessonId)?.displayNumber ?: unlock.lessonId,
            course.family(unlock.lessonId)?.name ?: unlock.lessonId.substringBefore('.'),
            unlock.rank.displayName,
        )
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 32.dp),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            textAlign = TextAlign.Center,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun RankButton(rank: PracticeRank, onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.levels_rank_button, rank.displayName.uppercase(), rank.hitsPerBeat),
        modifier = Modifier
            .border(1.dp, RudiColors.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Difficulty: ${rank.displayName}" },
        style = RudiTextStyles.RowNumber,
        color = RudiColors.Muted,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun RankDialog(
    rank: PracticeRank,
    onSelectRank: (PracticeRank) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RudiColors.SurfaceAlt,
        title = {
            Text(
                text = stringResource(R.string.levels_rank_dialog_title).uppercase(),
                style = RudiTextStyles.Timer,
                color = RudiColors.Text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PracticeRank.entries.forEach { option ->
                    val selected = option == rank
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (selected) RudiColors.Brick else RudiColors.Line,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelectRank(option) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = option.displayName.uppercase(),
                            style = RudiTextStyles.Timer,
                            color = if (selected) RudiColors.Text else RudiColors.Muted,
                        )
                        Text(
                            text = stringResource(R.string.levels_rank_hits, option.hitsPerBeat),
                            style = RudiTextStyles.RowNumber,
                            color = RudiColors.Muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.levels_rank_dismiss), color = RudiColors.Muted)
            }
        },
    )
}

@Composable
private fun LevelMap(
    catalog: LevelCatalog,
    progress: LearningProgress,
    rank: PracticeRank,
    onOpenLevel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val mapHeight = MAP_VERTICAL_PADDING * 2 + MAP_ROW_HEIGHT * (catalog.lastRow + 1)
    // The map is as wide as its widest branch, so every side level fits without overlap.
    val mapWidth = MAP_COLUMN_WIDTH * (2 * catalog.lastLateral) + MAP_CENTER_WIDTH
    val currentLevel = progress.currentLevel(catalog, rank)
    val currentRow = currentLevel?.row ?: 0

    LaunchedEffect(verticalScroll.maxValue, verticalScroll.viewportSize, catalog.family.id, rank) {
        if (verticalScroll.maxValue == 0) return@LaunchedEffect
        val currentY = with(density) {
            (mapHeight - MAP_VERTICAL_PADDING - MAP_ROW_HEIGHT * currentRow - NODE_SIZE).roundToPx()
        }
        verticalScroll.scrollTo(
            (currentY - verticalScroll.viewportSize / 2).coerceIn(0, verticalScroll.maxValue),
        )
    }
    LaunchedEffect(horizontalScroll.maxValue, catalog.family.id) {
        if (horizontalScroll.maxValue > 0) horizontalScroll.scrollTo(horizontalScroll.maxValue / 2)
    }

    Box(
        modifier = modifier.background(
            brush = Brush.horizontalGradient(
                0f to RudiColors.Bg,
                0.32f to RudiColors.Bg,
                0.5f to RudiColors.SurfaceAlt.copy(alpha = 0.3f),
                0.68f to RudiColors.Bg,
                1f to RudiColors.Bg,
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll),
        ) {
            val states = remember(catalog, progress, rank) {
                catalog.levels.associate { it.id to progress.stateOf(it, catalog, rank) }
            }
            Box(
                modifier = Modifier
                    .width(mapWidth)
                    .height(mapHeight)
                    .drawBehind {
                        val nodePx = NODE_SIZE.toPx()
                        val rowPx = MAP_ROW_HEIGHT.toPx()
                        val columnPx = MAP_COLUMN_WIDTH.toPx()
                        val bottomPx = MAP_VERTICAL_PADDING.toPx()

                        fun centerOf(level: Level) = Offset(
                            x = size.width / 2f + level.column.direction * columnPx * level.lateral,
                            y = size.height - bottomPx - level.row * rowPx - nodePx / 2f,
                        )

                        // One elbow per prerequisite: the required path draws as a straight
                        // chain, a branch turns once towards its side.
                        catalog.levels.forEach { level ->
                            val to = centerOf(level)
                            level.prerequisiteIds.mapNotNull(catalog::level).forEach { prerequisite ->
                                val from = centerOf(prerequisite)
                                val passed = states[prerequisite.id] == LevelNodeState.Completed
                                val color = if (passed) RudiColors.Brick else RudiColors.Line
                                val stroke = (if (passed) 2.dp else 1.5.dp).toPx()
                                val dash = if (passed) {
                                    null
                                } else {
                                    PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                                }
                                if (from.x != to.x) {
                                    drawLine(
                                        color = color,
                                        start = Offset(from.x, from.y),
                                        end = Offset(from.x, to.y),
                                        strokeWidth = stroke,
                                        pathEffect = dash,
                                    )
                                }
                                drawLine(
                                    color = color,
                                    start = Offset(from.x, to.y),
                                    end = Offset(to.x, to.y),
                                    strokeWidth = stroke,
                                    pathEffect = dash,
                                )
                                if (passed) {
                                    drawCircle(
                                        color = RudiColors.BrickLit,
                                        radius = 2.6.dp.toPx(),
                                        center = from,
                                    )
                                }
                            }
                        }
                    },
            ) {
                catalog.levels.forEach { level ->
                    val x = mapWidth / 2 +
                        MAP_COLUMN_WIDTH * (level.column.direction * level.lateral) -
                        NODE_SIZE / 2
                    val y = mapHeight - MAP_VERTICAL_PADDING - MAP_ROW_HEIGHT * level.row - NODE_SIZE
                    LevelMapNode(
                        level = level,
                        state = states.getValue(level.id),
                        rank = rank,
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
    rank: PracticeRank,
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
    val description = "${level.displayNumber}, ${level.mapCaption(rank)}, ${state.name.lowercase()}"

    Box(
        modifier = modifier
            .size(NODE_SIZE)
            .then(
                if (state == LevelNodeState.Current) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to RudiColors.BrickLit.copy(alpha = pulse),
                                1f to Color.Transparent,
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
            // Shape says where the level sits, not which hand leads (decision 111):
            // a square is a required level of the main path, a circle is an optional branch.
            shape = if (level.column.required) PadShape.Square else PadShape.Round,
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

/** Which way a column grows away from the required path. */
private val LevelColumn.direction: Float
    get() = when (this) {
        LevelColumn.Left -> -1f
        LevelColumn.Center -> 0f
        LevelColumn.Right -> 1f
    }

private val NODE_SIZE = 46.dp
private val MAP_ROW_HEIGHT = 78.dp
private val MAP_COLUMN_WIDTH = 74.dp
private val MAP_CENTER_WIDTH = 200.dp
private val MAP_VERTICAL_PADDING = 30.dp
