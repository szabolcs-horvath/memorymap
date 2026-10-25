package com.szabolcshorvath.memorymap

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.google.android.gms.maps.MapsInitializer
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MemoryMapApplication : Application(), SingletonImageLoader.Factory {

    /**
     * For short background work that must finish even if the screen that started it goes away
     * (for example the clean-up after a delete's undo window). Failures are logged, not crashed on.
     */
    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background task failed", e) }
    )

    override fun onCreate() {
        super.onCreate()
        initializeMaps()
        initializePlaces()
    }

    private fun initializeMaps() {
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) { renderer ->
            when (renderer) {
                MapsInitializer.Renderer.LATEST -> Log.d(TAG, "The latest version of the renderer is used.")

                MapsInitializer.Renderer.LEGACY -> Log.d(TAG, "The legacy version of the renderer is used.")
            }
        }
    }

    private fun initializePlaces() {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            when {
                // The manifest placeholder is empty when MAPS_API_KEY is not set; every Places request would fail.
                apiKey.isNullOrBlank() -> Log.w(TAG, "No Maps API key (MAPS_API_KEY), so the Places SDK is not initialized.")

                !Places.isInitialized() -> {
                    Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
                    Log.d(TAG, "Places SDK initialized.")
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to initialize Places SDK", e)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, IMAGE_LOADER_MEMORY_CACHE_MAX_SIZE_PERCENT)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .build()
    }

    companion object {
        const val TAG = "MemoryMapApplication"
        private const val IMAGE_LOADER_MEMORY_CACHE_MAX_SIZE_PERCENT = 0.25 // 0.25 = 25%
    }
}
