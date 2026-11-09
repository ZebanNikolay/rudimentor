package com.rudimentor.app.ui.levels

import com.rudimentor.app.ui.component.ScreenInsets
import androidx.compose.foundation.layout.fillMaxHeight
import com.rudimentor.app.ui.component.isLandscape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.rudimentor.app.data.levels.RankProgress
import com.rudimentor.app.data.levels.UnlockRule
import com.rudimentor.app.ui.component.AppToolbar
import com.rudimentor.app.ui.component.SquareIconButton
import com.rudimentor.app.ui.component.Pad
import com.rudimentor.app.ui.component.PadShape
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.component.RudiButton
import com.rudimentor.app.ui.component.RudiButtonStyle
import com.rudimentor.app.ui.component.RudiTab
import com.rudimentor.app.ui.component.RudiTabRow
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiTextStyles

/**
 * All maps of the curriculum behind one tab row (decision 111). The tabs come from the
 * curriculum package, and so do their gates: a locked tab still draws its whole map so the
 * learner can scroll it and see what is coming, and states the gate on a banner over it
 * (decision 123). Difficulty is chosen once for the whole screen from the toolbar, not per level.
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
    onOpenSettings: () -> Unit,
    /** Opens the sound check, from the node on the map or from the plate above it. */
    onOpenSoundCheck: () -> Unit,
    /** Whether the sound check has already been walked once on this device. */
    soundCheckDone: Boolean,
    /**
     * Whether the click sounds on the selected output. The plate promises only what this
     * visit would actually measure: without a click there is no headphones step (decision 178).
     */
    clickSounds: Boolean,
    /** Whether the learner has closed the plate that calls for the sound check. */
    soundCheckPlateHidden: Boolean,
    /** Closes that plate for good. The node on the map keeps the check reachable. */
    onHideSoundCheckPlate: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var rankDialogVisible by remember { mutableStateOf(false) }

    val activeTab = course.curriculum.tab(activeTabId) ?: course.tabs.first()
    val unlocked = progress.isTabUnlocked(activeTab)
    val catalog = course.catalog(activeTab.id)

    val landscape = isLandscape()
    val toolbarActions: @Composable () -> Unit = {
        RankButton(rank = rank, onClick = { rankDialogVisible = true })
        Spacer(modifier = Modifier.width(8.dp))
        // The settings of practising belong to the map, not to the main menu:
        // the metronome has its own sheet and the two were read as one
        // (decision 158).
        SquareIconButton(
            onClick = onOpenSettings,
            contentDescription = stringResource(R.string.menu_settings),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_menu_settings),
                contentDescription = null,
                tint = RudiColors.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    val tabs: @Composable (Modifier) -> Unit = { modifier ->
        FamilyTabs(
            tabs = course.tabs,
            progress = progress,
            activeTabId = activeTab.id,
            onSelectTab = onSelectTab,
            modifier = modifier,
        )
    }
    // Before the first level there is a check that the phone can hear the pad at all.
    // The plate is the one-time call for it and can be closed; the way in lives on the
    // map itself, so closing it never takes the check away (decision 171).
    val showPlate = !soundCheckDone && !soundCheckPlateHidden
    val plate: @Composable () -> Unit = {
        SoundCheckPlate(
            clickSounds = clickSounds,
            onClick = onOpenSoundCheck,
            onDismiss = onHideSoundCheckPlate,
        )
    }
    val map: @Composable (Modifier, Boolean) -> Unit = { modifier, bannerOver ->
        Box(modifier = modifier) {
            LevelMap(
                catalog = catalog!!,
                progress = progress,
                rank = rank,
                locked = !unlocked,
                onOpenLevel = onOpenLevel,
                soundCheckDone = soundCheckDone,
                onOpenSoundCheck = onOpenSoundCheck,
                modifier = Modifier.fillMaxSize(),
                horizontal = landscape,
            )
            if (!unlocked && bannerOver) {
                LockedBanner(
                    text = gateText(activeTab, course),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                )
            }
        }
    }

    Scaffold(containerColor = RudiColors.Bg, contentWindowInsets = ScreenInsets) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (landscape) {
                // On its side the screen is a few rows high, and the tabs, the plate and the
                // bar of the next level left the map a strip of 80 dp. The tabs move into the
                // toolbar, and everything that is not the map stands in a column on the right
                // of it, so the map keeps the whole height (decision 259).
                AppToolbar(
                    title = stringResource(R.string.levels_title),
                    onBack = onBack,
                    rightContent = {
                        Spacer(modifier = Modifier.width(16.dp))
                        tabs(Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(16.dp))
                        toolbarActions()
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (catalog == null) {
                    MissingMap(
                        tab = activeTab,
                        course = course,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        map(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            false,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .width(LANDSCAPE_SIDE_WIDTH)
                                .fillMaxHeight(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                if (!unlocked) {
                                    LockedBanner(
                                        text = gateText(activeTab, course),
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                } else if (showPlate) {
                                    plate()
                                }
                            }
                            if (unlocked) {
                                NextLevelBar(
                                    catalog = catalog,
                                    progress = progress,
                                    rank = rank,
                                    onOpenLevel = onOpenLevel,
                                )
                            }
                        }
                    }
                }
            } else {
                AppToolbar(
                    title = stringResource(R.string.levels_title),
                    onBack = onBack,
                    rightContent = { toolbarActions() },
                )
                Spacer(modifier = Modifier.height(12.dp))
                tabs(Modifier)
                Spacer(modifier = Modifier.height(6.dp))
                if (showPlate) {
                    plate()
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (catalog == null) {
                    // Nothing to look at yet: the map of this family is still being written.
                    MissingMap(
                        tab = activeTab,
                        course = course,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    map(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        true,
                    )
                    if (unlocked) {
                        NextLevelBar(
                            catalog = catalog,
                            progress = progress,
                            rank = rank,
                            onOpenLevel = onOpenLevel,
                        )
                    }
                }
            }
        }
    }

    if (rankDialogVisible) {
        RankDialog(
            rank = rank,
            onConfirm = {
                if (it != rank) onSelectRank(it)
                rankDialogVisible = false
            },
            onDismiss = { rankDialogVisible = false },
        )
    }
}

/**
 * The one-time call for the sound check, above the map.
 *
 * It is a nudge, not the way in: the node on the map is that, and it is always there. So the
 * plate can be closed with its cross and never comes back, and it disappears by itself once
 * the check has been walked (decision 171).
 */
@Composable
private fun SoundCheckPlate(clickSounds: Boolean, onClick: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RudiColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.sound_check_title),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.BrickLit,
            letterSpacing = 1.6.sp,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = stringResource(
                if (clickSounds) {
                    R.string.levels_sound_check_call
                } else {
                    R.string.levels_sound_check_call_silent
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = RudiColors.Text,
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Two named buttons, dismissive first and confirming last, as Material lays out the
        // actions of a card: the plate used to carry the round play button of a level, which
        // promised an attempt, and a 28 dp cross, which was below the touch target size and
        // never said whether it meant "later" or "never" (decision 174).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            RudiButton(
                text = stringResource(R.string.levels_sound_check_hide),
                onClick = onDismiss,
                style = RudiButtonStyle.Secondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            RudiButton(
                text = stringResource(R.string.levels_sound_check_open),
                onClick = onClick,
            )
        }
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

/**
 * One row, one slot per family, on the Material tab row wrapped as [RudiTabRow]: the tab set is
 * fixed and small, so the tabs split the width evenly instead of sizing to their titles — a short
 * title used to collapse into a chip next to the long ones (decision 117). Under each title the
 * tab spells the family figure in R/L letters instead of carrying an icon (decision 120).
 */
@Composable
private fun FamilyTabs(
    tabs: List<CurriculumTab>,
    progress: LearningProgress,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noSticking = stringResource(R.string.levels_tab_sticking_none)
    RudiTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0),
        modifier = modifier,
    ) {
        tabs.forEach { tab ->
            val enabled = tab.available && progress.isTabUnlocked(tab)
            val selected = tab.id == activeTabId
            RudiTab(
                // The strip prints the short label when the family has one; the full
                // name still reaches the screen reader through [tabDescription].
                title = tab.tabTitle.uppercase(),
                subtitle = tab.sticking ?: noSticking,
                selected = selected,
                // A locked tab still opens its own gate explanation, so it stays clickable.
                enabled = enabled || !selected,
                description = tabDescription(tab),
                onClick = { onSelectTab(tab.id) },
            )
        }
    }
}

/**
 * Sticking is spoken as words, not as letters: "Singles, right left right left" instead of
 * "Singles, R L R L". A family without a figure of its own is announced by name only.
 */
@Composable
private fun tabDescription(tab: CurriculumTab): String {
    val sticking = tab.sticking ?: return tab.title
    val right = stringResource(R.string.levels_tab_hand_right)
    val left = stringResource(R.string.levels_tab_hand_left)
    val hands = sticking.map { hand -> if (hand == 'R') right else left }.joinToString(" ")
    return stringResource(R.string.levels_tab_description, tab.title, hands)
}

/** What opens this family, in one sentence. */
@Composable
private fun gateText(tab: CurriculumTab, course: LevelCourse): String =
    when (val unlock = tab.unlock) {
        UnlockRule.Always -> stringResource(R.string.levels_tab_planned)
        UnlockRule.Never -> stringResource(R.string.levels_tab_planned)
        is UnlockRule.LessonRank -> stringResource(
            R.string.levels_tab_locked,
            course.level(unlock.lessonId)?.displayCode ?: unlock.lessonId,
            course.family(unlock.lessonId)?.name ?: unlock.lessonId.substringBefore('.'),
            unlock.rank.displayName,
        )
    }

/** A tab whose map is still being written: there is no tree to look at. */
@Composable
private fun MissingMap(
    tab: CurriculumTab,
    course: LevelCourse,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = gateText(tab, course),
            modifier = Modifier.padding(horizontal = 32.dp),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            textAlign = TextAlign.Center,
            letterSpacing = 0.8.sp,
        )
    }
}

/**
 * The gate of a locked family, stated over its map (decision 123). The map underneath stays
 * whole and scrollable — the banner only says that nothing can be started yet, so the learner
 * can still look through what is coming. It sits at the top so it never covers the first rows,
 * which is where the map opens.
 */
@Composable
private fun LockedBanner(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(RudiColors.SurfaceAlt.copy(alpha = 0.94f), RoundedCornerShape(12.dp))
            .border(1.dp, RudiColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.levels_tab_locked_badge).uppercase(),
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Brick,
            letterSpacing = 1.6.sp,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = text,
            style = RudiTextStyles.RowNumber,
            color = RudiColors.Muted,
            textAlign = TextAlign.Center,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun RankButton(rank: PracticeRank, onClick: () -> Unit) {
    val difficultyDescription = stringResource(R.string.levels_rank_button_cd, rank.displayName)
    Text(
        text = rank.displayName.uppercase(),
        modifier = Modifier
            .border(1.dp, RudiColors.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = difficultyDescription },
        style = RudiTextStyles.RowNumber,
        color = RudiColors.Muted,
        letterSpacing = 1.2.sp,
    )
}

/** Illustration of a rank, drawn on dark surfaces only (decision 110 cutout limitation). */
@DrawableRes
private fun rankArt(rank: PracticeRank): Int = when (rank) {
    PracticeRank.Practice -> R.drawable.art_rank_practice
    PracticeRank.Groove -> R.drawable.art_rank_groove
    PracticeRank.Stage -> R.drawable.art_rank_stage
}

/**
 * Difficulty picker. Tapping a row only moves the local highlight and swaps the illustration
 * above the list; the choice is applied to the screen when the user confirms.
 */
@Composable
private fun RankDialog(
    rank: PracticeRank,
    onConfirm: (PracticeRank) -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf(rank) }
    // A hand-built dialog, not `AlertDialog`: the illustration is the point of this screen and
    // the platform width kept it postage-stamp sized (decision 131). The window fills the width
    // minus the Material dialog margin, and the art takes the whole width above the rows.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DIALOG_MARGIN)
                .clip(RoundedCornerShape(20.dp))
                .background(RudiColors.SurfaceAlt)
                .border(1.dp, RudiColors.Line, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.levels_rank_dialog_title).uppercase(),
                style = RudiTextStyles.Timer,
                color = RudiColors.Text,
            )
            // The art is a square canvas whose drawing lives in the middle half of it, so a
            // square slot spent a third of the dialog height on transparent pixels (decision
            // 163). The slot is a band instead and the picture is cropped to it: the width
            // still sets the scale, so the drum keeps the size it had, and only the empty rows
            // above and below it are cut.
            Image(
                painter = painterResource(rankArt(pending)),
                contentDescription = stringResource(R.string.levels_rank_art_cd, pending.displayName),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(RANK_ART_RATIO),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PracticeRank.entries.forEach { option ->
                    val selected = option == pending
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) RudiColors.BrickBright else RudiColors.Line,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { pending = option }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = option.displayName.uppercase(),
                            style = RudiTextStyles.Timer,
                            color = if (selected) RudiColors.Text else RudiColors.Muted,
                        )
                        Text(
                            // Tempo and density belong to the level, not the rank: the
                            // card describes the step, the level card gives the numbers.
                            text = stringResource(option.description),
                            style = RudiTextStyles.RowNumber,
                            color = RudiColors.Muted,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.levels_rank_cancel), color = RudiColors.Muted)
                }
                TextButton(onClick = { onConfirm(pending) }) {
                    Text(
                        text = stringResource(R.string.levels_rank_confirm),
                        color = RudiColors.BrickBright,
                    )
                }
            }
        }
    }
}

/** Material keeps 28.dp between a dialog and the screen edge; the window keeps the rest. */
private val DIALOG_MARGIN = 24.dp

/**
 * Width over height of the illustration slot. The drawing of every rank art sits inside the
 * middle 50% of its square, centred, so a 1.7 band keeps the whole drawing with a margin of
 * about 8 dp above and below it and drops the rest.
 */
private const val RANK_ART_RATIO = 1.7f


@Composable
private fun LevelMap(
    catalog: LevelCatalog,
    progress: LearningProgress,
    rank: PracticeRank,
    onOpenLevel: (String) -> Unit,
    soundCheckDone: Boolean,
    onOpenSoundCheck: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    /**
     * Lays the path from left to right instead of bottom to top. On a phone held on its side
     * the vertical map was a narrow strip a row or two high; along the long edge the same
     * tree shows a dozen rows at once, with the three lanes stacked across (decision 259).
     */
    horizontal: Boolean = false,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    // A locked family has no level to start, so no node is highlighted as the current one and
    // every pad reads as locked — the map is there to be read, not to be played.
    val currentLevel = if (locked) null else progress.currentLevel(catalog, rank)
    val currentRow = currentLevel?.row ?: 0
    // The level the sound check hangs under: the first one of the main path.
    val soundCheckAnchor = remember(catalog) {
        val firstRow = catalog.levels.filter { it.row == 0 }
        firstRow.firstOrNull { it.column == LevelColumn.Center } ?: firstRow.firstOrNull()
    }

    // The quiet band follows the main path: down the middle of a vertical map, across the
    // middle of a horizontal one.
    val bandStops = arrayOf(
        0f to RudiColors.Bg,
        0.32f to RudiColors.Bg,
        0.5f to RudiColors.SurfaceAlt.copy(alpha = 0.3f),
        0.68f to RudiColors.Bg,
        1f to RudiColors.Bg,
    )
    BoxWithConstraints(
        modifier = modifier.background(
            brush = if (horizontal) {
                Brush.verticalGradient(*bandStops)
            } else {
                Brush.horizontalGradient(*bandStops)
            },
        ),
    ) {
        val geometry = remember(horizontal, catalog.lastRow, maxWidth, maxHeight) {
            MapGeometry.of(horizontal, catalog.lastRow, maxWidth, maxHeight)
        }
        val mapWidth = geometry.width
        val mapHeight = geometry.height
        val panningX = mapWidth > maxWidth
        val panningY = mapHeight > maxHeight

        // Wait for the stored progress before placing the map, and place it again whenever the
        // current level moves. Storage answers a frame or two after the screen is composed, and
        // until it does every level looks unplayed: the map read that as "start of the tree",
        // scrolled to the bottom and stayed there, so the learner opened the map on levels they
        // had already cleared with the current one off the top edge (decision 221).
        val alongPath = if (horizontal) horizontalScroll else verticalScroll
        val acrossPath = if (horizontal) verticalScroll else horizontalScroll
        LaunchedEffect(
            alongPath.maxValue,
            alongPath.viewportSize,
            catalog.family.id,
            rank,
            progress.loaded,
            currentRow,
            horizontal,
        ) {
            if (alongPath.maxValue == 0 || !progress.loaded) return@LaunchedEffect
            val center = geometry.levelCenter(currentRow, 0f)
            val currentPx = with(density) { (if (horizontal) center.x else center.y).roundToPx() }
            alongPath.scrollTo(
                (currentPx - alongPath.viewportSize / 2).coerceIn(0, alongPath.maxValue),
            )
        }
        LaunchedEffect(acrossPath.maxValue, catalog.family.id, horizontal) {
            if (acrossPath.maxValue > 0) acrossPath.scrollTo(acrossPath.maxValue / 2)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (panningY) Modifier.verticalScroll(verticalScroll) else Modifier)
                .then(if (panningX) Modifier.horizontalScroll(horizontalScroll) else Modifier),
        ) {
            val states = remember(catalog, progress, rank, locked) {
                catalog.levels.associate {
                    it.id to if (locked) {
                        LevelNodeState.Locked
                    } else {
                        progress.stateOf(it, catalog, rank)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(mapWidth)
                    .height(mapHeight)
                    .drawBehind {
                        val halfNode = NODE_SIZE.toPx() / 2f
                        fun DpOffset.px() = Offset(x.toPx(), y.toPx())
                        fun centerOf(level: Level) =
                            geometry.levelCenter(level.row, level.column.direction).px()

                        // Leave through the edge of the node box, not its inscribed circle, so
                        // a diagonal starts outside the pad as well; along an axis this is the
                        // plain half of the node.
                        fun edges(from: Offset, to: Offset): Pair<Offset, Offset> {
                            val dx = to.x - from.x
                            val dy = to.y - from.y
                            val length = kotlin.math.hypot(dx, dy)
                            val unitX = dx / length
                            val unitY = dy / length
                            val reach = halfNode /
                                maxOf(kotlin.math.abs(unitX), kotlin.math.abs(unitY))
                            return Offset(from.x + unitX * reach, from.y + unitY * reach) to
                                Offset(to.x - unitX * reach, to.y - unitY * reach)
                        }

                        // Connectors follow decision 33: they start and end on the node edge,
                        // never crossing the pad, and an LED dot marks both ends. The layout
                        // only ever links neighbouring cells (decision 121), so a connector is
                        // either straight along a lane or a straight line into the cell beside
                        // it — in the same row or one row further along.
                        catalog.levels.forEach { level ->
                            val toCenter = centerOf(level)
                            level.prerequisiteIds.mapNotNull(catalog::level).forEach { prerequisite ->
                                val fromCenter = centerOf(prerequisite)
                                val passed = states[prerequisite.id] == LevelNodeState.Completed
                                val color = if (passed) RudiColors.Brick else RudiColors.Line
                                val stroke = (if (passed) 2.dp else 1.5.dp).toPx()
                                // An optional level branches off into the next lane; everything
                                // else runs straight along its own lane.
                                val across = if (horizontal) {
                                    toCenter.y - fromCenter.y
                                } else {
                                    toCenter.x - fromCenter.x
                                }
                                val branching = kotlin.math.abs(across) > 1f
                                val (start, end) = edges(fromCenter, toCenter)
                                drawLine(
                                    color = color,
                                    start = start,
                                    end = end,
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                    // The dash means "optional", not "not reached yet": a branch
                                    // stays dashed after the prerequisite is passed and only
                                    // turns brick red (decision 131).
                                    pathEffect = if (branching) {
                                        PathEffect.dashPathEffect(
                                            floatArrayOf(2.dp.toPx(), 3.dp.toPx()),
                                        )
                                    } else {
                                        null
                                    },
                                )

                                val dotColor = if (passed) RudiColors.BrickLit else RudiColors.PadLed
                                val dotRadius = (if (passed) 2.6.dp else 2.dp).toPx()
                                drawCircle(color = dotColor, radius = dotRadius, center = start)
                                drawCircle(color = dotColor, radius = dotRadius, center = end)
                            }
                        }

                        // The sound check hangs before the first level on a dashed line: the
                        // dash says it is not on the path to any rank and can be walked again
                        // at any time (decisions 171, 174).
                        if (soundCheckAnchor != null) {
                            val (start, end) = edges(
                                geometry.soundCheckCenter.px(),
                                centerOf(soundCheckAnchor),
                            )
                            drawLine(
                                color = if (soundCheckDone) RudiColors.Brick else RudiColors.Line,
                                start = start,
                                end = end,
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(2.dp.toPx(), 3.dp.toPx()),
                                ),
                            )
                            val dotColor = if (soundCheckDone) {
                                RudiColors.BrickLit
                            } else {
                                RudiColors.PadLed
                            }
                            drawCircle(color = dotColor, radius = 2.dp.toPx(), center = start)
                            drawCircle(color = dotColor, radius = 2.dp.toPx(), center = end)
                        }
                    },
            ) {
                if (soundCheckAnchor != null) {
                    // Row zero, in the main lane: the lanes beside it are empty, so the node
                    // can be named in place instead of leaving the learner to guess the
                    // pictogram (decision 174). The caption stays English in every locale.
                    // Beside the node on a vertical map, under it on a horizontal one.
                    val center = geometry.soundCheckCenter
                    val caption: @Composable () -> Unit = {
                        Text(
                            text = stringResource(R.string.sound_check_title),
                            style = RudiTextStyles.RowNumber,
                            color = if (soundCheckDone) RudiColors.Muted else RudiColors.BrickLit,
                            letterSpacing = 1.6.sp,
                            maxLines = 1,
                            modifier = Modifier.clickable(onClick = onOpenSoundCheck),
                        )
                    }
                    if (horizontal) {
                        Column(
                            modifier = Modifier
                                .absoluteOffset(
                                    x = center.x - SOUND_CHECK_CAPTION_WIDTH / 2,
                                    y = center.y - NODE_SIZE / 2,
                                )
                                .width(SOUND_CHECK_CAPTION_WIDTH),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SoundCheckMapNode(done = soundCheckDone, onClick = onOpenSoundCheck)
                            Spacer(modifier = Modifier.height(6.dp))
                            caption()
                        }
                    } else {
                        Row(
                            modifier = Modifier.absoluteOffset(
                                x = center.x - NODE_SIZE / 2,
                                y = center.y - NODE_SIZE / 2,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SoundCheckMapNode(done = soundCheckDone, onClick = onOpenSoundCheck)
                            Spacer(modifier = Modifier.width(10.dp))
                            caption()
                        }
                    }
                }
                catalog.levels.forEach { level ->
                    val center = geometry.levelCenter(level.row, level.column.direction)
                    LevelMapNode(
                        level = level,
                        state = states.getValue(level.id),
                        rank = rank,
                        rankProgress = progress.forLevel(level.id, rank),
                        onClick = { onOpenLevel(level.id) },
                        modifier = Modifier.absoluteOffset(
                            x = center.x - NODE_SIZE / 2,
                            y = center.y - NODE_SIZE / 2,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Where the nodes of the map stand, in either direction.
 *
 * A row is a step along the path, a column is a lane across it. Upright the path climbs from
 * the bottom with the lanes side by side; on its side the path runs from left to right with
 * the Left lane on top. The sound check owns the step before row 0 (decision 174).
 */
private class MapGeometry private constructor(
    val horizontal: Boolean,
    val width: Dp,
    val height: Dp,
) {
    fun levelCenter(row: Int, direction: Float): DpOffset = if (horizontal) {
        DpOffset(
            x = MAP_HORIZONTAL_LEAD + SOUND_CHECK_ROW_HEIGHT + MAP_ROW_HEIGHT * row + NODE_SIZE / 2,
            y = height / 2 + MAP_LANE_HEIGHT * direction,
        )
    } else {
        DpOffset(
            x = width / 2 + MAP_COLUMN_WIDTH * direction,
            y = height - MAP_VERTICAL_PADDING - SOUND_CHECK_ROW_HEIGHT - MAP_ROW_HEIGHT * row -
                NODE_SIZE / 2,
        )
    }

    val soundCheckCenter: DpOffset
        get() = if (horizontal) {
            DpOffset(x = MAP_HORIZONTAL_LEAD + NODE_SIZE / 2, y = height / 2)
        } else {
            DpOffset(x = width / 2, y = height - MAP_VERTICAL_PADDING - NODE_SIZE / 2)
        }

    companion object {
        fun of(horizontal: Boolean, lastRow: Int, maxWidth: Dp, maxHeight: Dp): MapGeometry =
            if (horizontal) {
                // The path is as long as it needs to be; across, the three lanes fill the
                // height when it is there and scroll when it is not.
                val length = MAP_HORIZONTAL_LEAD + SOUND_CHECK_ROW_HEIGHT +
                    MAP_ROW_HEIGHT * lastRow + NODE_SIZE + MAP_VERTICAL_PADDING
                val across = MAP_LANE_HEIGHT * 2 + NODE_SIZE + MAP_LANE_MARGIN * 2
                MapGeometry(true, maxOf(length, maxWidth), maxOf(across, maxHeight))
            } else {
                // The map needs room for its widest branch; when that fits on screen it simply
                // fills the width, so there is nothing to pan sideways. The sound-check
                // caption sits beside its node, so that room is always needed.
                val neededWidth = MAP_CENTER_WIDTH + MAP_COLUMN_WIDTH * 2
                val mapHeight = MAP_VERTICAL_PADDING * 2 + MAP_ROW_HEIGHT * (lastRow + 1) +
                    SOUND_CHECK_ROW_HEIGHT
                MapGeometry(false, maxOf(neededWidth, maxWidth), mapHeight)
            }
    }
}

/**
 * The sound check as a node of the map: a pad beside the first level, never locked.
 *
 * It carries no stars, no crown and no lock, because there is nothing to score and nothing to
 * fail — it only tells whether the phone hears the pad and how late it hears it. Its shape is
 * a square: like a required level it is the thing to do before the first attempt, and unlike
 * one it stays playable forever (decision 171).
 */
@Composable
private fun SoundCheckMapNode(
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "soundCheckPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "soundCheckGlow",
    )
    val label = stringResource(R.string.levels_sound_check_node)

    Box(
        modifier = modifier
            .size(NODE_SIZE)
            .then(
                if (done) {
                    Modifier
                } else {
                    // Un-walked it breathes like the current level, so the eye finds it first.
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
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Pad(
            size = NODE_SIZE,
            shape = PadShape.Square,
            tone = if (done) PadTone.Normal else PadTone.Accent,
            lit = done,
            iconRes = R.drawable.ic_sound_check,
            showLetter = true,
            pressed = false,
        )
    }
}

@Composable
private fun LevelMapNode(
    level: Level,
    state: LevelNodeState,
    rank: PracticeRank,
    rankProgress: RankProgress,
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
    val description = "${level.displayCode}, ${level.mapCaption(rank)}, ${state.name.lowercase()}"

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
            // The number alone repeats across tracks (`ST-01`, `RM-01`), so the node carries
            // the level code at a smaller size.
            letter = level.displayCode,
            pressed = false,
            letterFraction = 0.2f,
            // The result of the level lives on the node itself: stars in the bottom band,
            // and the crown where the LED dot sits (decision 126).
            stars = rankProgress.clampedStars,
            crown = rankProgress.crown,
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

/** On its side: the column beside the map with the plate, the gate and the next level. */
private val LANDSCAPE_SIDE_WIDTH = 280.dp
private val MAP_ROW_HEIGHT = 78.dp

/** The row the sound check owns, under row 0 of the levels (decision 174). */
private val SOUND_CHECK_ROW_HEIGHT = MAP_ROW_HEIGHT
private val MAP_COLUMN_WIDTH = 74.dp
private val MAP_CENTER_WIDTH = 200.dp
private val MAP_VERTICAL_PADDING = 30.dp

/** On a horizontal map: room before the sound check, enough for its caption underneath. */
private val MAP_HORIZONTAL_LEAD = 44.dp

/** On a horizontal map: the step between lanes, tighter than upright to fit a phone on its side. */
private val MAP_LANE_HEIGHT = 64.dp
private val MAP_LANE_MARGIN = 12.dp
private val SOUND_CHECK_CAPTION_WIDTH = 120.dp
