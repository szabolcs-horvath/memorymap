package com.szabolcshorvath.memorymap.fakes

import android.content.Context
import android.location.Location
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
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

    /**
     * Whether the phone's last location is held back until [answerLastLocation], as on a phone that takes a while to
     * find it after the app starts.
     */
    @Volatile
    var answersLate = false

    private var unanswered: TaskCompletionSource<Location?>? = null

    override fun currentLocation(context: Context): Task<Location?> = Tasks.forResult(location())

    override fun lastLocation(context: Context): Task<Location?> = synchronized(this) {
        if (!answersLate) return Tasks.forResult(location())
        TaskCompletionSource<Location?>().also { unanswered = it }.task
    }

    /** Whether the app asked where the phone was last, and [answersLate] holds the answer back. */
    val isAskedForLastLocation: Boolean get() = synchronized(this) { unanswered != null }

    /** Answers the request for the last location held back by [answersLate]. */
    fun answerLastLocation() = synchronized(this) {
        val request = checkNotNull(unanswered) { "The app hasn't asked where the phone was last" }
        unanswered = null
        request.setResult(location())
    }

    private fun location(): Location? = position?.let { (latitude, longitude) ->
        Location("fake").also {
            it.latitude = latitude
            it.longitude = longitude
        }
    }
}
