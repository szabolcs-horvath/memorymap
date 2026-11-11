package com.szabolcshorvath.memorymap.ui.common

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.szabolcshorvath.memorymap.util.PermissionUtil

/**
 * What a "Re-link" button does, shared by the memory screen and Settings: re-links at once when the app has full media
 * access, and otherwise asks for it first. Partial ("selected photos") access is enough to re-link the photos the user
 * selected, so it re-links after the request whenever any access was granted. Showing the run's progress stays with the
 * screen. Get it with [rememberRelinkRequester].
 */
@Stable
class RelinkRequester internal constructor(
    private val context: Context,
    private val relink: State<() -> Unit>,
    private val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
) {
    /** Called when the user taps the button. */
    fun start() {
        if (PermissionUtil.hasAllMediaPermissions(context)) relink.value() else askForAccess()
    }

    /** Asks for media access, then re-links with whatever was granted. */
    fun askForAccess() {
        launcher.launch(PermissionUtil.getRequiredMediaPermissions())
    }
}

/** A [RelinkRequester] that calls [relink] to re-link. */
@Composable
fun rememberRelinkRequester(relink: () -> Unit): RelinkRequester {
    val context = LocalContext.current
    val currentRelink = rememberUpdatedState(relink)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (PermissionUtil.hasAnyMediaPermission(context)) currentRelink.value()
    }
    return remember(context, launcher) { RelinkRequester(context, currentRelink, launcher) }
}
