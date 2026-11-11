package com.szabolcshorvath.memorymap.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task

/**
 * Where the phone is. Reached through `AppServices.location`, so UI tests can say where the phone is. Call it only
 * with location access: it never asks for it (see `MyLocationRequester`).
 */
interface LocationService {
    /** A fresh position, for a "my location" tap. Null when the phone's location is off or there is no fix yet. */
    fun currentLocation(context: Context): Task<Location?>

    /** The last known position, which costs nothing to read. Null when there is none. */
    fun lastLocation(context: Context): Task<Location?>
}

/** The position from Google Play services' fused location provider. */
@SuppressLint("MissingPermission") // Only called with location access (see LocationService).
object FusedLocationService : LocationService {
    override fun currentLocation(context: Context): Task<Location?> =
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)

    override fun lastLocation(context: Context): Task<Location?> =
        LocationServices.getFusedLocationProviderClient(context).lastLocation
}
