package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** [cameraTargetFor]: where the camera goes so a place shows a given distance above the map's centre. */
class CameraTargetTest {
    @Test
    fun withNothingToClearTheCameraGoesToThePlace() {
        assertEquals(BUDAPEST, BUDAPEST.cameraTargetFor(0.dp, ZOOM))
    }

    @Test
    fun theCameraGoesThatFarSouthOnTheScreen() {
        val target = BUDAPEST.cameraTargetFor(UP.dp, ZOOM)

        assertEquals(BUDAPEST.longitude, target.longitude, 0.0)
        assertTrue(target.latitude < BUDAPEST.latitude, "The camera should be south of the place")
        assertEquals(UP, screenY(target, ZOOM) - screenY(BUDAPEST, ZOOM), PRECISION)
    }

    @Test
    fun theSameDistanceIsLessOfTheWorldWhenZoomedIn() {
        val zoomedOut = BUDAPEST.latitude - BUDAPEST.cameraTargetFor(UP.dp, ZOOM - 4).latitude
        val zoomedIn = BUDAPEST.latitude - BUDAPEST.cameraTargetFor(UP.dp, ZOOM).latitude

        assertTrue(zoomedIn < zoomedOut / 10, "$zoomedIn degrees zoomed in, $zoomedOut zoomed out")
    }

    /** How far down the world a place is, in dp at [zoom], on the map's Web Mercator projection. */
    private fun screenY(place: LatLng, zoom: Float): Double {
        val world = 256 * 2.0.pow(zoom.toDouble())
        val mercator = ln(tan(PI / 4 + Math.toRadians(place.latitude) / 2))
        return (1 - mercator / PI) / 2 * world
    }

    private companion object {
        val BUDAPEST = LatLng(47.4979, 19.0402)
        const val ZOOM = 15f
        const val UP = 120.0
        const val PRECISION = 1e-6
    }
}
