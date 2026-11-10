package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.ui.add.AddMemoryActions
import com.szabolcshorvath.memorymap.ui.add.AddMemoryContent
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** The Add tab: the form for a new memory, or for editing one ([requestEditMode]). Its content is [AddMemoryContent]. */
class AddMemoryGroupFragment : Fragment(), AddMemoryActions {

    private val commonViewModel: CommonViewModel by activityViewModels { CommonViewModel.Factory }
    private val viewModel: AddMemoryGroupFragmentViewModel by viewModels { AddMemoryGroupFragmentViewModel.Factory }

    data class SelectedMedia(
        val uri: Uri,
        val type: MediaType,
        val deviceId: String,
        val fileSize: Long = 0L,
        val dateTaken: Long = 0L,
        val mediaSignature: String = ""
    )

    private var addMemoryGroupListener: AddMemoryGroupListener? = null

    private val pickMediaLauncher =
        registerForActivityResult(AppServices.mediaPicker) { uris ->
            val context = context ?: return@registerForActivityResult
            val knownDeviceId = viewModel.currentDeviceId
            lifecycleScope.launch {
                // One content resolver call or more per picked item: keep them off the main thread.
                val pickedItems = withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver
                    uris.forEach { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Could not persist access for $uri", e)
                        }
                    }
                    val deviceId = knownDeviceId ?: InstallationIdentifier.getInstallationIdentifier(context)
                    uris.map { uri -> readPickedMedia(context, uri, deviceId) }
                }
                // New media come first.
                viewModel.addPickedMedia(pickedItems)
            }
        }

    /** Reads the type, size and date taken of a picked item. Does content resolver calls: not on the main thread. */
    private fun readPickedMedia(context: Context, uri: Uri, deviceId: String): SelectedMedia {
        val type = context.contentResolver.getType(uri)
        val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
        val metadata = LocalMediaUtil.queryMetadata(context, uri)
        if (metadata == null) Log.w(TAG, "Could not read the size and date of picked media $uri")
        return SelectedMedia(uri, mediaType, deviceId, metadata?.size ?: 0L, metadata?.dateTaken ?: 0L)
    }

    interface AddMemoryGroupListener {
        fun onPickLocation(lat: Double?, lng: Double?, placeName: String?, address: String?)
        fun onMemorySaved(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AddMemoryGroupListener) {
            addMemoryGroupListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The questions come back after a rotation or process death, so what they act on comes with the answer.
        ConfirmDialog.listen(this, CLEAR_REQUEST) { viewModel.resetForm() }
        ConfirmDialog.listen(this, DISCARD_FOR_EDIT_REQUEST, onCancel = { dropPendingEdit(it.getInt(MEMORY_ID)) }) { extras ->
            val memoryId = extras.getInt(MEMORY_ID)
            val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@listen
            viewModel.pendingEditMemoryId = memoryId // After process death, the new ViewModel no longer has it.
            loadForEditing(memoryId, viewLifecycleOwner)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the form's scroll position and its open picker under this id.
            id = R.id.add_memory_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val presets by commonViewModel.allPresets.collectAsStateWithLifecycle(emptyList())
                MemoryMapTheme { AddMemoryContent(state, presets, this@AddMemoryGroupFragment) }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewLifecycleOwner = viewLifecycleOwner
        val context = requireContext()
        loadPendingEdit()

        if (viewModel.currentDeviceId == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.currentDeviceId = InstallationIdentifier.getInstallationIdentifier(context)
                viewModel.publish()
            }
        }

        // Only while shown: a hidden tab keeps its view, and a save error must not show over another screen.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.saveResult.collect { result ->
                    when (result) {
                        // The view model has already cleared the form.
                        is AddMemoryGroupFragmentViewModel.SaveResult.Success ->
                            if (result.openMemory) addMemoryGroupListener?.onMemorySaved(result.groupId)

                        AddMemoryGroupFragmentViewModel.SaveResult.Error -> viewModel.showMessage(R.string.memory_save_failed)
                    }
                }
            }
        }
    }

    override fun changeTitle(text: String) = viewModel.changeTitle(text)

    override fun changeDescription(text: String) = viewModel.changeDescription(text)

    override fun pickLocation(part: FormPart) {
        val (index, place) = when (part) {
            FormPart.Memory -> -1 to FormPlace.of(viewModel.lat, viewModel.lng, viewModel.placeName, viewModel.address)
            is FormPart.Fragment -> {
                val index = viewModel.fragments.indexOfFirst { it.localId == part.localId }
                if (index == -1) return
                val fragment = viewModel.fragments[index]
                index to FormPlace.of(fragment.lat, fragment.lng, fragment.placeName, fragment.address)
            }
        }
        viewModel.activePickingIndex = index
        addMemoryGroupListener?.onPickLocation(place?.lat, place?.lng, place?.name, place?.address)
    }

    override fun setAllDay(part: FormPart, allDay: Boolean) = viewModel.setAllDay(part, allDay)

    override fun setOwnTime(localId: String, on: Boolean) = viewModel.setOwnTime(localId, on)

    override fun setDate(part: FormPart, isStart: Boolean, date: LocalDate) = viewModel.setDate(part, isStart, date)

    override fun setTime(part: FormPart, isStart: Boolean, time: LocalTime) = viewModel.setTime(part, isStart, time)

    override fun setDateRange(part: FormPart, first: LocalDate, last: LocalDate) = viewModel.setDateRange(part, first, last)

    override fun setColor(part: FormPart, color: Hsv) = viewModel.setColor(part, color)

    override fun toggleColor(part: FormPart) = viewModel.toggleColor(part)

    override fun addFragment() = viewModel.addFragment()

    override fun removeFragment(localId: String) = viewModel.removeFragment(localId)

    override fun pickMedia() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    override fun removeMedia(item: SelectedMedia) = viewModel.removeMedia(item)

    override fun clear() {
        val isEditing = viewModel.editingMemoryId != null
        ConfirmDialog.show(
            childFragmentManager,
            CLEAR_REQUEST,
            title = getString(if (isEditing) R.string.discard_changes_title else R.string.clear_fields_title),
            message = getString(if (isEditing) R.string.discard_changes_message else R.string.clear_fields_message),
            confirm = getString(if (isEditing) R.string.discard else R.string.clear),
            cancel = getString(R.string.cancel)
        )
    }

    override fun save() = viewModel.requestSave()

    override fun onMessageShown() = viewModel.onMessageShown()

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // A save that finishes while another tab is shown must not open the memory once this tab is shown again.
        viewModel.isScreenShown = !hidden
    }

    /** The place picked on the map, for the memory or the fragment whose place was being picked. */
    fun updateLocation(newLat: Double, newLng: Double, newPlaceName: String? = null, newAddress: String? = null) {
        viewModel.setPlace(newLat, newLng, newPlaceName, newAddress)
    }

    /**
     * Opens memory [memoryId] for editing. The request is kept in the ViewModel and handled once this fragment has a
     * view (right away if it has one), so it can be made while the tab is hidden or still being shown. If the form has
     * unsaved changes, the user is asked first; for the memory already being edited, the changes are kept.
     */
    fun requestEditMode(memoryId: Int) {
        viewModel.pendingEditMemoryId = memoryId
        loadPendingEdit()
    }

    /** Handles the pending edit request, if any. It stays pending until handled, so a view destroyed meanwhile retries it. */
    private fun loadPendingEdit() {
        val memoryId = viewModel.pendingEditMemoryId ?: return
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        when {
            !viewModel.hasUnsavedChanges() -> loadForEditing(memoryId, viewLifecycleOwner)
            memoryId == viewModel.editingMemoryId -> viewModel.pendingEditMemoryId = null
            else -> confirmDiscardForEdit(memoryId)
        }
    }

    private fun loadForEditing(memoryId: Int, viewLifecycleOwner: LifecycleOwner) {
        viewLifecycleOwner.lifecycleScope.launch {
            val groupWithMedia = viewModel.loadForEditing(memoryId)
            if (viewModel.pendingEditMemoryId != memoryId) return@launch // A newer request replaced this one.
            viewModel.pendingEditMemoryId = null
            // Null if the memory was deleted meanwhile: then the form stays as it is.
            groupWithMedia?.let {
                viewModel.fillForEditing(it)
                viewModel.markUnchanged()
            }
        }
    }

    /**
     * Asks whether the edit request may replace the unsaved changes. The request stays pending while the question is
     * shown, and the question comes back after a rotation or process death. A recreated view asks it again, replacing
     * the open one.
     */
    private fun confirmDiscardForEdit(memoryId: Int) {
        if (isStateSaved) return
        val isEditing = viewModel.editingMemoryId != null
        ConfirmDialog.show(
            childFragmentManager,
            DISCARD_FOR_EDIT_REQUEST,
            title = getString(if (isEditing) R.string.discard_edit_title else R.string.discard_draft_title),
            message = getString(if (isEditing) R.string.discard_edit_message else R.string.discard_draft_message),
            confirm = getString(R.string.discard),
            cancel = getString(R.string.keep_editing),
            extras = bundleOf(MEMORY_ID to memoryId)
        )
    }

    private fun dropPendingEdit(memoryId: Int) {
        if (viewModel.pendingEditMemoryId == memoryId) viewModel.pendingEditMemoryId = null
    }

    /** The URIs of the media in the form (a draft or an edit), which a save may still store. */
    fun mediaUrisInForm(): Set<String> {
        if (!isAdded) return emptySet() // A detached fragment has no ViewModel to read.
        return viewModel.selectedMedia.mapTo(HashSet()) { it.uri.toString() }
    }

    /** Called when another tab is selected: an edit without changes is closed, so this tab offers a new memory next time. */
    fun closeUnchangedEdit() {
        if (viewModel.editingMemoryId == null || viewModel.hasUnsavedChanges()) return
        viewModel.resetForm()
    }

    companion object {
        const val TAG = "AddMemoryGroupFragment"
        private const val CLEAR_REQUEST = "clear_form"
        private const val DISCARD_FOR_EDIT_REQUEST = "discard_for_edit"
        private const val MEMORY_ID = "memory_id"
    }
}
