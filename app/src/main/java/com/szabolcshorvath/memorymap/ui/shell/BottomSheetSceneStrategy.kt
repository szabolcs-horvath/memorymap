package com.szabolcshorvath.memorymap.ui.shell

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Shows an entry marked with [BottomSheetSceneStrategy.bottomSheet] as Material 3's modal bottom sheet, over the
 * screens below it, which stay as they are. Dragging the sheet away, tapping outside it or Back closes it (pops it);
 * when the entry is popped otherwise, the sheet slides away first.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull()?.takeIf { it.metadata[SheetKey] == true } ?: return null
        return BottomSheetScene(key = last.contentKey, entry = last, previousEntries = entries.dropLast(1), onBack = onBack)
    }

    companion object {
        private object SheetKey : NavMetadataKey<Boolean>

        /** The metadata of an entry shown as a bottom sheet. */
        fun bottomSheet(): Map<String, Any> = metadata { put(SheetKey, true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<T>> = previousEntries

    // The sheet's state while it shows, so that a pop from elsewhere can slide it away first.
    private var sheetState: SheetState? = null

    override val content: @Composable () -> Unit = {
        val state = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        sheetState = state
        ModalBottomSheet(onDismissRequest = onBack, sheetState = state) { entry.Content() }
    }

    override suspend fun onRemove() {
        sheetState?.hide()
    }

    override fun equals(other: Any?) = other is BottomSheetScene<*> && key == other.key && entry == other.entry &&
        previousEntries == other.previousEntries

    override fun hashCode() = (key.hashCode() * 31 + entry.hashCode()) * 31 + previousEntries.hashCode()
}
