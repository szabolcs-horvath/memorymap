package com.szabolcshorvath.memorymap.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import com.google.android.gms.maps.model.LatLng
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.PermissionUtil.hasLocationPermission
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What a "my location" button does when tapped. */
enum class MyLocationStep { USE_LOCATION, EXPLAIN_THEN_ASK, OFFER_SETTINGS }

/**
 * With permission, use the location. Otherwise explain and ask, unless the user said no before and Android would no
 * longer show its dialog (it shows a rationale hint only while asking again is still possible).
 */
internal fun myLocationStep(hasPermission: Boolean, shouldShowRationale: Boolean, wasDenied: Boolean): MyLocationStep = when {
    hasPermission -> MyLocationStep.USE_LOCATION
    shouldShowRationale || !wasDenied -> MyLocationStep.EXPLAIN_THEN_ASK
    else -> MyLocationStep.OFFER_SETTINGS
}

/**
 * What a "my location" button does, shared by the map and the location picker: finds the user's location and passes it
 * on. Location access is asked for only here, when the user taps the button, never on its own: first a short
 * explanation, then the system dialog. Once the user has said no and Android no longer shows its dialog, it offers the
 * app's settings instead (whether they said no is kept in DataStore, so it survives restarts).
 *
 * Get it with [rememberMyLocationRequester], and show its questions with [MyLocationQuestions].
 */
@Stable
class MyLocationRequester internal constructor(
    private val context: Context,
    private val activity: Activity?,
    private val scope: CoroutineScope,
    private val onLocation: State<(LatLng) -> Unit>,
    internal val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    /** The question open now; saved, so it comes back after the phone is turned. */
    internal val question: MutableState<MyLocationStep?>,
) {
    /** Called when the user taps the button. */
    fun start() {
        scope.launch {
            val wasDenied = context.dataStore.data.first()[PreferencesKeys.LOCATION_PERMISSION_DENIED] ?: false
            val shouldShowRationale = activity != null &&
                LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            when (val step = myLocationStep(hasLocationPermission(context), shouldShowRationale, wasDenied)) {
                MyLocationStep.USE_LOCATION -> {
                    // Allowed in Settings since: Android may ask again if it is revoked.
                    if (wasDenied) rememberDenial(context, scope, isDenied = false)
                    fetchLocation(context, onLocation.value)
                }

                else -> question.value = step
            }
        }
    }

    internal fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent)
    }

    internal companion object {
        val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

/** A [MyLocationRequester] that passes the location it finds to [onLocation]. */
@Composable
fun rememberMyLocationRequester(onLocation: (LatLng) -> Unit): MyLocationRequester {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val currentOnLocation = rememberUpdatedState(onLocation)
    val question = rememberSaveable { mutableStateOf<MyLocationStep?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        // Approximate location alone is enough.
        val isGranted = results.values.any { it }
        rememberDenial(context, scope, isDenied = !isGranted)
        if (isGranted) fetchLocation(context, currentOnLocation.value)
    }
    return remember(context, activity, scope, launcher) {
        MyLocationRequester(context, activity, scope, currentOnLocation, launcher, question)
    }
}

/** The questions [requester] asks before Android's own dialog: the explanation, or the offer to open the settings. */
@Composable
fun MyLocationQuestions(requester: MyLocationRequester) {
    val close = { requester.question.value = null }
    when (requester.question.value) {
        MyLocationStep.EXPLAIN_THEN_ASK -> ConfirmDialog(
            title = stringResource(R.string.location_rationale_title),
            message = stringResource(R.string.location_rationale_message),
            confirm = stringResource(R.string.location_rationale_continue),
            cancel = stringResource(R.string.not_now),
            onConfirm = {
                close()
                requester.launcher.launch(MyLocationRequester.LOCATION_PERMISSIONS)
            },
            onCancel = close,
        )

        MyLocationStep.OFFER_SETTINGS -> ConfirmDialog(
            title = stringResource(R.string.location_settings_title),
            message = stringResource(R.string.location_settings_message),
            confirm = stringResource(R.string.open_settings),
            cancel = stringResource(R.string.not_now),
            onConfirm = {
                close()
                requester.openAppSettings()
            },
            onCancel = close,
        )

        MyLocationStep.USE_LOCATION, null -> Unit
    }
}

private fun rememberDenial(context: Context, scope: CoroutineScope, isDenied: Boolean) {
    val appContext = context.applicationContext
    scope.launch { appContext.dataStore.edit { it[PreferencesKeys.LOCATION_PERMISSION_DENIED] = isDenied } }
}

/** Finds where the phone is and passes it on. Only called with location permission. */
private fun fetchLocation(context: Context, onLocation: (LatLng) -> Unit) {
    AppServices.location.currentLocation(context).addOnCompleteListener { task ->
        // Null when the phone's location is turned off or there is no fix yet.
        val location = if (task.isSuccessful) task.result else null
        if (location != null) {
            onLocation(LatLng(location.latitude, location.longitude))
        } else {
            Toast.makeText(context, R.string.location_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
