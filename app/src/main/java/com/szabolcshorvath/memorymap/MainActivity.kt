package com.szabolcshorvath.memorymap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.perf.metrics.Trace
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
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
import com.szabolcshorvath.memorymap.util.MediaDetailsBackfill
import com.szabolcshorvath.memorymap.util.MediaRelinker
import com.szabolcshorvath.memorymap.util.PerfUtil
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PermissionUtil
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "googleAuthDatastore")

class MainActivity :
    AppCompatActivity(),
    MapFragment.MapListener,
    TimelineFragment.TimelineListener,
    MemoryFragment.MemoryFragmentListener,
    AddMemoryGroupFragment.AddMemoryGroupListener,
    PickLocationFragment.PickLocationListener {

    private lateinit var binding: ActivityMainContainerBinding
    private lateinit var commonViewModel: CommonViewModel

    // Looked up by tag each time: after the activity is recreated, the FragmentManager restores these instances.
    private val mapFragment get() = supportFragmentManager.findFragmentByTag(MapFragment.TAG) as? MapFragment
    private val timelineFragment get() = supportFragmentManager.findFragmentByTag(TimelineFragment.TAG) as? TimelineFragment
    private val addMemoryFragment get() = supportFragmentManager.findFragmentByTag(AddMemoryGroupFragment.TAG) as? AddMemoryGroupFragment
    private val pickLocationFragment get() = supportFragmentManager.findFragmentByTag(PickLocationFragment.TAG) as? PickLocationFragment

    private var fragmentNavigationTrace: Trace? = null
    private var pendingTraceTag: String? = null

    private val mediaPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Partial ("selected photos") access is enough to re-link the photos the user selected.
            if (PermissionUtil.hasAnyMediaPermission(applicationContext)) {
                lifecycleScope.launch { MediaRelinker.relink(applicationContext) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        commonViewModel = ViewModelProvider(this)[CommonViewModel::class.java]

        // After a recreation the FragmentManager has already restored the fragments.
        if (savedInstanceState == null) {
            val mapFragment = MapFragment()
            val timelineFragment = TimelineFragment()
            val addMemoryFragment = AddMemoryGroupFragment()
            val pickLocationFragment = PickLocationFragment()
            val settingsFragment = SettingsFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, mapFragment, MapFragment.TAG)
                .setMaxLifecycle(mapFragment, Lifecycle.State.RESUMED)
                .add(R.id.fragmentContainer, timelineFragment, TimelineFragment.TAG).hide(timelineFragment)
                .setMaxLifecycle(timelineFragment, Lifecycle.State.CREATED)
                .add(R.id.fragmentContainer, addMemoryFragment, AddMemoryGroupFragment.TAG).hide(addMemoryFragment)
                .setMaxLifecycle(addMemoryFragment, Lifecycle.State.CREATED)
                .add(R.id.fragmentContainer, pickLocationFragment, PickLocationFragment.TAG).hide(pickLocationFragment)
                .setMaxLifecycle(pickLocationFragment, Lifecycle.State.CREATED)
                .add(R.id.fragmentContainer, settingsFragment, SettingsFragment.TAG).hide(settingsFragment)
                .setMaxLifecycle(settingsFragment, Lifecycle.State.CREATED)
                .commit()
        } else {
            releaseHiddenViews()
        }

        setupBottomNavigationBar()
        setupBackPress()
        setupNavigationTracing()
        lifecycleScope.launch {
            checkAppStatus()
        }
    }

    /**
     * After the activity is recreated (rotation, process death), the FragmentManager would build the view of every
     * hidden screen that kept one. Only the shown screen gets one now; the others build theirs when shown again.
     */
    private fun releaseHiddenViews() {
        val hidden = supportFragmentManager.fragments.filter { it.isHidden && it.id == R.id.fragmentContainer }
        if (hidden.isEmpty()) return
        val transaction = supportFragmentManager.beginTransaction()
        hidden.forEach { transaction.setMaxLifecycle(it, Lifecycle.State.CREATED) }
        transaction.commitNow()
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

        pendingTraceTag = targetTag
        fragmentNavigationTrace = PerfUtil.startTrace("main_activity_navigate_to_tab_$targetTag")

        if (targetTag != AddMemoryGroupFragment.TAG) addMemoryFragment?.closeUnchangedEdit()
        hideKeyboard()

        // Close the detail screens (a memory, the media viewer, the location picker) first.
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        val targetFragment = supportFragmentManager.findFragmentByTag(targetTag) ?: return
        val transaction = supportFragmentManager.beginTransaction()
        TAB_TAGS.filter { it != targetTag }.mapNotNull { supportFragmentManager.findFragmentByTag(it) }.forEach { tab ->
            // A tab that has a view keeps it, paused, so it comes back as it was left. One never shown stays without one.
            transaction.hide(tab)
            transaction.setMaxLifecycle(tab, if (tab.view != null) Lifecycle.State.STARTED else Lifecycle.State.CREATED)
        }
        pickLocationFragment?.let { transaction.hide(it).setMaxLifecycle(it, Lifecycle.State.CREATED) }
        transaction.show(targetFragment)
        transaction.setMaxLifecycle(targetFragment, Lifecycle.State.RESUMED)
        transaction.commit()
    }

    /** Closes the keyboard: a hidden screen keeps its view now, and with it a focused text field. */
    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        WindowCompat.getInsetsController(window, focused).hide(WindowInsetsCompat.Type.ime())
        focused.clearFocus()
    }

    /**
     * Switches to tab [itemId]. To make the tab do something, give its fragment a request it keeps in its ViewModel and
     * handles once it has a view (for example `MapFragment.requestFocusOnMemory`); its view may not exist yet here.
     */
    private fun selectTab(itemId: Int) {
        val isAlreadySelected = binding.bottomNavigation.selectedItemId == itemId
        binding.bottomNavigation.selectedItemId = itemId

        // If the selection didn't change, the listener wasn't triggered,
        // so we manually call navigateToTab to ensure backstack is cleared and fragment is shown.
        if (isAlreadySelected) {
            navigateToTab(itemId)
        }
    }

    private fun setupNavigationTracing() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    val tag = f.tag
                    val view = f.view

                    // Only measure if this fragment matches the one we just triggered navigation for
                    if (tag != null && tag == pendingTraceTag && view != null) {
                        // The View exists, but isn't rendered yet.
                        // We wait for the PreDraw pass which happens right before pixels are sent to the GPU.
                        view.doOnPreDraw {
                            fragmentNavigationTrace?.stop()
                            fragmentNavigationTrace = null
                            pendingTraceTag = null
                            Log.d(TAG, "Fragment navigation trace stopped for $tag")
                        }
                    }
                }
            },
            false
        )
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
                        selectTab(R.id.navigation_map)
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
                    initializeHSVPresetsIfEmpty()
                    val relinkResult = MediaRelinker.relink(applicationContext)
                    MediaDetailsBackfill.start(applicationContext)
                    // Only prompt when there is no media access at all, so users who chose
                    // "selected photos" are not asked again on every launch.
                    !PermissionUtil.hasAnyMediaPermission(applicationContext) && relinkResult.missingCount > 0
                }

                if (needsPermissionForRelink) {
                    withContext(Dispatchers.Main) {
                        val snackbar = Snackbar.make(
                            binding.root,
                            "Some photos need permission to re-link to your gallery",
                            Snackbar.LENGTH_LONG
                        )
                        snackbar.anchorView = binding.bottomNavigation
                        snackbar.setAction("Grant") {
                            mediaPermissionLauncher.launch(PermissionUtil.getRequiredMediaPermissions())
                        }
                        snackbar.show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check app status", e)
            }
        }
    }

    private suspend fun initializeHSVPresetsIfEmpty() {
        if (commonViewModel.getHSVPresetDao().getCount() == 0) {
            val defaultPresets = ColorUtil.DEFAULT_HSV_PRESETS.mapIndexed { index, hsv ->
                HSVPreset(hue = hsv[0], saturation = hsv[1], brightness = hsv[2], order = index)
            }
            commonViewModel.getHSVPresetDao().insertPresets(defaultPresets)
        }
    }

    override fun onMemoryClicked(id: Int) {
        hideKeyboard()
        val fragment = MemoryPagerFragment.newInstance(id)
        val transaction = supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, fragment, MemoryPagerFragment.TAG)
            .setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
        // The screen below keeps its view, paused, so Back returns to it as it was (camera, scroll, search).
        supportFragmentManager.fragments
            .filter { it.id == R.id.fragmentContainer && !it.isHidden }
            .forEach { transaction.hide(it).setMaxLifecycle(it, Lifecycle.State.STARTED) }
        transaction.addToBackStack(null).commit()
    }

    override fun onMediaClick(memoryId: Int, mediaItemId: Int) {
        val fragment = MediaViewerFragment.newInstance(memoryId, mediaItemId)
        val memoryPagerFragment = supportFragmentManager.findFragmentByTag(MemoryPagerFragment.TAG)

        val transaction = supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, fragment, MediaViewerFragment.TAG)
            .setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            .addToBackStack(MediaViewerFragment.TAG)

        if (memoryPagerFragment != null && memoryPagerFragment.isVisible) {
            transaction.hide(memoryPagerFragment)
            transaction.setMaxLifecycle(memoryPagerFragment, Lifecycle.State.STARTED)
        }

        transaction.commit()
    }

    override fun onBackFromMemory() {
        supportFragmentManager.popBackStack()
    }

    override fun onNavigateToTimeline(memoryId: Int) {
        selectTab(R.id.navigation_timeline)
        timelineFragment?.requestScrollToAndFlash(memoryId)
    }

    override fun onNavigateToMap(lat: Double, lng: Double, id: Int) {
        selectTab(R.id.navigation_map)
        mapFragment?.requestFocusOnMemory(lat, lng, id)
    }

    override fun onPickLocation(lat: Double?, lng: Double?, placeName: String?, address: String?) {
        val picker = pickLocationFragment ?: return
        val form = addMemoryFragment ?: return
        if (lat == null || lng == null) {
            picker.clearSelection()
        } else {
            picker.setInitialLocation(lat, lng, placeName, address)
        }

        supportFragmentManager.beginTransaction()
            .hide(form) // Keeps its view, paused, for when the picker closes
            .setMaxLifecycle(form, Lifecycle.State.STARTED)
            .show(picker)
            .setMaxLifecycle(picker, Lifecycle.State.RESUMED)
            .addToBackStack(PickLocationFragment.TAG) // Use the TAG here
            .commit()
    }

    override fun onMemorySaved(id: Int) {
        onMemoryClicked(id)
    }

    override fun onLocationConfirmed(lat: Double, lng: Double, placeName: String?, address: String?) {
        // Simply pop the backstack to return to the AddMemoryFragment
        supportFragmentManager.popBackStack()
        addMemoryFragment?.updateLocation(lat, lng, placeName, address)
    }

    override fun onMemoryDeleted(deleted: MemoryGroupWithMedia) {
        // Application scope: this work must finish even if the activity is destroyed meanwhile
        // (which also dismisses the Snackbar).
        val app = application as MemoryMapApplication
        val db = commonViewModel.getDb()
        val snackbar = Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.bottomNavigation
        snackbar.setAction("Undo") {
            app.applicationScope.launch {
                db.memoryGroupDao().restoreDeletedGroup(deleted)
                AutomaticBackups.schedule(app)
            }
        }
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (event == DISMISS_EVENT_ACTION) return // Undone; the undo above backs up.
                // The delete is final now: the media grants can't be needed for an undo any more. An edit of the
                // memory still open on the Add tab saves it again as a new memory, so its media keep their grants.
                val keptByForm = addMemoryFragment?.mediaUrisInForm().orEmpty()
                val released = deleted.mediaItems.map { it.uri }.filterNot { it in keptByForm }
                app.applicationScope.launch {
                    LocalMediaUtil.releaseUriGrantsIfUnused(app, db.memoryGroupDao(), released)
                    AutomaticBackups.schedule(app)
                }
            }
        })
        snackbar.show()
    }

    override fun onEditMemory(memoryId: Int) {
        selectTab(R.id.navigation_add)
        addMemoryFragment?.requestEditMode(memoryId)
    }

    companion object {
        const val TAG = "MainActivity"

        private val TAB_TAGS = listOf(MapFragment.TAG, TimelineFragment.TAG, AddMemoryGroupFragment.TAG, SettingsFragment.TAG)
    }
}
