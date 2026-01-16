package com.szabolcshorvath.memorymap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.places.api.Places
import com.google.android.material.snackbar.Snackbar
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.ActivityMainContainerBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment
import com.szabolcshorvath.memorymap.fragment.MapFragment
import com.szabolcshorvath.memorymap.fragment.MediaViewerFragment
import com.szabolcshorvath.memorymap.fragment.MemoryFragment
import com.szabolcshorvath.memorymap.fragment.MemoryPagerFragment
import com.szabolcshorvath.memorymap.fragment.PickLocationFragment
import com.szabolcshorvath.memorymap.fragment.SettingsFragment
import com.szabolcshorvath.memorymap.fragment.TimelineFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "googleAuthDatastore")

class MainActivity : AppCompatActivity(), TimelineFragment.TimelineListener,
    MapFragment.MapListener, AddMemoryGroupFragment.AddMemoryListener,
    PickLocationFragment.PickLocationListener, MemoryFragment.MemoryFragmentListener {

    private lateinit var binding: ActivityMainContainerBinding

    private lateinit var mapFragment: MapFragment
    private lateinit var timelineFragment: TimelineFragment
    private lateinit var addMemoryFragment: AddMemoryGroupFragment
    private lateinit var pickLocationFragment: PickLocationFragment
    private lateinit var settingsFragment: SettingsFragment

    private var activeFragment: Fragment? = null
    private var isNavigatedFromTimeline = false
    private var isNavigatedFromMap = false
    private var isProgrammaticSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Places
        try {
            val appInfo =
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")
            if (apiKey != null) {
                Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            mapFragment = MapFragment()
            timelineFragment = TimelineFragment()
            addMemoryFragment = AddMemoryGroupFragment()
            pickLocationFragment = PickLocationFragment()
            settingsFragment = SettingsFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mapFragment, MapFragment.TAG)
                .add(R.id.fragment_container, timelineFragment, TimelineFragment.TAG)
                .add(R.id.fragment_container, addMemoryFragment, AddMemoryGroupFragment.TAG)
                .add(R.id.fragment_container, pickLocationFragment, PickLocationFragment.TAG)
                .add(R.id.fragment_container, settingsFragment, SettingsFragment.TAG)
                .hide(timelineFragment)
                .hide(addMemoryFragment)
                .hide(pickLocationFragment)
                .hide(settingsFragment)
                .commit()

            activeFragment = mapFragment
        } else {
            mapFragment = supportFragmentManager.findFragmentByTag(MapFragment.TAG) as? MapFragment
                ?: MapFragment()
            timelineFragment =
                supportFragmentManager.findFragmentByTag(TimelineFragment.TAG) as? TimelineFragment
                    ?: TimelineFragment()
            addMemoryFragment =
                supportFragmentManager.findFragmentByTag(AddMemoryGroupFragment.TAG) as? AddMemoryGroupFragment
                    ?: AddMemoryGroupFragment()
            pickLocationFragment =
                supportFragmentManager.findFragmentByTag(PickLocationFragment.TAG) as? PickLocationFragment
                    ?: PickLocationFragment()
            settingsFragment =
                supportFragmentManager.findFragmentByTag(SettingsFragment.TAG) as? SettingsFragment
                    ?: SettingsFragment()

            activeFragment = if (!addMemoryFragment.isHidden) {
                addMemoryFragment
            } else if (!timelineFragment.isHidden) {
                timelineFragment
            } else if (!settingsFragment.isHidden) {
                settingsFragment
            } else {
                mapFragment
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // Clear navigation history flags when manually switching tabs, but not when we switch programmatically
            if (!isProgrammaticSelection) {
                isNavigatedFromTimeline = false
                isNavigatedFromMap = false
            }

            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(
                    null,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }

            when (item.itemId) {
                R.id.navigation_map -> {
                    showFragment(mapFragment)
                    true
                }

                R.id.navigation_timeline -> {
                    showFragment(timelineFragment)
                    true
                }

                R.id.navigation_add -> {
                    showFragment(addMemoryFragment)
                    true
                }

                R.id.navigation_settings -> {
                    showFragment(settingsFragment)
                    true
                }

                else -> false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                when (activeFragment) {
                    mapFragment -> {
                        if (isNavigatedFromTimeline) {
                            binding.bottomNavigation.selectedItemId = R.id.navigation_timeline
                            isNavigatedFromTimeline = false
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }

                    timelineFragment -> {
                        if (isNavigatedFromMap) {
                            binding.bottomNavigation.selectedItemId = R.id.navigation_map
                            isNavigatedFromMap = false
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }

                    addMemoryFragment -> {
                        binding.bottomNavigation.selectedItemId = R.id.navigation_map
                    }

                    pickLocationFragment -> {
                        binding.bottomNavigation.selectedItemId = R.id.navigation_add
                    }

                    settingsFragment -> {
                        binding.bottomNavigation.selectedItemId = R.id.navigation_map
                    }

                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun showFragment(fragment: Fragment) {
        if (fragment != activeFragment) {
            supportFragmentManager.beginTransaction()
                .hide(activeFragment!!)
                .show(fragment)
                .commit()
            activeFragment = fragment
        }
    }

    override fun startAddMemoryFlow(lat: Double, lng: Double) {
        addMemoryFragment.clearFields()
        showFragment(addMemoryFragment)
        addMemoryFragment.updateLocation(lat, lng)
        // Ensure the tab is selected
        isProgrammaticSelection = true
        binding.bottomNavigation.selectedItemId = R.id.navigation_add
        isProgrammaticSelection = false
    }

    override fun onMemoryClicked(id: Int) {
        val fragment = MemoryPagerFragment.newInstance(id)
        val transaction = supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, MemoryPagerFragment.TAG)
            .addToBackStack(MemoryPagerFragment.TAG)
        activeFragment?.let { transaction.hide(it) }
        transaction.commit()
    }

    override fun onMediaClick(
        mediaItems: ArrayList<String>,
        types: ArrayList<String>,
        startPosition: Int
    ) {
        val fragment = MediaViewerFragment.newInstance(mediaItems, types, startPosition)
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
        // First pop the back stack to remove the details fragment
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        isProgrammaticSelection = true
        binding.bottomNavigation.selectedItemId = R.id.navigation_timeline
        isProgrammaticSelection = false

        isNavigatedFromMap = true

        timelineFragment.scrollToAndFlash(memoryId)
    }

    override fun onNavigateToMap(lat: Double, lng: Double, id: Int) {
        // First pop the back stack to remove the details fragment
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        isProgrammaticSelection = true
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        isProgrammaticSelection = false

        isNavigatedFromTimeline = true
        mapFragment.focusOnMemory(lat, lng, id)
    }

    override fun onPickLocation(lat: Double, lng: Double) {
        pickLocationFragment.clearSelection()
        addMemoryFragment.updateLocation(lat, lng)
        showFragment(pickLocationFragment)
    }

    override fun onMemorySaved(
        lat: Double,
        lng: Double,
        id: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        binding.bottomNavigation.selectedItemId = R.id.navigation_map

        mapFragment.refreshData()
        timelineFragment.refreshData()

        mapFragment.setDateFilter(startDate, endDate)
        mapFragment.focusOnMemory(lat, lng, id)
    }

    override fun onLocationConfirmed(
        lat: Double,
        lng: Double,
        placeName: String?,
        address: String?
    ) {
        showFragment(addMemoryFragment)
        addMemoryFragment.updateLocation(lat, lng, placeName, address)
    }

    override fun onMemoryDeleted(memoryGroup: MemoryGroup, mediaItems: List<MediaItem>) {
        mapFragment.refreshData()
        timelineFragment.refreshData()

        val snackbar = Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.bottomNavigation
        snackbar.setAction("Undo") {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = StoryMapDatabase.getDatabase(applicationContext)
                val newGroupId = db.memoryGroupDao().insertGroup(memoryGroup)
                val restoredMediaItems =
                    mediaItems.map { it.copy(id = 0, groupId = newGroupId.toInt()) }
                db.memoryGroupDao().insertMediaItems(restoredMediaItems)

                // Trigger automatic backup after undo
                BackupManager(applicationContext).triggerAutomaticBackup()

                withContext(Dispatchers.Main) {
                    mapFragment.refreshData()
                    timelineFragment.refreshData()
                }
            }
        }
        snackbar.show()
    }
}
