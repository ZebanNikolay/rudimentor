package com.rudimentor.app.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The room every ordinary screen keeps clear: the system bars and the display cutout.
 *
 * The Material default stops at the bars. In portrait that is enough -- the cutout lives in
 * the status bar -- but turned on its side the camera hole is on a short edge with no bar
 * over it, and edge-to-edge content ran under it (decision 259).
 */
val ScreenInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing

/** True while the phone is turned on its side. */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * The widest a column of text and settings grows. A phone turned on its side is twice as
 * wide as the screens were drawn for, and a full-width line of 140 characters with a switch
 * at the far end of it is hard to read (decision 259).
 */
val READABLE_WIDTH: Dp = 600.dp

/** Caps a column at [READABLE_WIDTH]; the parent decides where the column stands. */
fun Modifier.readableWidth(): Modifier = widthIn(max = READABLE_WIDTH)
