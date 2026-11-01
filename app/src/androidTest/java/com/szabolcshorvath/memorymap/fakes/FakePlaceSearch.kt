package com.szabolcshorvath.memorymap.fakes

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.szabolcshorvath.memorymap.util.ChosenPlace
import com.szabolcshorvath.memorymap.util.FoundPlace
import com.szabolcshorvath.memorymap.util.PlaceSearch
import java.io.IOException

/**
 * Place search without Google's search screen or its paid lookups: opening the search returns [nextPick] at once, as
 * if the user had searched for it and chosen it, and its details are [nextPick]'s. A null [nextPick] is a search the
 * user left. With [searchWorks] false it acts like a build without a Maps API key. Every test runs in a new process,
 * so it starts with nothing to find.
 */
object FakePlaceSearch : PlaceSearch {
    private const val PLACE_ID = "fake-place"

    @Volatile
    var nextPick: FoundPlace? = null

    @Volatile
    var searchWorks = true

    override fun isAvailable(): Boolean = searchWorks

    override val screen = object : ActivityResultContract<Unit, ChosenPlace?>() {
        override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<ChosenPlace?> =
            SynchronousResult(nextPick?.let { ChosenPlace(PLACE_ID, session = null) })

        override fun createIntent(context: Context, input: Unit): Intent =
            error("The fake place search answers at once and never opens a screen")

        override fun parseResult(resultCode: Int, intent: Intent?): ChosenPlace? = null
    }

    // Other places (such as one the map offers when tapped) are unknown, as when a lookup fails.
    override suspend fun details(context: Context, placeId: String, session: AutocompleteSessionToken?): FoundPlace =
        nextPick?.takeIf { placeId == PLACE_ID } ?: throw IOException("The fake place search knows no place $placeId")
}
