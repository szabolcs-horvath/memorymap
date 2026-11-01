package com.szabolcshorvath.memorymap.util

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.PermissionUtil.hasLocationPermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What a "my location" button does, shared by the map and the location picker: finds the user's location and passes
 * it to [onLocation]. Location access is asked for only here, when the user taps the button, never on its own: first
 * a short explanation, then the system dialog. Once the user has said no and Android no longer shows its dialog, it
 * offers the app's settings instead (whether they said no is kept in DataStore, so it survives restarts).
 *
 * Create it as a property initializer of the fragment: it registers an activity result launcher.
 */
class MyLocationAction(private val fragment: Fragment, private val onLocation: (LatLng) -> Unit) {

    /** What to do when the button is tapped. */
    enum class Step { USE_LOCATION, EXPLAIN_THEN_ASK, OFFER_SETTINGS }

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Approximate location alone is enough.
        val isGranted = results.values.any { it }
        rememberDenial(!isGranted)
        if (isGranted) fetchLocation()
    }

    init {
        // The questions are DialogFragments, so they come back after a rotation; their answers need the fragment created.
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                ConfirmDialog.listen(fragment, EXPLAIN_REQUEST) { permissionLauncher.launch(LOCATION_PERMISSIONS) }
                ConfirmDialog.listen(fragment, SETTINGS_REQUEST) { openAppSettings() }
            }
        })
    }

    /** Called when the user taps the button. */
    fun start() {
        val context = fragment.context ?: return
        fragment.lifecycleScope.launch {
            val wasDenied = context.dataStore.data.first()[PreferencesKeys.LOCATION_PERMISSION_DENIED] ?: false
            val shouldShowRationale = LOCATION_PERMISSIONS.any { fragment.shouldShowRequestPermissionRationale(it) }
            when (nextStep(hasLocationPermission(context), shouldShowRationale, wasDenied)) {
                Step.USE_LOCATION -> {
                    if (wasDenied) rememberDenial(false) // Allowed in Settings since: Android may ask again if it is revoked.
                    fetchLocation()
                }
                Step.EXPLAIN_THEN_ASK -> ask(
                    EXPLAIN_REQUEST,
                    R.string.location_rationale_title,
                    R.string.location_rationale_message,
                    R.string.location_rationale_continue
                )
                Step.OFFER_SETTINGS -> ask(
                    SETTINGS_REQUEST,
                    R.string.location_settings_title,
                    R.string.location_settings_message,
                    R.string.open_settings
                )
            }
        }
    }

    /** Asks question [requestKey]; the answer goes to the listener registered in `init`. */
    private fun ask(requestKey: String, @StringRes title: Int, @StringRes message: Int, @StringRes confirm: Int) {
        if (!fragment.isAdded || fragment.isStateSaved) return
        ConfirmDialog.show(
            fragment.childFragmentManager,
            requestKey,
            title = fragment.getString(title),
            message = fragment.getString(message),
            confirm = fragment.getString(confirm),
            cancel = fragment.getString(R.string.not_now)
        )
    }

    private fun openAppSettings() {
        val context = fragment.context ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent)
    }

    private fun rememberDenial(isDenied: Boolean) {
        val context = fragment.context?.applicationContext ?: return
        fragment.lifecycleScope.launch {
            context.dataStore.edit { it[PreferencesKeys.LOCATION_PERMISSION_DENIED] = isDenied }
        }
    }

    /** Finds where the phone is and passes it on. Only called with location permission. */
    private fun fetchLocation() {
        val context = fragment.context ?: return
        AppServices.location.currentLocation(context)
            .addOnCompleteListener { task ->
                if (fragment.viewLifecycleOwnerLiveData.value == null) return@addOnCompleteListener
                // Null when the phone's location is turned off or there is no fix yet.
                val location = if (task.isSuccessful) task.result else null
                if (location != null) {
                    onLocation(LatLng(location.latitude, location.longitude))
                } else {
                    Toast.makeText(context, R.string.location_unavailable, Toast.LENGTH_LONG).show()
                }
            }
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        private const val EXPLAIN_REQUEST = "my_location_explain"
        private const val SETTINGS_REQUEST = "my_location_settings"

        /**
         * With permission, use the location. Otherwise explain and ask, unless the user said no before and Android
         * would no longer show its dialog (it shows a rationale hint only while asking again is still possible).
         */
        internal fun nextStep(hasPermission: Boolean, shouldShowRationale: Boolean, wasDenied: Boolean): Step = when {
            hasPermission -> Step.USE_LOCATION
            shouldShowRationale || !wasDenied -> Step.EXPLAIN_THEN_ASK
            else -> Step.OFFER_SETTINGS
        }
    }
}
