package com.szabolcshorvath.memorymap

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.maps.MapsInitializer
import com.google.android.libraries.places.api.Places

class MemoryMapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeMaps()
        initializePlaces()
    }

    private fun initializeMaps() {
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) { renderer ->
            when (renderer) {
                MapsInitializer.Renderer.LATEST -> Log.d(
                    TAG,
                    "The latest version of the renderer is used."
                )

                MapsInitializer.Renderer.LEGACY -> Log.d(
                    TAG,
                    "The legacy version of the renderer is used."
                )
            }
        }
    }

    private fun initializePlaces() {
        try {
            val appInfo =
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")
            if (apiKey != null && !Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
                Log.d(TAG, "Places SDK initialized.")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to initialize Places SDK", e)
        }
    }

    companion object {
        const val TAG = "MemoryMapApplication"
    }
}
