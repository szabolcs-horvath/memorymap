package com.szabolcshorvath.memorymap.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Groups markers at the same spot into one pin (before the map's own zoom-based clustering).
 *
 * Each pin has an anchor: the first item, in list order, that fit no existing pin. An item joins the nearest pin whose
 * anchor is closer than [SAME_LOCATION_METERS], or closer than [SAME_PLACE_METERS] when both have the same place name
 * and address. Otherwise it starts a new pin. Distances are measured to the anchor only, so pins don't chain: two
 * items of one pin are less than twice the threshold apart.
 *
 * Anchors are kept in a grid of cells [SAME_PLACE_METERS] high, so each item is compared only with the anchors in the
 * cells around it, not with every other item.
 */
object MarkerClustering {
    const val SAME_LOCATION_METERS = 20.0
    const val SAME_PLACE_METERS = 100.0

    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val HALF_TURN_DEGREES = 180.0
    private const val FULL_TURN_DEGREES = 360.0
    private const val QUARTER_TURN_DEGREES = 90.0
    private const val METERS_PER_DEGREE = EARTH_RADIUS_METERS * PI / HALF_TURN_DEGREES
    private const val CELL_DEGREES = SAME_PLACE_METERS / METERS_PER_DEGREE

    private val longitudeCells = ceil(FULL_TURN_DEGREES / CELL_DEGREES).toInt()

    /** The pins for [items], each with its items in list order. */
    fun cluster(items: List<Markerable>): List<Markerable.MarkerableCluster> {
        val pins = mutableListOf<Pin>()
        val grid = HashMap<Long, MutableList<Pin>>()
        for (item in items) {
            val pin = nearestPin(item, grid)
            if (pin != null) {
                pin.members += item
            } else {
                val newPin = Pin(item)
                pins += newPin
                grid.getOrPut(cellKey(row(item.latitude), column(item.longitude))) { mutableListOf() } += newPin
            }
        }
        return pins.map { Markerable.MarkerableCluster(it.members) }
    }

    /** The distance between two points in metres (haversine; plenty accurate at these distances). */
    internal fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, a)))
    }

    /** [degrees] as a longitude difference in -180 until 180. */
    internal fun wrapLongitude(degrees: Double): Double =
        (degrees + HALF_TURN_DEGREES).mod(FULL_TURN_DEGREES) - HALF_TURN_DEGREES

    /** The pin whose anchor is nearest to [item] among those close enough to it, if any. */
    private fun nearestPin(item: Markerable, grid: Map<Long, List<Pin>>): Pin? =
        pinsAround(item, grid)
            .map { pin -> pin to distanceMeters(item.latitude, item.longitude, pin.anchor.latitude, pin.anchor.longitude) }
            .filter { (pin, distance) -> distance < threshold(item, pin.anchor) }
            .minByOrNull { (_, distance) -> distance }
            ?.first

    /** The pins whose anchors are in [item]'s cell or close enough to it to be within [SAME_PLACE_METERS]. */
    private fun pinsAround(item: Markerable, grid: Map<Long, List<Pin>>): Sequence<Pin> {
        val row = row(item.latitude)
        val column = column(item.longitude)
        // A cell is CELL_DEGREES wide, which is fewer metres the further the item is from the equator.
        // Very close to a pole the search spans every column (toInt caps the huge span right at the pole).
        val span = ceil(1 / cos(Math.toRadians(item.latitude))).toInt()
        val columns = if (span >= longitudeCells / 2) {
            0 until longitudeCells
        } else {
            (column - span..column + span).map { it.mod(longitudeCells) }
        }
        return (row - 1..row + 1).asSequence()
            .flatMap { r -> columns.asSequence().map { c -> cellKey(r, c) } }
            .flatMap { key -> grid[key].orEmpty() }
    }

    /** How close [item] must be to [anchor] to share its pin. */
    private fun threshold(item: Markerable, anchor: Markerable): Double =
        if (isSamePlace(item, anchor)) SAME_PLACE_METERS else SAME_LOCATION_METERS

    private fun isSamePlace(a: Markerable, b: Markerable): Boolean =
        a.placeName != null && a.address != null && a.placeName == b.placeName && a.address == b.address

    private fun row(latitude: Double): Int = floor((latitude + QUARTER_TURN_DEGREES) / CELL_DEGREES).toInt()

    private fun column(longitude: Double): Int =
        floor((wrapLongitude(longitude) + HALF_TURN_DEGREES) / CELL_DEGREES).toInt().mod(longitudeCells)

    private fun cellKey(row: Int, column: Int): Long = row.toLong() * longitudeCells + column

    private class Pin(val anchor: Markerable) {
        val members = mutableListOf(anchor)
    }
}
