package com.szabolcshorvath.memorymap.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.MapsInitializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The phone the tests run on draws maps with Google's latest map renderer, as users' phones do. Google Play services
 * downloads that renderer only on a phone set up with Google Play; without it the map falls back to the legacy
 * renderer, which doesn't support the app's markers and sometimes draws nothing, so the map tests would fail for
 * reasons that have nothing to do with the app. If this fails, set up the test emulator as docs/ui-tests.md says.
 */
@RunWith(AndroidJUnit4::class)
class MapRendererTest {
    @Test
    fun mapsUseTheLatestRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renderer = CompletableFuture<MapsInitializer.Renderer>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) { renderer.complete(it) }
        }
        assertEquals(
            "The map renderer (sign in to Google Play on this emulator and wait for the download: docs/ui-tests.md)",
            MapsInitializer.Renderer.LATEST,
            renderer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
