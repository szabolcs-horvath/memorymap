package com.szabolcshorvath.memorymap

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.szabolcshorvath.memorymap.databinding.ActivityMainContainerBinding

class MainActivity : AppCompatActivity(), TimelineFragment.TimelineListener, MapFragment.MapListener, AddMemoryGroupFragment.AddMemoryListener, PickLocationFragment.PickLocationListener {

    private lateinit var binding: ActivityMainContainerBinding
    
    private lateinit var mapFragment: MapFragment
    private lateinit var timelineFragment: TimelineFragment
    private lateinit var addMemoryFragment: AddMemoryGroupFragment
    
    private var activeFragment: Fragment? = null
    private var isNavigatedFromTimeline = false
    private var isNavigatedFromMap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            mapFragment = MapFragment()
            timelineFragment = TimelineFragment()
            addMemoryFragment = AddMemoryGroupFragment()
            
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mapFragment, "MAP")
                .add(R.id.fragment_container, timelineFragment, "TIMELINE")
                .add(R.id.fragment_container, addMemoryFragment, "ADD_MEMORY")
                .hide(timelineFragment)
                .hide(addMemoryFragment)
                .commit()
                
            activeFragment = mapFragment
        } else {
            mapFragment = supportFragmentManager.findFragmentByTag("MAP") as? MapFragment ?: MapFragment()
            timelineFragment = supportFragmentManager.findFragmentByTag("TIMELINE") as? TimelineFragment ?: TimelineFragment()
            addMemoryFragment = supportFragmentManager.findFragmentByTag("ADD_MEMORY") as? AddMemoryGroupFragment ?: AddMemoryGroupFragment()

            activeFragment = if (!addMemoryFragment.isHidden) {
                addMemoryFragment
            } else if (!timelineFragment.isHidden) {
                timelineFragment
            } else {
                mapFragment
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // Clear navigation history flags when manually switching tabs
            isNavigatedFromTimeline = false 
            isNavigatedFromMap = false
            
            // If we are in PickLocation (which is still a modal/child flow of Add), handle it?
            // If PickLocation is visible, we should probably pop it or handle it.
            // PickLocation is added to backstack. AddMemory is now a main section.
            
            // If we switch tabs while PickLocation is open, we should probably pop PickLocation?
            val pickLocation = supportFragmentManager.findFragmentByTag("PICK_LOCATION")
            if (pickLocation != null && pickLocation.isVisible) {
                supportFragmentManager.popBackStackImmediate()
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
                else -> false
            }
        }
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check for PickLocation first (it's a backstack item)
                val pickLocation = supportFragmentManager.findFragmentByTag("PICK_LOCATION")
                if (pickLocation != null && pickLocation.isVisible) {
                    supportFragmentManager.popBackStackImmediate()
                    // activeFragment should remain AddMemoryFragment (which is underneath)
                    // We need to ensure activeFragment variable is correct
                    activeFragment = addMemoryFragment
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
                        // If we are on Add, where do we go back to?
                        // If it's treated as a main tab, back usually exits or goes to Home (Map).
                        // Let's go to Map as default home.
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
    }

    override fun onMemorySelected(lat: Double, lng: Double, id: Int) {
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        isNavigatedFromTimeline = true
        mapFragment.focusOnMemory(lat, lng, id)
    }

    override fun onNavigateToTimeline(memoryId: Int) {
        binding.bottomNavigation.selectedItemId = R.id.navigation_timeline
        isNavigatedFromMap = true
    }

    override fun onPickLocation(currentLat: Double, currentLng: Double) {
        val fragment = PickLocationFragment() 
        // PickLocation is still a modal on top of Add
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "PICK_LOCATION")
            .hide(addMemoryFragment) // Hide Add fragment
            .addToBackStack("PICK_LOCATION")
            .commit()
        // activeFragment logic for back press needs to handle this
        // We don't necessarily update activeFragment to PickLocation if we handle it via backstack check?
        // But for consistency let's update it?
        // Actually my showFragment logic relies on activeFragment. 
        // But PickLocation is not switched to via bottom nav.
        // So let's just leave activeFragment as AddMemoryFragment?
        // Or update it?
        // If I update it, then showFragment might try to hide it?
        // PickLocation is temporary.
        // Let's NOT update activeFragment, but handle visibility in back press.
        // Actually, in `onLocationConfirmed`, we pop back.
        // But `showFragment` might be called if user taps bottom nav while PickLocation is open.
        // In that listener, I added logic to pop back stack if PickLocation is visible.
    }

    override fun onMemorySaved() {
        // Memory saved. We should probably go to Map or Timeline?
        // "The user might want to go to timeline to see it?"
        // Or just stay? 
        // Typically after save, we go to list or map.
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        mapFragment.onResume() // Refresh
        timelineFragment.refreshData()
    }

    override fun onLocationConfirmed(lat: Double, lng: Double) {
        supportFragmentManager.popBackStackImmediate()
        addMemoryFragment.updateLocation(lat, lng)
        // activeFragment is still addMemoryFragment (conceptually)
    }
}