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

    /** Adds a memory and returns its id. Memories are shown newest first, so [daysAgo] sets their order. */
    fun memory(
        title: String,
        daysAgo: Long = 1,
        latitude: Double = 47.4979,
        longitude: Double = 19.0402,
        placeName: String? = "Budapest",
    ): Int = runBlocking {
        val start = ZonedDateTime.now(ZoneId.systemDefault()).minusDays(daysAgo).withHour(12).withMinute(0)
        val group = MemoryGroup(
            title = title,
            description = null,
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            address = null,
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
}
