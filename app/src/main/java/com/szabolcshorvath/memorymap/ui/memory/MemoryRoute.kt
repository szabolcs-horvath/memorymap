package com.szabolcshorvath.memorymap.ui.memory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.fragment.MemoryFragmentViewModel
import com.szabolcshorvath.memorymap.ui.common.ConfirmDialog
import com.szabolcshorvath.memorymap.ui.common.rememberRelinkRequester
import kotlinx.coroutines.launch
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

/** A memory's page with its ViewModel: the page, the question before a delete, and the "Re-link" flow. */
@Composable
fun MemoryRoute(viewModel: MemoryFragmentViewModel, common: CommonViewModel, navigation: MemoryNavigation) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showFragmentMarkers by common.showFragmentsEnabled.collectAsStateWithLifecycle()
    val relink = rememberRelinkRequester(viewModel::relink)
    val scope = rememberCoroutineScope()
    val navigate by rememberUpdatedState(navigation)
    var askingToDelete by rememberSaveable { mutableStateOf(false) }
    val actions = remember(viewModel, relink, scope) {
        object : MemoryActions {
            override fun back() = navigate.back()
            override fun edit() = navigate.edit(viewModel.memoryId)
            override fun delete() {
                askingToDelete = true
            }
            override fun showOnMap() {
                val group = viewModel.uiState.value.group ?: return
                navigate.showOnMap(group.latitude, group.longitude, group.id)
            }
            override fun showOnTimeline() = navigate.showOnTimeline(viewModel.memoryId)
            override fun showFragmentOnMap(fragment: MemoryFragmentEntity) =
                navigate.showOnMap(fragment.latitude, fragment.longitude, fragment.groupId)
            override fun openMedia(item: MediaItem) {
                scope.launch { if (viewModel.isMissing(item)) navigate.openMissing(item) else navigate.openViewer(item) }
            }
            override fun relinkMedia() = relink.start()
            override fun saveMediaOrder(items: List<MediaItem>) = viewModel.saveMediaOrder(items)
            override fun saveFragmentsOrder(fragments: List<MemoryFragmentEntity>) = viewModel.saveFragmentsOrder(fragments)
        }
    }

    MemoryContent(state, showFragmentMarkers, actions)
    if (askingToDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_memory),
            message = stringResource(R.string.delete_memory_message),
            confirm = stringResource(R.string.delete),
            cancel = stringResource(R.string.cancel),
            onConfirm = {
                askingToDelete = false
                scope.launch {
                    val deleted = viewModel.delete() ?: return@launch
                    navigate.onDeleted(deleted)
                    navigate.back()
                }
            },
            onCancel = { askingToDelete = false },
        )
    }
}
