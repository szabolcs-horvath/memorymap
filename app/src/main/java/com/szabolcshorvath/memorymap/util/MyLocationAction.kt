package com.szabolcshorvath.memorymap.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
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
                Step.EXPLAIN_THEN_ASK -> explainThenAsk(context)
                Step.OFFER_SETTINGS -> offerSettings(context)
            }
        }
    }

    private fun explainThenAsk(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.location_rationale_title)
            .setMessage(R.string.location_rationale_message)
            .setPositiveButton(R.string.location_rationale_continue) { _, _ -> permissionLauncher.launch(LOCATION_PERMISSIONS) }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun offerSettings(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.location_settings_title)
            .setMessage(R.string.location_settings_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun rememberDenial(isDenied: Boolean) {
        val context = fragment.context?.applicationContext ?: return
        fragment.lifecycleScope.launch {
            context.dataStore.edit { it[PreferencesKeys.LOCATION_PERMISSION_DENIED] = isDenied }
        }
    }

    @SuppressLint("MissingPermission") // Only called with location permission.
    private fun fetchLocation() {
        val context = fragment.context ?: return
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
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
