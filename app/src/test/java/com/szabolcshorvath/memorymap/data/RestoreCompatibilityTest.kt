package com.szabolcshorvath.memorymap.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.szabolcshorvath.memorymap.data.SchemaDatabases.SAMPLE_DATE
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.RandomAccessFile
import java.time.ZonedDateTime

/**
 * Runs backups through the same steps a restore uses: open the backup copy with the app's database
 * configuration (migrations, schema and integrity checks), read everything, and replace the live
 * database's contents with it.
 */
@ExtendWith(RobolectricExtension::class)
class RestoreCompatibilityTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var live: MemoryMapDatabase

    @BeforeEach
    fun setUp() = runBlocking {
        live = Room.inMemoryDatabaseBuilder(context, MemoryMapDatabase::class.java).build()
        // The user's current data, which a restore must replace (or, on failure, leave alone).
        live.groupDaoForTest().insertGroup(LIVE_GROUP)
        live.contentsDaoForTest().insertPresets(listOf(HSVPreset(id = 70, hue = 10f, saturation = 1f, brightness = 1f)))
        Unit
    }

    @AfterEach
    fun tearDown() {
        live.close()
    }

    @ParameterizedTest(name = "schema version {0}")
    @MethodSource("backupVersions")
    fun `a backup restores with all its data`(version: Int) = runBlocking {
        val file = SchemaDatabases.create(context, "backup_v$version.db", version)

        restore(file.name)

        val columns = SchemaDatabases.columns(version)
        val groupColumns = columns.getValue("memory_groups")

        val group = live.contentsDaoForTest().getAllGroups().single()
        assertEquals(1, group.id)
        assertEquals("memory_groups.title", group.title)
        assertEquals("memory_groups.description", group.description)
        assertEquals("memory_groups.placeName", group.placeName)
        assertEquals(ZonedDateTime.parse(SAMPLE_DATE), group.startDate)
        assertEquals(ZonedDateTime.parse(SAMPLE_DATE), group.endDate)
        assertTrue(group.isAllDay)
        // Columns added by later migrations get their defaults; markerValue was renamed to markerBrightness.
        assertEquals(if ("markerHue" in groupColumns) 0.5f else 0f, group.markerHue)
        assertEquals(if ("markerSaturation" in groupColumns) 0.5f else 1f, group.markerSaturation)
        assertEquals(if ("markerBrightness" in groupColumns || "markerValue" in groupColumns) 0.5f else 1f, group.markerBrightness)

        val media = live.contentsDaoForTest().getAllMediaItems().single()
        assertEquals(1, media.groupId)
        assertEquals("media_items.uri", media.uri)
        assertEquals("media_items.mediaSignature", media.mediaSignature)
        assertEquals(MediaType.VIDEO, media.type)
        assertEquals(3L, media.fileSize)
        assertEquals(if ("order" in columns.getValue("media_items")) 3 else null, media.order)

        val fragments = live.contentsDaoForTest().getAllFragments()
        if ("memory_fragments" in columns) {
            val fragment = fragments.single()
            assertEquals(1, fragment.groupId)
            assertEquals("memory_fragments.placeName", fragment.placeName)
            assertEquals(ZonedDateTime.parse(SAMPLE_DATE), fragment.startDate)
        } else {
            assertEquals(emptyList<MemoryFragment>(), fragments)
        }

        val presets = live.contentsDaoForTest().getAllPresets()
        if ("hsv_presets" in columns) {
            assertEquals(0.5f, presets.single().hue)
        } else {
            assertEquals(emptyList<HSVPreset>(), presets)
        }
    }

    @Test
    fun `a corrupt backup is rejected and the live data is kept`() {
        val file = SchemaDatabases.create(context, "corrupt.db", MemoryMapDatabase.DB_VERSION)
        RandomAccessFile(file, "rw").use { raf ->
            // Keep the header and schema page, overwrite every table page.
            raf.seek(PAGE_SIZE)
            raf.write(ByteArray((raf.length() - PAGE_SIZE).toInt()) { 0x55 })
        }

        assertThrows<SQLiteException> { runBlocking { restore(file.name) } }
        assertLiveDataUnchanged()
    }

    @Test
    fun `a file that is not a database is rejected instead of being read as an empty backup`() {
        context.getDatabasePath("junk.db").apply { parentFile?.mkdirs() }.writeText("this is not a database".repeat(100))

        assertThrows<SQLiteException> { runBlocking { restore("junk.db") } }
        assertLiveDataUnchanged()
    }

    @Test
    fun `an empty file is rejected instead of being read as an empty backup`() {
        context.getDatabasePath("empty.db").apply { parentFile?.mkdirs() }.writeBytes(ByteArray(0))

        assertThrows<SQLiteException> { runBlocking { restore("empty.db") } }
        assertLiveDataUnchanged()
    }

    @Test
    fun `a failure while writing rolls back and keeps the live data`() = runBlocking {
        val group = LIVE_GROUP.copy(id = 1, title = "Backup")
        val broken = DatabaseContents(groups = listOf(group, group), mediaItems = emptyList(), fragments = emptyList(), presets = emptyList())

        assertThrows<SQLiteException> { runBlocking { broken.replaceAllIn(live.contentsDaoForTest()) } }
        assertLiveDataUnchanged()
    }

    @Test
    fun `media and fragments without a memory are skipped`() = runBlocking {
        val file = SchemaDatabases.create(context, "orphans.db", MemoryMapDatabase.DB_VERSION)
        val contents = MemoryMapDatabase.withCheckedCopy(context, file.name) {
            DatabaseContents.readFrom(it.contentsDaoForTest())
        }
        val withOrphans = contents.copy(
            mediaItems = contents.mediaItems + contents.mediaItems.single().copy(id = 2, groupId = 99),
            fragments = contents.fragments + contents.fragments.single().copy(id = 2, groupId = 99)
        )

        withOrphans.replaceAllIn(live.contentsDaoForTest())

        assertEquals(listOf(1), live.contentsDaoForTest().getAllMediaItems().map { it.id })
        assertEquals(listOf(1), live.contentsDaoForTest().getAllFragments().map { it.id })
    }

    @Test
    fun `restore covers every table in the current schema`() {
        assertEquals(SchemaDatabases.columns(MemoryMapDatabase.DB_VERSION).keys, DatabaseContents.TABLES)
    }

    private suspend fun restore(backupName: String) {
        val contents = MemoryMapDatabase.withCheckedCopy(context, backupName) {
            DatabaseContents.readFrom(it.contentsDaoForTest())
        }
        contents.replaceAllIn(live.contentsDaoForTest())
    }

    private fun assertLiveDataUnchanged() = runBlocking {
        assertEquals(listOf(LIVE_GROUP), live.contentsDaoForTest().getAllGroups())
        assertEquals(listOf(70), live.contentsDaoForTest().getAllPresets().map { it.id })
        assertNull(live.groupDaoForTest().getGroupWithMedia(1))
    }

    companion object {
        private const val PAGE_SIZE = 4096L

        private val LIVE_GROUP = MemoryGroup(
            id = 50,
            title = "Live memory",
            description = null,
            latitude = 1.0,
            longitude = 2.0,
            placeName = null,
            address = null,
            startDate = ZonedDateTime.parse("2026-10-24T12:00:00+02:00[Europe/Budapest]"),
            endDate = ZonedDateTime.parse("2026-10-24T13:00:00+02:00[Europe/Budapest]"),
            isAllDay = false
        )

        @JvmStatic
        fun backupVersions() = (SchemaDatabases.OLDEST_BACKUP_VERSION..MemoryMapDatabase.DB_VERSION).toList()
    }
}
