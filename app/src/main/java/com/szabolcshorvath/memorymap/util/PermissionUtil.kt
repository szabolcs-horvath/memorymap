package com.szabolcshorvath.memorymap.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtil {

    fun checkPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Whether the app may use the location, precise or approximate. */
    fun hasLocationPermission(context: Context): Boolean =
        checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun getRequiredMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasAllMediaPermissions(context: Context): Boolean =
        getRequiredMediaPermissions().all { checkPermission(context, it) }

    fun hasAnyMediaPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (checkPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
                return true
            }
        }
        return getRequiredMediaPermissions().any { checkPermission(context, it) }
    }

    fun hasImagePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            checkPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            return true
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkPermission(context, permission)
    }

    fun hasVideoPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            checkPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            return true
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkPermission(context, permission)
    }
}
