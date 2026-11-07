package com.rudimentor.app.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.waterfall
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rudimentor.app.ui.util.KeepScreenOn
import com.rudimentor.app.ui.util.OnForegrounded

/** Which way a screen asks the phone to turn. */
enum class StageOrientation {
    /** The practice flow: landscape either way round, whatever auto-rotate says. */
    Landscape,

    /** The metronome: follows the phone into either orientation, even with auto-rotate off. */
    Sensor,
}

/**
 * The one owner of the activity's orientation and of the immersive system bars, plus
 * (while a round runs) a screen that stays awake. [orientation] null leaves the phone to
 * the system.
 *
 * It is hoisted to the app level on purpose. Owning it per screen made the stage
 * flap between the attempt and its result -- the orientation was restored while
 * the next screen was already asking for landscape, which recreated the activity
 * and dropped the result on the floor (fix for the dev.15 report). The metronome had
 * the same second owner: the screens cross-fade, so the metronome left composition only
 * after the result screen had asked for landscape, and its reset handed the result to
 * portrait (decision 239). One effect keyed by the request cannot race itself.
 */
@Composable
fun LandscapeStage(orientation: StageOrientation?, keepScreenOn: Boolean) {
    val activity = LocalActivity.current

    DisposableEffect(activity, orientation) {
        if (orientation == null) return@DisposableEffect onDispose { }
        activity?.requestedOrientation = when (orientation) {
            StageOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            StageOrientation.Sensor -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // The bars step aside whenever the phone is actually in landscape for a stage screen:
    // always on the practice flow, only while turned on the metronome.
    val landscapeNow = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val immersive = orientation == StageOrientation.Landscape ||
        (orientation == StageOrientation.Sensor && landscapeNow)
    val window = activity?.window
    DisposableEffect(window, immersive) {
        if (!immersive || window == null) return@DisposableEffect onDispose { }
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Coming back from the background brings the system bars back with it, so the
    // immersive stage has to be re-applied instead of set once.
    OnForegrounded {
        if (!immersive || window == null) return@OnForegrounded
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
    }

    KeepScreenOn(active = keepScreenOn)
}

/**
 * Keeps content of an immersive stage screen clear of the display cutout and the rounded
 * corners of the device. [min] is the corner allowance: window insets say nothing
 * about the radius, so every edge keeps at least this much room.
 *
 * Only for the screens [LandscapeStage] runs immersive (the practice flow, the metronome
 * turned on its side). Their system bars are hidden, so the bars are not room to keep:
 * when they come back for a moment -- the lock screen, a swipe from the edge -- they
 * overlay the stage. Padding by them made the whole stage shrink by a bar while the
 * phone was locked and grow back on unlock, a visible jump each time (decision 248).
 */
@Composable
fun Modifier.stageSafePadding(min: Dp = STAGE_EDGE_MIN): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.displayCutout.union(WindowInsets.waterfall)
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
