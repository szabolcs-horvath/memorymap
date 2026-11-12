package com.szabolcshorvath.memorymap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.szabolcshorvath.memorymap.ui.shell.AppShell
import com.szabolcshorvath.memorymap.ui.shell.ShellMessage
import com.szabolcshorvath.memorymap.ui.shell.ShellViewModel
import com.szabolcshorvath.memorymap.ui.shell.TabViewModels
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.MediaDetailsBackfill
import com.szabolcshorvath.memorymap.util.MediaRelinker
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PermissionUtil
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "googleAuthDatastore")

/** The app's only activity: it shows the Compose shell ([AppShell]) and runs the checks the app needs at start. */
class MainActivity : AppCompatActivity() {

    private val shell: ShellViewModel by viewModels()
    private val tabs = TabViewModels(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) shell.onActivityRecreated()

        setContent {
            MemoryMapTheme { AppShell(shell, tabs) }
        }
        lifecycleScope.launch {
            checkAppStatus()
        }
    }

    private suspend fun checkAppStatus() {
        trace("main_activity_check_app_status") {
            try {
                val currentVersion =
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode

                val prefs = dataStore.data.first()
                val isFirstRun = prefs[PreferencesKeys.IS_FIRST_RUN] ?: true
                val lastVersion = prefs[PreferencesKeys.LAST_APP_VERSION] ?: 0L

                // Nothing reads it yet: it records the version this installation last ran, for a future step that
                // must run once after an update.
                if (currentVersion > lastVersion) {
                    dataStore.edit { it[PreferencesKeys.LAST_APP_VERSION] = currentVersion }
                }

                if (isFirstRun) {
                    InstallationIdentifier.getInstallationIdentifier(applicationContext)
                    dataStore.edit { it[PreferencesKeys.IS_FIRST_RUN] = false }
                }

                val needsPermissionForRelink = withContext(Dispatchers.IO) {
                    tabs.common.addDefaultPresetsIfEmpty()
                    val relinkResult = MediaRelinker.relink(applicationContext)
                    MediaDetailsBackfill.start(applicationContext)
                    // Only prompt when there is no media access at all, so users who chose
                    // "selected photos" are not asked again on every launch.
                    !PermissionUtil.hasAnyMediaPermission(applicationContext) && relinkResult.missingCount > 0
                }

                if (needsPermissionForRelink) shell.show(ShellMessage.RelinkNeedsAccess)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check app status", e)
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
