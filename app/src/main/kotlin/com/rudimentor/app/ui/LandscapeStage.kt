package com.rudimentor.app.ui

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The landscape stage of the practice flow: forced orientation, hidden system bars
 * and (while an attempt runs) a screen that stays awake.
 *
 * It is hoisted to the app level on purpose. Owning it per screen made the stage
 * flap between the attempt and its result -- the orientation was restored while
 * the next screen was already asking for landscape, which recreated the activity
 * and dropped the result on the floor (fix for the dev.15 report).
 */
@Composable
fun LandscapeStage(landscape: Boolean, keepScreenOn: Boolean) {
    val activity = LocalActivity.current

    DisposableEffect(activity, landscape) {
        if (!landscape) return@DisposableEffect onDispose { }
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insets = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        insets?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(activity, keepScreenOn) {
        val window = activity?.window
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (keepScreenOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * Keeps content clear of the display cutout, the system bars and the rounded
 * corners of the device. [min] is the corner allowance: window insets say nothing
 * about the radius, so every edge keeps at least this much room.
 */
@Composable
fun Modifier.stageSafePadding(min: Dp = STAGE_EDGE_MIN): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    return with(density) {
        padding(
            start = maxOf(insets.getLeft(this, layoutDirection).toDp(), min),
            top = maxOf(insets.getTop(this).toDp(), min),
            end = maxOf(insets.getRight(this, layoutDirection).toDp(), min),
            bottom = maxOf(insets.getBottom(this).toDp(), min),
        )
    }
}

/** Enough to clear the rounded corner of a phone in landscape. */
val STAGE_EDGE_MIN: Dp = 12.dp
