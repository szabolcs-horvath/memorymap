package com.szabolcshorvath.memorymap.fakes

import android.content.Context
import android.location.Location
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.szabolcshorvath.memorymap.util.LocationService

/**
 * Says the phone is at [position] (latitude and longitude), at once. Null is a phone with location turned off, which
 * is how every test starts, since each runs in a new process. Location access is still Android's: grant it with
 * `TestData.grantLocationAccess`, or through the system dialog.
 */
object FakeLocationService : LocationService {
    @Volatile
    var position: Pair<Double, Double>? = null

    override fun currentLocation(context: Context): Task<Location?> = Tasks.forResult(location())

    override fun lastLocation(context: Context): Task<Location?> = Tasks.forResult(location())

    private fun location(): Location? = position?.let { (latitude, longitude) ->
        Location("fake").also {
            it.latitude = latitude
            it.longitude = longitude
        }
    }
}
