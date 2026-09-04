package com.rudimentor.app.ui.util

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.rudimentor.app.R
import com.rudimentor.app.util.AppLog

/**
 * The microphone permission as one button sees it: whether to ask the system again or to
 * send the learner to the app's page in the phone settings.
 *
 * Android stops showing the permission dialog after the second refusal. The request then
 * comes back denied at once, and a screen that only knows how to ask again offers a button
 * that does nothing -- a dead end at the very first step of the app (decision 212).
 */
class MicPermissionRequest internal constructor(
    /** True once the system will no longer ask; the button should open the settings. */
    val settingsNeeded: Boolean,
    /** Ask, or open the app's settings page when asking is no longer possible. */
    val request: () -> Unit,
) {
    /** Label for the one button behind [request]. */
    val buttonRes: Int
        get() = if (settingsNeeded) R.string.mic_permission_open_settings else R.string.practice_permission_button
}

/**
 * Remembers a request for [Manifest.permission.RECORD_AUDIO]. [onResult] gets the answer
 * of every system dialog; a return from the settings page is not a dialog, so the caller
 * re-reads the permission in [OnForegrounded] as the screens already do.
 */
@Composable
fun rememberMicPermissionRequest(onResult: (Boolean) -> Unit): MicPermissionRequest {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var settingsNeeded by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Denied with no rationale to show means the dialog was never shown this time: the
        // permission is set to "don't ask again" (or the app is not in the foreground).
        // Before the first request the rationale flag is false too, which is why this is
        // only read after a refusal.
        val silent = !granted && activity?.shouldShowRequestPermissionRationale(
            Manifest.permission.RECORD_AUDIO,
        ) == false
        if (silent) AppLog.event("permission", "microphone refused without a dialog")
        settingsNeeded = silent
        onResult(granted)
    }
    return remember(settingsNeeded) {
        MicPermissionRequest(
            settingsNeeded = settingsNeeded,
            request = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                when {
                    granted -> onResult(true)
                    settingsNeeded -> {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                            .onFailure { AppLog.error("permission", "app settings page: $it") }
                    }
                    else -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    }
}
