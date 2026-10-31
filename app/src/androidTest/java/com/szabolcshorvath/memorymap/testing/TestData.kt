package com.szabolcshorvath.memorymap.testing

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeLocationService
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.MediaHasher
import com.szabolcshorvath.memorymap.util.PermissionUtil
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Puts data in the app before a test opens it: memories, photos and a signed-in account. It writes through the
 * app's own database and DataStore (the tests run in the app's process). The test orchestrator clears the app's
 * data between tests; [deleteCreatedPhotos] removes the photos this test added to the phone's gallery.
 */
object TestData {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = MemoryMapDatabase.getDatabase(context).memoryGroupDao()
    private val createdPhotos = mutableListOf<Uri>()

    /** Where a memory is: its coordinates, the place name and the address the location picker would store. */
    data class Place(val latitude: Double, val longitude: Double, val name: String?, val address: String? = null) {
        companion object {
            val BUDAPEST = Place(47.4979, 19.0402, "Budapest")
            val VIENNA = Place(48.2082, 16.3738, "Vienna")
            val MADRID = Place(40.4168, -3.7038, "Madrid")
            val PARIS = Place(48.8566, 2.3522, "Paris")
            val ROME = Place(41.9028, 12.4964, "Rome")
            val ATHENS = Place(37.9838, 23.7275, "Athens")
            val CAPE_TOWN = Place(-33.9249, 18.4241, "Cape Town")
        }
    }

    /**
     * Adds a memory that starts at [hour]:00 [daysAgo] days ago and lasts two hours, and returns its id. Memories
     * are shown newest first, so [daysAgo] and [hour] set their order.
     */
    fun memory(
        title: String,
        daysAgo: Long = 1,
        hour: Int = 12,
        place: Place = Place.BUDAPEST,
        description: String? = null,
    ): Int {
        val start = ZonedDateTime.now(ZoneId.systemDefault()).minusDays(daysAgo).withHour(hour).withMinute(0)
        return insertMemory(title, start, start.plusHours(2), place, description)
    }

    /** Adds a memory that runs from noon [startedDaysAgo] days ago to noon [endedDaysAgo] days ago. */
    fun trip(title: String, startedDaysAgo: Long, endedDaysAgo: Long, place: Place = Place.BUDAPEST): Int {
        val today = ZonedDateTime.now(ZoneId.systemDefault()).withHour(12).withMinute(0)
        return insertMemory(title, today.minusDays(startedDaysAgo), today.minusDays(endedDaysAgo), place, null)
    }

    private fun insertMemory(title: String, start: ZonedDateTime, end: ZonedDateTime, place: Place, description: String?) =
        runBlocking {
            val group = MemoryGroup(
                title = title,
                description = description,
                latitude = place.latitude,
                longitude = place.longitude,
                placeName = place.name,
                address = place.address,
                startDate = start,
                endDate = end,
                isAllDay = false,
            )
            dao.insertGroup(group).toInt()
        }

    /**
     * Adds a photo (a generated image in the phone's gallery, owned by the app) to memory [groupId]. With
     * [linkedToThisPhone] false, it is saved as a restored backup brings it from another phone: the file is here,
     * but re-linking has yet to find it. A memory shows its photos by [order], lowest first.
     */
    fun photo(
        groupId: Int,
        color: Int = Color.rgb(30, 120, 200),
        linkedToThisPhone: Boolean = true,
        order: Int = 0,
    ): Uri = runBlocking {
        val uri = createGalleryImage(color)
        val size = context.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }
        val thisPhone = InstallationIdentifier.getInstallationIdentifier(context)
        dao.insertMediaItems(
            listOf(
                MediaItem(
                    groupId = groupId,
                    uri = uri.toString(),
                    deviceId = if (linkedToThisPhone) thisPhone else ANOTHER_DEVICE,
                    type = MediaType.IMAGE,
                    mediaSignature = MediaHasher.calculateMediaSignature(context, uri),
                    fileSize = size,
                    dateTaken = 0,
                    order = order,
                )
            )
        )
        uri
    }

    /**
     * Adds a photo to the phone's gallery only, in no memory: one for the fake photo picker to return. Photos of
     * one colour are the same file to the app, which keeps only one of them in a memory.
     */
    fun galleryPhoto(color: Int = Color.rgb(200, 80, 40)): Uri = createGalleryImage(color)

    /**
     * Adds a photo to memory [groupId] as a restored backup brings it from another phone: a different installation
     * id, and a file that doesn't exist here, so re-linking finds nothing to link it to.
     */
    fun photoFromAnotherDevice(groupId: Int, fileSize: Long = FOREIGN_PHOTO_SIZE) = runBlocking {
        dao.insertMediaItems(
            listOf(
                MediaItem(
                    groupId = groupId,
                    uri = "content://media/external/images/media/$MISSING_MEDIA_ID",
                    deviceId = ANOTHER_DEVICE,
                    type = MediaType.IMAGE,
                    mediaSignature = "${fileSize}_00000000000000000000000000000000",
                    fileSize = fileSize,
                    dateTaken = 0,
                    order = 0,
                )
            )
        )
    }

    /** Adds a fragment at [place] to memory [groupId], with no dates of its own. */
    fun fragment(groupId: Int, place: Place) = runBlocking {
        dao.insertFragments(
            listOf(
                MemoryFragment(
                    groupId = groupId,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    placeName = place.name,
                    address = place.address,
                    startDate = null,
                    endDate = null,
                )
            )
        )
    }

    /** Turns marker clustering on or off, as the Settings switch does. */
    fun markerClustering(enabled: Boolean) = runBlocking {
        context.dataStore.edit { it[PreferencesKeys.MARKER_CLUSTERING_ENABLED] = enabled }
    }

    /** Sets the date filter the map opens with, as the Settings dropdown does. */
    fun defaultMapFilter(option: DateFilterOption) = runBlocking {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_DATE_FILTER] = option.name }
    }

    /** A colour as the colour sliders set it: hue 0-360, saturation and brightness 0-1. */
    data class Hsv(val hue: Float, val saturation: Float = 1f, val brightness: Float = 1f)

    /**
     * Sets the colour presets, in this order. The app adds its default presets on the first start only when there
     * are none, so these replace them.
     */
    fun colorPresets(vararg colors: Hsv) = runBlocking {
        MemoryMapDatabase.getDatabase(context).hsvPresetDao().insertPresets(
            colors.mapIndexed { index, color ->
                HSVPreset(hue = color.hue, saturation = color.saturation, brightness = color.brightness, order = index)
            }
        )
    }

    /** Grants the app access to all photos and videos, as "Allow all" in the system dialog does. */
    fun grantMediaAccess() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        PermissionUtil.getRequiredMediaPermissions().forEach { automation.grantRuntimePermission(context.packageName, it) }
    }

    /** Grants the app location access, as "While using the app" in the system dialog does. */
    fun grantLocationAccess() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        LOCATION_PERMISSIONS.forEach { automation.grantRuntimePermission(context.packageName, it) }
    }

    /** The phone is at [place], as far as the app can tell (once it has location access). */
    fun phoneAt(place: Place) {
        FakeLocationService.position = place.latitude to place.longitude
    }

    private val LOCATION_PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Signs in as [email] the way a finished sign-in leaves the app; Drive calls then go to the fake storage. */
    fun signIn(email: String = FakeAccountService.DEFAULT_EMAIL) = runBlocking {
        context.dataStore.edit { it[PreferencesKeys.USER_EMAIL_KEY] = email }
    }

    fun deleteCreatedPhotos() {
        createdPhotos.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        createdPhotos.clear()
    }

    private fun createGalleryImage(color: Int): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "memorymap_ui_test_${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MemoryMapUiTests")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(resolver.insert(collection, values)) { "Could not add a test image" }
        createdPhotos += uri

        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()

        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private const val IMAGE_WIDTH = 640
    private const val IMAGE_HEIGHT = 480
    private const val JPEG_QUALITY = 90
    private const val FOREIGN_PHOTO_SIZE = 2_345_678L
    private const val MISSING_MEDIA_ID = 987_654_321L
    private const val ANOTHER_DEVICE = "another-device"
}
