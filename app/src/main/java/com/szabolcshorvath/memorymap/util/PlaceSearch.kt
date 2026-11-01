package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity

/** A place the user searched for, as the location picker stores it. */
data class FoundPlace(val name: String?, val address: String?, val latitude: Double, val longitude: Double)

/** The place the user chose on the search screen: its id, and the search session to look up its details in. */
data class ChosenPlace(val placeId: String, val session: AutocompleteSessionToken?)

/**
 * Searching for places by name, for the location picker. Reached through `AppServices.placeSearch`, so UI tests can
 * answer with a place of their choice instead of Google's search screen and its paid lookups.
 */
interface PlaceSearch {
    /** Whether searching works: it needs a Maps API key. */
    fun isAvailable(): Boolean

    /**
     * The search screen. It returns the chosen place, or null when the user leaves it or it fails. A screen registers
     * its launcher with it once, when it is created.
     */
    val screen: ActivityResultContract<Unit, ChosenPlace?>

    /** The name, address and location of the place [placeId]; null if it has no location. Throws when it fails. */
    suspend fun details(context: Context, placeId: String, session: AutocompleteSessionToken?): FoundPlace?
}

/** Google's place search (the Places SDK): its own search screen, and Place Details lookups. */
object GooglePlaceSearch : PlaceSearch {
    private const val TAG = "GooglePlaceSearch"
    private val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)

    override fun isAvailable(): Boolean = Places.isInitialized()

    override val screen = object : ActivityResultContract<Unit, ChosenPlace?>() {
        override fun createIntent(context: Context, input: Unit): Intent = PlaceAutocomplete.createIntent(context) {
            setAutocompleteSessionToken(AutocompleteSessionToken.newInstance())
        }

        override fun parseResult(resultCode: Int, intent: Intent?): ChosenPlace? {
            if (intent == null) return null
            return when (resultCode) {
                PlaceAutocompleteActivity.RESULT_OK -> {
                    val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)
                    if (prediction == null) Log.e(TAG, "Autocomplete returned no place")
                    prediction?.let { ChosenPlace(it.placeId, PlaceAutocomplete.getSessionTokenFromIntent(intent)) }
                }

                PlaceAutocompleteActivity.RESULT_ERROR -> {
                    Log.e(TAG, "Autocomplete error: ${PlaceAutocomplete.getResultStatusFromIntent(intent)?.statusMessage}")
                    null
                }

                else -> null // The user left the search screen.
            }
        }
    }

    override suspend fun details(context: Context, placeId: String, session: AutocompleteSessionToken?): FoundPlace? {
        val place = Places.createClient(context).awaitFetchPlace(placeId, fields) { sessionToken = session }.place
        val location = place.location ?: return null
        return FoundPlace(place.displayName, place.formattedAddress, location.latitude, location.longitude)
    }
}
