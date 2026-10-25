package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.util.MyLocationAction.Companion.nextStep
import com.szabolcshorvath.memorymap.util.MyLocationAction.Step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MyLocationActionTest {

    @Test
    fun `with permission the location is used`() {
        assertEquals(Step.USE_LOCATION, nextStep(hasPermission = true, shouldShowRationale = false, wasDenied = true))
    }

    @Test
    fun `the first time it explains before asking`() {
        assertEquals(Step.EXPLAIN_THEN_ASK, nextStep(hasPermission = false, shouldShowRationale = false, wasDenied = false))
    }

    @Test
    fun `after one no it explains and asks again while Android still allows it`() {
        assertEquals(Step.EXPLAIN_THEN_ASK, nextStep(hasPermission = false, shouldShowRationale = true, wasDenied = true))
    }

    @Test
    fun `once Android no longer asks it offers the settings`() {
        assertEquals(Step.OFFER_SETTINGS, nextStep(hasPermission = false, shouldShowRationale = false, wasDenied = true))
    }
}
