package com.szabolcshorvath.memorymap

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import com.szabolcshorvath.memorymap.auth.AccountService
import com.szabolcshorvath.memorymap.auth.GoogleAccountService
import com.szabolcshorvath.memorymap.backup.BackupStorage
import com.szabolcshorvath.memorymap.backup.DriveBackupStorage
import com.szabolcshorvath.memorymap.util.FusedLocationService
import com.szabolcshorvath.memorymap.util.GooglePlaceSearch
import com.szabolcshorvath.memorymap.util.LocationService
import com.szabolcshorvath.memorymap.util.PlaceSearch

/**
 * The outside services the app talks to through an interface. UI tests replace them with fakes before the app
 * starts, so a test never signs in to a real account or touches real backups. Always read them here, at the
 * time of the call, instead of keeping a reference.
 */
object AppServices {
    @Volatile
    @set:VisibleForTesting
    var accounts: AccountService = GoogleAccountService

    @Volatile
    @set:VisibleForTesting
    var backupStorage: BackupStorage = DriveBackupStorage

    /**
     * The system photo picker the Add form opens. UI tests replace it with one that returns chosen photos at once.
     * A screen reads it when it registers its launcher, which it does once, when it is created.
     */
    @Volatile
    @set:VisibleForTesting
    var mediaPicker: ActivityResultContract<PickVisualMediaRequest, List<Uri>> = ActivityResultContracts.PickMultipleVisualMedia()

    /** Where the phone is, for the "my location" buttons and the first view of the map and the location picker. */
    @Volatile
    @set:VisibleForTesting
    var location: LocationService = FusedLocationService

    /** Searching for places by name in the location picker, and looking up a place's details. */
    @Volatile
    @set:VisibleForTesting
    var placeSearch: PlaceSearch = GooglePlaceSearch
}
