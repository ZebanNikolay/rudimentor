package com.rudimentor.app.ui.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Runs [action] when the app leaves the foreground.
 *
 * Composition alone is not enough for anything that owns audio: leaving the app
 * with the home button or the recents switcher does not dispose the screen, so a
 * `DisposableEffect` never fires and the microphone or the click keeps running in
 * the background.
 */
@Composable
fun OnBackgrounded(action: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { action() }
}

/** Runs [action] every time the app comes back to the foreground. */
@Composable
fun OnForegrounded(action: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { action() }
}
