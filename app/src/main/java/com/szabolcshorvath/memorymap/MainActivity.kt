package com.szabolcshorvath.memorymap

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.google.android.material.snackbar.Snackbar
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.ViewModel
import com.szabolcshorvath.memorymap.databinding.ActivityMainContainerBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment
import com.szabolcshorvath.memorymap.fragment.MapFragment
import com.szabolcshorvath.memorymap.fragment.MediaViewerFragment
import com.szabolcshorvath.memorymap.fragment.MemoryFragment
import com.szabolcshorvath.memorymap.fragment.MemoryPagerFragment
import com.szabolcshorvath.memorymap.fragment.PickLocationFragment
import com.szabolcshorvath.memorymap.fragment.SettingsFragment
import com.szabolcshorvath.memorymap.fragment.TimelineFragment
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "googleAuthDatastore")

class MainActivity :
    AppCompatActivity(),
    MapFragment.MapListener,
    TimelineFragment.TimelineListener,
    MemoryFragment.MemoryFragmentListener,
    AddMemoryGroupFragment.AddMemoryListener,
    PickLocationFragment.PickLocationListener {

    private lateinit var binding: ActivityMainContainerBinding
    private lateinit var viewModel: ViewModel

    private lateinit var mapFragment: MapFragment
    private lateinit var timelineFragment: TimelineFragment
    private lateinit var addMemoryFragment: AddMemoryGroupFragment
    private lateinit var pickLocationFragment: PickLocationFragment
    private lateinit var settingsFragment: SettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ViewModel::class.java]

        if (savedInstanceState == null) {
            mapFragment = MapFragment()
            timelineFragment = TimelineFragment()
            addMemoryFragment = AddMemoryGroupFragment()
            pickLocationFragment = PickLocationFragment()
            settingsFragment = SettingsFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mapFragment, MapFragment.TAG)
                .add(R.id.fragment_container, timelineFragment, TimelineFragment.TAG).hide(timelineFragment)
                .add(R.id.fragment_container, addMemoryFragment, AddMemoryGroupFragment.TAG).hide(addMemoryFragment)
                .add(R.id.fragment_container, pickLocationFragment, PickLocationFragment.TAG).hide(pickLocationFragment)
                .add(R.id.fragment_container, settingsFragment, SettingsFragment.TAG).hide(settingsFragment)
                .commit()
        } else {
            mapFragment = supportFragmentManager.findFragmentByTag(MapFragment.TAG) as? MapFragment ?: MapFragment()
            timelineFragment = supportFragmentManager.findFragmentByTag(TimelineFragment.TAG) as? TimelineFragment ?: TimelineFragment()
            addMemoryFragment = supportFragmentManager.findFragmentByTag(AddMemoryGroupFragment.TAG) as? AddMemoryGroupFragment ?: AddMemoryGroupFragment()
            pickLocationFragment = supportFragmentManager.findFragmentByTag(PickLocationFragment.TAG) as? PickLocationFragment ?: PickLocationFragment()
            settingsFragment = supportFragmentManager.findFragmentByTag(SettingsFragment.TAG) as? SettingsFragment ?: SettingsFragment()
        }

        setupBottomNavigationBar()
        setupBackPress()
        checkAppStatus()
    }

    private fun setupBottomNavigationBar() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navigateToTab(item.itemId)
            true
        }
    }

    private fun navigateToTab(itemId: Int) {
        val targetTag = when (itemId) {
            R.id.navigation_map -> MapFragment.TAG
            R.id.navigation_timeline -> TimelineFragment.TAG
            R.id.navigation_add -> AddMemoryGroupFragment.TAG
            R.id.navigation_settings -> SettingsFragment.TAG
            else -> return
        }

        // 1. If we have a backstack (Detail views), clear it first
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        val targetFragment = supportFragmentManager.findFragmentByTag(targetTag) ?: return
        val transaction = supportFragmentManager.beginTransaction()

        // 2. Hide ALL fragments that are currently added to the container
        // This ensures that even if we just popped the backstack, everything is reset
        supportFragmentManager.fragments.forEach { fragment ->
            transaction.hide(fragment)
        }

        // 3. Show the one we want
        transaction.show(targetFragment)
        transaction.commit()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // 1. Handle Detail views (added via .addToBackStack)
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                        return
                    }

                    // 2. Handle Tab navigation
                    val currentTabId = binding.bottomNavigation.selectedItemId
                    if (currentTabId != R.id.navigation_map) {
                        // If not on Home tab, always go back to Home tab
                        binding.bottomNavigation.selectedItemId = R.id.navigation_map
                    } else {
                        // 3. We are on the Map tab: Exit the app
                        isEnabled = false // Disable this callback
                        onBackPressedDispatcher.onBackPressed() // Pass back to system
                        isEnabled = true // Re-enable for next time
                    }
                }
            }
        )
    }

    private fun checkAppStatus() {
        lifecycleScope.launch {
            try {
                val currentVersion =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageManager.getPackageInfo(packageName, 0).longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                    }

                val prefs = dataStore.data.first()
                val isFirstRun = prefs[IS_FIRST_RUN] ?: true
                val lastVersion = prefs[LAST_APP_VERSION] ?: 0L

                if (currentVersion > lastVersion) {
                    launch {
                        LocalMediaUtil.verifyAndFixMediaItems(applicationContext, viewModel.getMemoryGroupDao())
                    }
                    dataStore.edit { it[LAST_APP_VERSION] = currentVersion }
                }

                if (isFirstRun) {
                    InstallationIdentifier.getInstallationIdentifier(applicationContext)
                    dataStore.edit { it[LAST_APP_VERSION] = currentVersion }
                    dataStore.edit { it[IS_FIRST_RUN] = false }
                }
                withContext(Dispatchers.IO) {
                    initializeHSVPresetsIfEmpty()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check app status", e)
            }
        }
    }

    private suspend fun initializeHSVPresetsIfEmpty() {
        if (viewModel.getHSVPresetDao().getCount() == 0) {
            val defaultPresets = ColorUtil.DEFAULT_HSV_PRESETS.mapIndexed { index, hsv ->
                HSVPreset(hue = hsv[0], saturation = hsv[1], brightness = hsv[2], order = index)
            }
            viewModel.getHSVPresetDao().insertPresets(defaultPresets)
        }
    }

    override fun startAddMemoryFlow(lat: Double, lng: Double) {
        addMemoryFragment.clearFields()
        addMemoryFragment.updateLocation(lat, lng)

        // Just trigger the UI selection; the logic follows automatically
        binding.bottomNavigation.selectedItemId = R.id.navigation_add
    }

    override fun onMemoryClicked(id: Int) {
        val fragment = MemoryPagerFragment.newInstance(id)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment) // Replace the current tab
            .addToBackStack(null) // This is the ONLY place you use backstack
            .commit()
    }

    override fun onMediaClick(mediaItems: ArrayList<Pair<String, String>>, startPosition: Int) {
        val fragment = MediaViewerFragment.newInstance(mediaItems, startPosition)
        val memoryPagerFragment = supportFragmentManager.findFragmentByTag(MemoryPagerFragment.TAG)

        val transaction = supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, MediaViewerFragment.TAG)
            .addToBackStack(MediaViewerFragment.TAG)

        if (memoryPagerFragment != null && memoryPagerFragment.isVisible) {
            transaction.hide(memoryPagerFragment)
        }

        transaction.commit()
    }

    override fun onBackFromMemory() {
        supportFragmentManager.popBackStack()
    }

    override fun onNavigateToTimeline(memoryId: Int) {
        // 1. If we have a backstack (Detail views), clear it first
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        // This triggers navigateToTab automatically via the listener
        binding.bottomNavigation.selectedItemId = R.id.navigation_timeline

        binding.root.post {
            timelineFragment.scrollToAndFlash(memoryId)
        }
    }

    override fun onNavigateToMap(lat: Double, lng: Double, id: Int) {
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        lifecycleScope.launch {
            mapFragment.focusOnMemory(lat, lng, id)
        }
    }

    override fun onPickLocation(lat: Double, lng: Double) {
        pickLocationFragment.clearSelection()
        addMemoryFragment.updateLocation(lat, lng)

        supportFragmentManager.beginTransaction()
            .hide(addMemoryFragment) // Specifically hide the caller
            .show(pickLocationFragment)
            .addToBackStack(PickLocationFragment.TAG) // Use the TAG here
            .commit()
    }

    override fun onMemorySaved(lat: Double, lng: Double, id: Int, startDate: LocalDate, endDate: LocalDate) {
        binding.bottomNavigation.selectedItemId = R.id.navigation_map

        lifecycleScope.launch {
            mapFragment.updateDateFilterForMemory(startDate, endDate)
            mapFragment.focusOnMemory(lat, lng, id)
        }
    }

    override fun onLocationConfirmed(lat: Double, lng: Double, placeName: String?, address: String?) {
        // Simply pop the backstack to return to the AddMemoryFragment
        supportFragmentManager.popBackStack()
        addMemoryFragment.updateLocation(lat, lng, placeName, address)
    }

    override fun onMemoryDeleted(memoryGroup: MemoryGroup, mediaItems: List<MediaItem>) {
        val snackbar = Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.bottomNavigation
        snackbar.setAction("Undo") {
            lifecycleScope.launch(Dispatchers.IO) {
                val dao = viewModel.getMemoryGroupDao()
                viewModel.getDb().withTransaction {
                    val newGroupId = dao.insertGroup(memoryGroup)
                    val restoredMediaItems = mediaItems.map { it.copy(id = 0, groupId = newGroupId.toInt()) }
                    dao.insertMediaItems(restoredMediaItems)
                }

                // Trigger automatic backup after undo
                BackupManager(applicationContext).triggerAutomaticBackup(viewModel.getDb())
            }
        }
        snackbar.show()
    }

    override fun onEditMemory(memoryId: Int) {
        // 1. Clear any detail views (like the Pager) to return to the tab level
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        // 2. Switch to the Add tab (this triggers navigateToTab)
        binding.bottomNavigation.selectedItemId = R.id.navigation_add

        // 3. Set the mode
        addMemoryFragment.setEditMode(memoryId)
    }

    companion object {
        const val TAG = "MainActivity"
        private val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
        private val LAST_APP_VERSION = longPreferencesKey("last_app_version")
        val SHOW_FRAGMENT_MARKERS = booleanPreferencesKey("show_fragment_markers")
        val DEFAULT_DATE_FILTER = stringPreferencesKey("default_date_filter")
    }
}
