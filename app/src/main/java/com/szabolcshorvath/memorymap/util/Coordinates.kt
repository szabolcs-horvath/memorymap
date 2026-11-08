package com.szabolcshorvath.memorymap.util

import android.content.Context
import com.szabolcshorvath.memorymap.R
import java.util.Locale
import kotlin.math.abs

/** Coordinates as people read them, for a place without a name: "47.4979° N, 19.0402° E". */
object Coordinates {
    // Four decimals are about 11 metres: as close as a tap on the map picks a spot.
    private const val NUMBER_FORMAT = "%.4f"

    /**
     * [latitude] and [longitude] with the phone's decimal separator ([locale]), and the hemisphere letters and the
     * order of the phone's language.
     */
    fun format(context: Context, latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): String {
        val north = context.getString(
            if (latitude < 0) R.string.latitude_south else R.string.latitude_north,
            String.format(locale, NUMBER_FORMAT, abs(latitude))
        )
        val east = context.getString(
            if (longitude < 0) R.string.longitude_west else R.string.longitude_east,
            String.format(locale, NUMBER_FORMAT, abs(longitude))
        )
        return context.getString(R.string.coordinates, north, east)
    }
}
