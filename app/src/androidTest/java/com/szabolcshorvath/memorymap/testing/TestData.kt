package com.szabolcshorvath.memorymap.testing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.MediaHasher
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
        }
    }

    /**
     * Adds a memory that starts at [hour]:00 [daysAgo] days ago and returns its id. Memories are shown newest first,
     * so [daysAgo] and [hour] set their order.
     */
    fun memory(
        title: String,
        daysAgo: Long = 1,
        hour: Int = 12,
        place: Place = Place.BUDAPEST,
        description: String? = null,
    ): Int = runBlocking {
        val start = ZonedDateTime.now(ZoneId.systemDefault()).minusDays(daysAgo).withHour(hour).withMinute(0)
        val group = MemoryGroup(
            title = title,
            description = description,
            latitude = place.latitude,
            longitude = place.longitude,
            placeName = place.name,
            address = place.address,
            startDate = start,
            endDate = start.plusHours(2),
            isAllDay = false,
        )
        dao.insertGroup(group).toInt()
    }

    /** Adds a photo (a generated image in the phone's gallery, owned by the app) to memory [groupId]. */
    fun photo(groupId: Int, color: Int = Color.rgb(30, 120, 200)): Uri = runBlocking {
        val uri = createGalleryImage(color)
        val size = context.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }
        dao.insertMediaItems(
            listOf(
                MediaItem(
                    groupId = groupId,
                    uri = uri.toString(),
                    deviceId = InstallationIdentifier.getInstallationIdentifier(context),
                    type = MediaType.IMAGE,
                    mediaSignature = MediaHasher.calculateMediaSignature(context, uri),
                    fileSize = size,
                    dateTaken = 0,
                    order = 0,
                )
            )
        )
        uri
    }

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
                    deviceId = "another-device",
                    type = MediaType.IMAGE,
                    mediaSignature = "${fileSize}_00000000000000000000000000000000",
                    fileSize = fileSize,
                    dateTaken = 0,
                    order = 0,
                )
            )
        )
    }

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
}
