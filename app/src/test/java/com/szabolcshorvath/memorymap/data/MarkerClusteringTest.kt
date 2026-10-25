package com.szabolcshorvath.memorymap.data

import com.szabolcshorvath.memorymap.data.MarkerClustering.SAME_LOCATION_METERS
import com.szabolcshorvath.memorymap.data.MarkerClustering.SAME_PLACE_METERS
import com.szabolcshorvath.memorymap.data.MarkerClustering.distanceMeters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.random.Random

class MarkerClusteringTest {

    private data class TestMarker(
        override val groupId: Int,
        override val latitude: Double,
        override val longitude: Double,
        override val placeName: String? = null,
        override val address: String? = null
    ) : Markerable {
        override val title: String? = null
        override val startDate: ZonedDateTime? = null
        override val endDate: ZonedDateTime? = null
        override val isAllDay = false
        override val markerHue: Float? = null
        override val markerSaturation: Float? = null
        override val markerBrightness: Float? = null
        override fun getFormattedDate(): String? = null
    }

    private val metersPerDegreeLatitude = distanceMeters(0.0, 0.0, 1.0, 0.0)

    /** A marker [meters] north of 47.5° N, 19.05° E (Budapest). */
    private fun markerNorthBy(id: Int, meters: Double, placeName: String? = null, address: String? = null) =
        TestMarker(id, BASE_LATITUDE + meters / metersPerDegreeLatitude, BASE_LONGITUDE, placeName, address)

    private fun List<Markerable.MarkerableCluster>.groupIds() = map { cluster -> cluster.items.map { it.groupId } }

    @Test
    fun `markers closer than the threshold share a pin`() {
        val clusters = MarkerClustering.cluster(listOf(markerNorthBy(1, 0.0), markerNorthBy(2, 15.0)))
        assertEquals(listOf(listOf(1, 2)), clusters.groupIds())
    }

    @Test
    fun `markers further apart than the threshold get their own pins`() {
        val clusters = MarkerClustering.cluster(listOf(markerNorthBy(1, 0.0), markerNorthBy(2, 25.0)))
        assertEquals(listOf(listOf(1), listOf(2)), clusters.groupIds())
    }

    @Test
    fun `a chain of close markers does not merge distant ends`() {
        // Each is 15 m from the next, but the first and the third are 30 m apart.
        val markers = listOf(markerNorthBy(1, 0.0), markerNorthBy(2, 15.0), markerNorthBy(3, 30.0))
        assertEquals(listOf(listOf(1, 2), listOf(3)), MarkerClustering.cluster(markers).groupIds())
    }

    @Test
    fun `a marker joins the nearest pin it is close enough to`() {
        // 3 is 18 m from pin 1 and 7 m from pin 2.
        val markers = listOf(markerNorthBy(1, 0.0), markerNorthBy(2, 25.0), markerNorthBy(3, 18.0))
        assertEquals(listOf(listOf(1), listOf(2, 3)), MarkerClustering.cluster(markers).groupIds())
    }

    @Test
    fun `the same place merges within the larger threshold`() {
        val markers = listOf(
            markerNorthBy(1, 0.0, "Great Mall", "447 Great Mall Dr"),
            markerNorthBy(2, 80.0, "Great Mall", "447 Great Mall Dr")
        )
        assertEquals(listOf(listOf(1, 2)), MarkerClustering.cluster(markers).groupIds())
    }

    @Test
    fun `the same place name and address far apart are separate pins`() {
        val markers = listOf(
            markerNorthBy(1, 0.0, "Costco", "Main St 1"),
            markerNorthBy(2, 5_000.0, "Costco", "Main St 1")
        )
        assertEquals(listOf(listOf(1), listOf(2)), MarkerClustering.cluster(markers).groupIds())
    }

    @Test
    fun `a different place at that distance is a separate pin`() {
        val markers = listOf(
            markerNorthBy(1, 0.0, "Great Mall", "447 Great Mall Dr"),
            markerNorthBy(2, 80.0, "Costco", "Main St 1")
        )
        assertEquals(listOf(listOf(1), listOf(2)), MarkerClustering.cluster(markers).groupIds())
    }

    @Test
    fun `the pin sits in the middle of its markers`() {
        val position = MarkerClustering.cluster(listOf(markerNorthBy(1, 0.0), markerNorthBy(2, 10.0))).single().position
        assertEquals(BASE_LATITUDE + 5.0 / metersPerDegreeLatitude, position.latitude, 1e-9)
        assertEquals(BASE_LONGITUDE, position.longitude, 1e-9)
    }

    @Test
    fun `markers on both sides of the 180th meridian share a pin there`() {
        val clusters = MarkerClustering.cluster(listOf(TestMarker(1, -16.8, 179.99995), TestMarker(2, -16.8, -179.99995)))
        val position = clusters.single().position
        assertEquals(180.0, kotlin.math.abs(position.longitude), 1e-6)
    }

    @Test
    fun `close markers near the pole share a pin`() {
        val clusters = MarkerClustering.cluster(listOf(TestMarker(1, 89.9999, 0.0), TestMarker(2, 89.9999, 90.0)))
        assertEquals(1, clusters.size)
    }

    @Test
    fun `random markers match the rules checked one by one`() {
        val random = Random(42)
        val places = listOf("A" to "1 Main St", "B" to "2 Main St", null to null)
        val markers = List(2_000) { id ->
            val (name, address) = places[random.nextInt(places.size)]
            // About 1 km × 1 km, so many markers are close to each other.
            TestMarker(id, BASE_LATITUDE + random.nextDouble(0.009), BASE_LONGITUDE + random.nextDouble(0.013), name, address)
        }
        val clusters = MarkerClustering.cluster(markers)

        assertEquals(markers.map { it.groupId }.sorted(), clusters.flatMap { it.items }.map { it.groupId }.sorted())
        val anchors = clusters.map { it.items.first() }
        for (cluster in clusters) {
            val anchor = cluster.items.first()
            for (member in cluster.items.drop(1)) {
                assertTrue(member.groupId > anchor.groupId, "an anchor is the first of its markers in the list")
                assertTrue(distance(member, anchor) < threshold(member, anchor), "every marker is close to its anchor")
                // No other anchor that existed when the marker was placed is nearer and close enough.
                anchors.filter { it !== anchor && it.groupId < member.groupId }.forEach { other ->
                    val d = distance(member, other)
                    assertTrue(d >= threshold(member, other) || d >= distance(member, anchor), "joined the nearest pin")
                }
            }
        }
        // A marker starts a pin only if no earlier anchor was close enough.
        for ((i, anchor) in anchors.withIndex()) {
            anchors.take(i).filter { it.groupId < anchor.groupId }.forEach { earlier ->
                assertTrue(distance(anchor, earlier) >= threshold(anchor, earlier), "anchors are not close to earlier anchors")
            }
        }
    }

    private fun distance(a: Markerable, b: Markerable) = distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    private fun threshold(a: Markerable, b: Markerable): Double {
        val isSamePlace = a.placeName != null && a.address != null && (a.placeName to a.address) == (b.placeName to b.address)
        return if (isSamePlace) SAME_PLACE_METERS else SAME_LOCATION_METERS
    }

    private companion object {
        const val BASE_LATITUDE = 47.5
        const val BASE_LONGITUDE = 19.05
    }
}
