package com.szabolcshorvath.memorymap

import android.app.Application
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.fakes.FakeLocationService
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.fakes.FakePlaceSearch

/**
 * Runs the UI tests. Before the app starts it swaps the outside services for fakes, so no test signs in to a
 * real account, touches real backups, opens the system photo picker or Google's place search, or depends on
 * where the emulator thinks it is. It also turns off Firebase collection, so test runs don't show up in
 * analytics, crash reports or performance data. Videos play with software decoders: the emulator's hardware
 * decoder fails when an app runs it (the system's own player, which runs it in the media server, plays them).
 */
class MemoryMapTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        AppServices.accounts = FakeAccountService
        AppServices.backupStorage = FakeBackupStorage
        AppServices.mediaPicker = FakeMediaPicker
        AppServices.location = FakeLocationService
        AppServices.placeSearch = FakePlaceSearch
        AppServices.videoDecoders = softwareDecoders

        FirebaseAnalytics.getInstance(app).setAnalyticsCollectionEnabled(false)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = false

        super.callApplicationOnCreate(app)
    }

    private companion object {
        val softwareDecoders = MediaCodecSelector { mimeType, secure, tunneling ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling).filterNot { it.hardwareAccelerated }
        }
    }
}
