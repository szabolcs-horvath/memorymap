package com.szabolcshorvath.memorymap

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.szabolcshorvath.memorymap.databinding.ActivityMainContainerBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment
import com.szabolcshorvath.memorymap.fragment.MapFragment
import com.szabolcshorvath.memorymap.fragment.MediaViewerFragment
import com.szabolcshorvath.memorymap.fragment.MemoryFragment
import com.szabolcshorvath.memorymap.fragment.MemoryPagerFragment
import com.szabolcshorvath.memorymap.fragment.PickLocationFragment
import com.szabolcshorvath.memorymap.fragment.TimelineFragment

class MainActivity : AppCompatActivity(), TimelineFragment.TimelineListener, MapFragment.MapListener, AddMemoryGroupFragment.AddMemoryListener, PickLocationFragment.PickLocationListener, MemoryFragment.MemoryFragmentListener {

    private lateinit var binding: ActivityMainContainerBinding
    
    private lateinit var mapFragment: MapFragment
    private lateinit var timelineFragment: TimelineFragment
    private lateinit var addMemoryFragment: AddMemoryGroupFragment
    
    private var activeFragment: Fragment? = null
    private var isNavigatedFromTimeline = false
    private var isNavigatedFromMap = false
    private var isProgrammaticSelection = false

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
            mapFragment = supportFragmentManager.findFragmentByTag("MAP") as? MapFragment
                ?: MapFragment()
            timelineFragment = supportFragmentManager.findFragmentByTag("TIMELINE") as? TimelineFragment
                ?: TimelineFragment()
            addMemoryFragment = supportFragmentManager.findFragmentByTag("ADD_MEMORY") as? AddMemoryGroupFragment
                ?: AddMemoryGroupFragment()

            activeFragment = if (!addMemoryFragment.isHidden) {
                addMemoryFragment
            } else if (!timelineFragment.isHidden) {
                timelineFragment
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
            
            // If any fragment is in backstack (PickLocation, MemoryDetails, etc), pop it when switching tabs?
            // Standard behavior usually clears backstack or keeps it per tab. 
            // Here we have a simple single backstack. Let's clear it to avoid confusion.
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
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
                // Check if we have fragments in backstack (PickLocation, MemoryDetails, MediaViewer)
                if (supportFragmentManager.backStackEntryCount > 0) {
                    // Check if PickLocation is top (special case for activeFragment?)
                    val pickLocation = supportFragmentManager.findFragmentByTag("PICK_LOCATION")
                    if (pickLocation != null && pickLocation.isVisible) {
                        // Restore activeFragment reference if needed, though usually it's still correct under the modal
                        activeFragment = addMemoryFragment
                    }
                    
                    supportFragmentManager.popBackStackImmediate()
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
        // Ensure the tab is selected
        isProgrammaticSelection = true
        binding.bottomNavigation.selectedItemId = R.id.navigation_add
        isProgrammaticSelection = false
    }

    override fun onMemoryClicked(id: Int) {
        val fragment = MemoryPagerFragment.newInstance(id)
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "MEMORY_DETAILS_PAGER")
            .addToBackStack("MEMORY_DETAILS_PAGER")
            .commit()
    }
    
    override fun onMediaClick(mediaItems: ArrayList<String>, types: ArrayList<String>, startPosition: Int) {
        val fragment = MediaViewerFragment.newInstance(mediaItems, types, startPosition)
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "MEDIA_VIEWER")
            .addToBackStack("MEDIA_VIEWER")
            .commit()
    }

    override fun onBackFromMemory() {
        supportFragmentManager.popBackStack()
    }

    override fun onNavigateToTimeline(memoryId: Int) {
        // First pop the back stack to remove the details fragment
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

        isProgrammaticSelection = true
        binding.bottomNavigation.selectedItemId = R.id.navigation_timeline
        isProgrammaticSelection = false
        
        isNavigatedFromMap = true
        
        timelineFragment.scrollToAndFlash(memoryId)
    }
    
    override fun onNavigateToMap(lat: Double, lng: Double, id: Int) {
        // First pop the back stack to remove the details fragment
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

        isProgrammaticSelection = true
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        isProgrammaticSelection = false
        
        isNavigatedFromTimeline = true
        mapFragment.focusOnMemory(lat, lng, id)
    }

    override fun onPickLocation(currentLat: Double, currentLng: Double) {
        val fragment = PickLocationFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, "PICK_LOCATION")
            .hide(addMemoryFragment) // Hide Add fragment
            .addToBackStack("PICK_LOCATION")
            .commit()
    }

    override fun onMemorySaved() {
        binding.bottomNavigation.selectedItemId = R.id.navigation_map
        mapFragment.refreshData()
        timelineFragment.refreshData()
    }

    override fun onLocationConfirmed(lat: Double, lng: Double) {
        supportFragmentManager.popBackStackImmediate()
        addMemoryFragment.updateLocation(lat, lng)
    }
}