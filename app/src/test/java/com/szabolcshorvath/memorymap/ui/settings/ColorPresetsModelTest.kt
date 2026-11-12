package com.szabolcshorvath.memorymap.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.work.testing.WorkManagerTestInitHelper
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.data.PresetRepository
import com.szabolcshorvath.memorymap.ui.color.Hsv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** The colour presets in Settings: selecting, editing, undoing, deleting and reordering them. */
@ExtendWith(RobolectricExtension::class)
class ColorPresetsModelTest {

    private lateinit var db: MemoryMapDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var model: ColorPresetsModel
    private val messages = mutableListOf<Int>()
    private val context get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MemoryMapDatabase::class.java).allowMainThreadQueries().build()
        // A preset change schedules an automatic backup; the test WorkManager only records it.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        runBlocking { db.hsvPresetDao().insertPresets(listOf(RED, GREEN, BLUE)) }
        // The model runs on the main thread, which the test thread stands in for.
        scope = CoroutineScope(SupervisorJob() + Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        model = ColorPresetsModel(scope, PresetRepository(context, db)) { messages += it }
        awaitPresets(RED_ID, GREEN_ID, BLUE_ID)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `a selected preset's swatch follows the sliders until Undo puts it back`() {
        model.select(GREEN_ID)
        model.setColor(Hsv(200f, 1f, 1f))

        assertTrue(model.state.value.changed)
        assertEquals(200f, model.state.value.presets.first { it.id == GREEN_ID }.hue)

        model.undo()

        assertFalse(model.state.value.changed)
        assertEquals(GREEN.hue, model.state.value.presets.first { it.id == GREEN_ID }.hue)
    }

    @Test
    fun `deleting the selected preset keeps the others and clears the selection`() {
        model.select(GREEN_ID)

        model.delete(GREEN_ID)

        awaitPresets(RED_ID, BLUE_ID)
        assertNull(model.state.value.selectedPresetId)
    }

    @Test
    fun `an order that leaves out a preset is ignored`() {
        // What a swatch going away used to send: no order at all, which emptied the row.
        model.reorder(emptyList())
        model.reorder(listOf(BLUE_ID, RED_ID))

        assertEquals(listOf(RED_ID, GREEN_ID, BLUE_ID), model.state.value.presets.map { it.id })
    }

    @Test
    fun `a new order of the same presets is shown at once and stored`() {
        model.reorder(listOf(BLUE_ID, RED_ID, GREEN_ID))

        assertEquals(listOf(BLUE_ID, RED_ID, GREEN_ID), model.state.value.presets.map { it.id })
        awaitStored(BLUE_ID, RED_ID, GREEN_ID)
    }

    /** Runs the main thread until the model shows the presets [ids], in this order. */
    private fun awaitPresets(vararg ids: Int) = await("the presets ${ids.toList()}") {
        model.state.value.presets.map { it.id } == ids.toList()
    }

    private fun awaitStored(vararg ids: Int) = await("the stored order ${ids.toList()}") {
        runBlocking { db.hsvPresetDao().getAllPresetsFlow().first() }.map { it.id } == ids.toList()
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        assertTrue(condition(), "Waited for $what")
    }

    private companion object {
        const val RED_ID = 1
        const val GREEN_ID = 2
        const val BLUE_ID = 3
        val RED = HSVPreset(RED_ID, 0f, 1f, 1f, order = 0)
        val GREEN = HSVPreset(GREEN_ID, 120f, 1f, 1f, order = 1)
        val BLUE = HSVPreset(BLUE_ID, 240f, 1f, 1f, order = 2)
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
