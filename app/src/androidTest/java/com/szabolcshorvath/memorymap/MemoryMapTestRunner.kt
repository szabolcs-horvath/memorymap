package com.szabolcshorvath.memorymap

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage

/**
 * Runs the UI tests. Before the app starts it swaps the outside services for fakes, so no test signs in to a
 * real account or touches real backups, and it turns off Firebase collection, so test runs don't show up in
 * analytics, crash reports or performance data.
 */
class MemoryMapTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        AppServices.accounts = FakeAccountService
        AppServices.backupStorage = FakeBackupStorage

        FirebaseAnalytics.getInstance(app).setAnalyticsCollectionEnabled(false)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = false

        super.callApplicationOnCreate(app)
    }
}
