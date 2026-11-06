package com.szabolcshorvath.memorymap.util

import java.util.Locale
import kotlin.math.abs

/** Coordinates as people read them, for a place without a name: "47.4979° N, 19.0402° E". */
object Coordinates {
    // Four decimals are about 11 metres: as close as a tap on the map picks a spot.
    private const val FORMAT = "%.4f° %s"

    /** [latitude] and [longitude] with the phone's decimal separator ([locale]) and the hemisphere letters. */
    fun format(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): String {
        val north = String.format(locale, FORMAT, abs(latitude), if (latitude < 0) "S" else "N")
        val east = String.format(locale, FORMAT, abs(longitude), if (longitude < 0) "W" else "E")
        return "$north, $east"
    }
}
