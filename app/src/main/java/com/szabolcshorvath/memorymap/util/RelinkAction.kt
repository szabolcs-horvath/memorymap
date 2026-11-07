package com.szabolcshorvath.memorymap.util

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * What a "Re-link" button does, shared by the memory screen and Settings: calls [relink] at once when the app has full
 * media access, and otherwise asks for it first. Partial ("selected photos") access is enough to re-link the photos the
 * user selected, so [relink] runs after the request whenever any access was granted. Showing the run's progress stays
 * with the screen, in [relink].
 *
 * Create it as a property initializer of the fragment: it registers an activity result launcher.
 */
class RelinkAction(private val fragment: Fragment, private val relink: () -> Unit) {

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (fragment.viewLifecycleOwnerLiveData.value == null) return@registerForActivityResult
        val context = fragment.context ?: return@registerForActivityResult
        if (PermissionUtil.hasAnyMediaPermission(context)) relink()
    }

    /** Called when the user taps the button. */
    fun start() {
        val context = fragment.context ?: return
        if (PermissionUtil.hasAllMediaPermissions(context)) relink() else askForAccess()
    }

    /** Asks for media access, then re-links with whatever was granted. */
    fun askForAccess() {
        permissionLauncher.launch(PermissionUtil.getRequiredMediaPermissions())
    }
}
