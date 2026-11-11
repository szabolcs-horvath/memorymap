package com.szabolcshorvath.memorymap.ui.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MyLocationStepTest {

    @Test
    fun `with permission the location is used`() {
        assertEquals(MyLocationStep.USE_LOCATION, myLocationStep(hasPermission = true, shouldShowRationale = false, wasDenied = true))
    }

    @Test
    fun `the first time it explains before asking`() {
        assertEquals(MyLocationStep.EXPLAIN_THEN_ASK, myLocationStep(hasPermission = false, shouldShowRationale = false, wasDenied = false))
    }

    @Test
    fun `after one no it explains and asks again while Android still allows it`() {
        assertEquals(MyLocationStep.EXPLAIN_THEN_ASK, myLocationStep(hasPermission = false, shouldShowRationale = true, wasDenied = true))
    }

    @Test
    fun `once Android no longer asks it offers the settings`() {
        assertEquals(MyLocationStep.OFFER_SETTINGS, myLocationStep(hasPermission = false, shouldShowRationale = false, wasDenied = true))
    }
}
