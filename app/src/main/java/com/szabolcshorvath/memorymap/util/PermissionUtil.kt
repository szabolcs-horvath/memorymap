package com.szabolcshorvath.memorymap.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtil {

    fun checkPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Whether the app may use the location, precise or approximate. */
    fun hasLocationPermission(context: Context): Boolean =
        checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun getRequiredMediaPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    fun hasAllMediaPermissions(context: Context): Boolean =
        getRequiredMediaPermissions().all { checkPermission(context, it) }

    fun hasAnyMediaPermission(context: Context): Boolean =
        hasPartialMediaAccess(context) || getRequiredMediaPermissions().any { checkPermission(context, it) }

    fun hasImagePermission(context: Context): Boolean =
        hasPartialMediaAccess(context) || checkPermission(context, Manifest.permission.READ_MEDIA_IMAGES)

    fun hasVideoPermission(context: Context): Boolean =
        hasPartialMediaAccess(context) || checkPermission(context, Manifest.permission.READ_MEDIA_VIDEO)

    /** Whether the user allowed access to selected photos and videos only. */
    private fun hasPartialMediaAccess(context: Context): Boolean =
        checkPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}
