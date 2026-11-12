package com.szabolcshorvath.memorymap.testing

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaThumbnail
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeLocationService
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.TestData.photoTakenAt
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.MediaDetails
import com.szabolcshorvath.memorymap.util.MediaHasher
import com.szabolcshorvath.memorymap.util.PermissionUtil
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Puts data in the app before a test opens it: memories, photos and a signed-in account. It writes through the
 * app's own database and DataStore (the tests run in the app's process). The test orchestrator clears the app's
 * data between tests; [deleteCreatedPhotos] removes the photos this test added to the phone's gallery.
 */
object TestData {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = MemoryMapDatabase.getDatabase(context).memoryGroupDao()
    private val createdPhotos = mutableListOf<Uri>()
    private val shellPhotos = mutableListOf<String>()

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

    /** Gives memory [groupId] the marker colour [color] (every memory starts red, hue 0). */
    fun markerColor(groupId: Int, color: Hsv) = runBlocking {
        val group = checkNotNull(dao.getGroupWithMedia(groupId)) { "No memory $groupId" }.group
        dao.updateGroup(group.copy(markerHue = color.hue, markerSaturation = color.saturation, markerBrightness = color.brightness))
    }

    /**
     * Adds a photo (a generated image in the phone's gallery, owned by the app) to memory [groupId]. A memory shows
     * its photos by [order], lowest first. With [rightHalf], the right half of the photo has that colour instead of
     * [color], so a zoom can be seen.
     */
    fun photo(
        groupId: Int,
        color: Int = Color.rgb(30, 120, 200),
        order: Int = 0,
        rightHalf: Int = color,
    ): Uri = createGalleryImage(color, rightHalf).also { insertMedia(groupId, it, order) }

    /** Adds a video of [color] to the phone's gallery only, in no memory: one for the fake photo picker to return. */
    fun galleryVideo(color: Int): Uri = createGalleryVideo(color)

    private fun insertMedia(groupId: Int, uri: Uri, order: Int) = runBlocking {
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
                    order = order,
                )
            )
        )
    }

    /**
     * Adds a photo to the phone's gallery only, in no memory: one for the fake photo picker to return. Photos of
     * one colour are the same file to the app, which keeps only one of them in a memory.
     */
    fun galleryPhoto(color: Int = Color.rgb(200, 80, 40)): Uri = createGalleryImage(color)

    /**
     * Adds a photo to memory [groupId] as a restored backup brings it from another phone, and puts the same photo in
     * this phone's gallery, saved there by another app (the shell). The app can see a file it didn't save only with
     * access to photos, so re-linking finds this one only once that is granted.
     */
    fun photoSavedByAnotherApp(groupId: Int, color: Int) {
        val bytes = jpeg(color, color)
        val path = "$SHELL_PHOTOS_DIR/memorymap_ui_test_shell_${System.nanoTime()}.jpg"
        shell("mkdir -p $SHELL_PHOTOS_DIR")
        writeAsShell(path, bytes)
        shell("content call --uri content://media --method scan_file --arg $path")
        shellPhotos += path
        val size = bytes.size.toLong()
        insertPhotoFromAnotherDevice(groupId, size, MediaHasher.signatureOf(bytes.inputStream(), size))
    }

    private fun insertPhotoFromAnotherDevice(groupId: Int, fileSize: Long, signature: String) = runBlocking {
        dao.insertMediaItems(
            listOf(
                MediaItem(
                    groupId = groupId,
                    uri = "content://media/external/images/media/$MISSING_MEDIA_ID",
                    deviceId = ANOTHER_DEVICE,
                    type = MediaType.IMAGE,
                    mediaSignature = signature,
                    fileSize = fileSize,
                    dateTaken = 0,
                    order = 0,
                )
            )
        )
    }

    /**
     * Adds a photo to memory [groupId] as the photo picker saved it on this phone, after its grant was lost: by its
     * picker URI, with the size and signature of the photo as picked ([pickedColor]). The phone's gallery holds the
     * photo as an edit in place left it ([editedColor]): under the same MediaStore id, with the same date taken, but
     * other bytes. The signature can't find it; only the id in the picker URI can.
     */
    fun pickedPhotoEditedLater(groupId: Int, pickedColor: Int, editedColor: Int) = runBlocking {
        val picked = withDateTaken(jpeg(pickedColor, pickedColor))
        val edited = createGalleryImageFrom(withDateTaken(jpeg(editedColor, editedColor)))
        val dateTaken = dateTakenOf(edited)
        val size = picked.size.toLong()
        dao.insertMediaItems(
            listOf(
                MediaItem(
                    groupId = groupId,
                    uri = "content://media/picker/0/com.android.providers.media.photopicker/media/${ContentUris.parseId(edited)}",
                    deviceId = InstallationIdentifier.getInstallationIdentifier(context),
                    type = MediaType.IMAGE,
                    mediaSignature = MediaHasher.signatureOf(picked.inputStream(), size),
                    fileSize = size,
                    dateTaken = dateTaken,
                    order = 0,
                )
            )
        )
    }

    /**
     * Adds a photo of [color] to the phone's gallery only, taken [secondsAfter] seconds after the others this test adds
     * this way (a negative number: before), and returns its date taken as the gallery read it.
     */
    fun photoTakenAt(color: Int, secondsAfter: Long): Long =
        photoTakenAt(jpeg(color, color), secondsAfter)

    /** As [photoTakenAt], with vertical [stripes] instead of one colour, for photos that look alike or not. */
    fun photoTakenAt(stripes: Stripes, secondsAfter: Long): Long =
        photoTakenAt(compress(stripes.bitmap()), secondsAfter)

    private fun photoTakenAt(jpeg: ByteArray, secondsAfter: Long): Long {
        val uri = createGalleryImageFrom(withDateTaken(jpeg, BASE_TIME.plusSeconds(secondsAfter).format(EXIF_FORMAT)))
        return dateTakenOf(uri)
    }

    /**
     * Nine vertical stripes of two greys. Photos of one colour all have the same perceptual hash; stripes that start
     * with the light grey and stripes that start with the dark one are as far apart as hashes get.
     */
    enum class Stripes(private val first: Int, private val second: Int) {
        LIGHT_FIRST(Color.rgb(220, 220, 220), Color.rgb(40, 40, 40)),
        DARK_FIRST(Color.rgb(40, 40, 40), Color.rgb(220, 220, 220));

        internal fun bitmap(): Bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val stripe = IMAGE_WIDTH / STRIPES
            repeat(STRIPES) { index ->
                canvas.save()
                canvas.clipRect(index * stripe, 0, if (index == STRIPES - 1) IMAGE_WIDTH else (index + 1) * stripe, IMAGE_HEIGHT)
                canvas.drawColor(if (index % 2 == 0) first else second)
                canvas.restore()
            }
        }
    }

    /**
     * Adds a photo to memory [groupId] as it was saved on this phone and whose file was deleted afterwards: its picker
     * URI names no file, so re-linking can't find it. It was taken at [dateTaken], or at an unknown time (0). With
     * [looksLike], it has the perceptual hash of those stripes; with [thumbnailColor], a stored thumbnail of that colour.
     */
    fun missingPhoto(groupId: Int, dateTaken: Long, order: Int = 0, looksLike: Stripes? = null, thumbnailColor: Int? = null) = runBlocking {
        val signature = "123456_0123456789abcdef0123456789abcdef"
        dao.insertMediaItems(
            listOf(
                MediaItem(
                    groupId = groupId,
                    uri = "content://media/picker/0/com.android.providers.media.photopicker/media/$MISSING_MEDIA_ID",
                    deviceId = InstallationIdentifier.getInstallationIdentifier(context),
                    type = MediaType.IMAGE,
                    mediaSignature = signature,
                    fileSize = 123_456,
                    dateTaken = dateTaken,
                    order = order,
                    displayName = "IMG_20250314_092653.jpg",
                    width = IMAGE_WIDTH,
                    height = IMAGE_HEIGHT,
                    perceptualHash = looksLike?.bitmap()?.let(MediaDetails::perceptualHash),
                )
            )
        )
        thumbnailColor?.let {
            val thumbnail = compress(Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(it) })
            MemoryMapDatabase.getDatabase(context).mediaThumbnailDao().insert(MediaThumbnail(signature, thumbnail))
        }
    }

    /** Waits until the app has stored [count] thumbnails: the start-up backfill runs in the background. */
    fun awaitStoredThumbnails(count: Int) {
        val thumbnails = MemoryMapDatabase.getDatabase(context).mediaThumbnailDao()
        val stored = Ui.eventually(read = { runBlocking { thumbnails.getSignatures().size } }) { it >= count }
        check(stored >= count) { "The app stored $stored thumbnails, not $count" }
    }

    private fun compress(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        bitmap.recycle()
        stream.toByteArray()
    }

    private fun dateTakenOf(uri: Uri): Long {
        val dateTaken = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L } ?: 0L
        check(dateTaken > 0) { "The gallery gave the test photo no date taken" }
        return dateTaken
    }

    /** [jpeg] with an EXIF capture time ([date], `yyyy:MM:dd HH:mm:ss`), which the gallery reads as its date taken. */
    private fun withDateTaken(jpeg: ByteArray, date: String = EXIF_DATE_TAKEN): ByteArray {
        val file = File.createTempFile("memorymap_ui_test_exif", ".jpg", context.cacheDir)
        try {
            file.writeBytes(jpeg)
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date)
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+00:00")
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    /** Writes [bytes] into the file at [path] as the shell does (`dd` copies what it reads into the file). */
    private fun writeAsShell(path: String, bytes: ByteArray) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val (output, input) = automation.executeShellCommandRw("dd of=$path")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() } // Waits until dd is done.
    }

    private fun shell(command: String): String = Ui.device.executeShellCommand(command)

    /** Adds a fragment at [place] to memory [groupId], with no dates of its own. A memory lists its fragments by [order]. */
    fun fragment(groupId: Int, place: Place, order: Int? = null) = runBlocking {
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
                    order = order,
                )
            )
        )
    }

    /** Turns the fragments' own markers on or off, as the Settings switch does. */
    fun showFragmentMarkers(enabled: Boolean) = runBlocking {
        context.dataStore.edit { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] = enabled }
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

    /**
     * Keeps the app's database busy with a write until [DatabaseHold.release], so a save started meanwhile waits for
     * it (reading still works). Release it in a `finally`, or the app's writes wait for good.
     */
    fun holdDatabaseWrites(): DatabaseHold {
        val hold = DatabaseHold()
        thread(name = "TestDatabaseHold") {
            MemoryMapDatabase.getDatabase(context).runInTransaction {
                hold.held.countDown()
                hold.released.await()
            }
        }
        check(hold.held.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "The test could not get the database" }
        return hold
    }

    /** A write that [holdDatabaseWrites] keeps open. */
    class DatabaseHold internal constructor() {
        internal val held = CountDownLatch(1)
        internal val released = CountDownLatch(1)

        fun release() = released.countDown()
    }

    private const val HOLD_TIMEOUT_SECONDS = 10L

    fun deleteCreatedPhotos() {
        createdPhotos.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        createdPhotos.clear()
        // The app may not delete what the shell saved: the shell removes it, and a scan drops it from the gallery.
        shellPhotos.forEach { path ->
            runCatching {
                shell("rm -f $path")
                shell("content call --uri content://media --method scan_file --arg $path")
            }
        }
        shellPhotos.clear()
    }

    private fun createGalleryImage(color: Int, rightHalf: Int = color): Uri = createGalleryImageFrom(jpeg(color, rightHalf))

    private fun createGalleryImageFrom(jpeg: ByteArray): Uri = createGalleryFile(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        "jpg",
        "image/jpeg",
        "Pictures/MemoryMapUiTests",
        jpeg
    )

    private fun createGalleryVideo(color: Int): Uri = createGalleryFile(
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        "mp4",
        "video/mp4",
        "Movies/MemoryMapUiTests",
        TestVideos.mp4(color, context.cacheDir)
    )

    /** Saves [bytes] as a new file in the gallery's [collection], owned by the app, and returns its URI. */
    private fun createGalleryFile(collection: Uri, extension: String, mimeType: String, folder: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "memorymap_ui_test_${System.nanoTime()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(collection, values)) { "Could not add a test $mimeType file" }
        createdPhotos += uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** A JPEG of [color], with its right half in [rightHalf]. */
    private fun jpeg(color: Int, rightHalf: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(color)
            clipRect(IMAGE_WIDTH / 2, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
            drawColor(rightHalf)
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        return bytes
    }

    private const val IMAGE_WIDTH = 640
    private const val IMAGE_HEIGHT = 480
    private const val JPEG_QUALITY = 90
    private const val STRIPES = 9
    private const val THUMBNAIL_WIDTH = 128
    private const val THUMBNAIL_HEIGHT = 96
    private const val MISSING_MEDIA_ID = 987_654_321L
    private const val ANOTHER_DEVICE = "another-device"
    private const val SHELL_PHOTOS_DIR = "/storage/emulated/0/Pictures/MemoryMapUiTests"
    private const val EXIF_DATE_TAKEN = "2026:05:01 10:00:00"

    /** When [photoTakenAt]'s photos are taken: a time no other photo on the test phone is near. */
    private val BASE_TIME = LocalDateTime.of(2025, 3, 14, 9, 26, 53)
    private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
}
