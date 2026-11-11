package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.ui.add.AddMemoryRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/**
 * Hosts the Add tab ([AddMemoryRoute]) until the app's shell is Compose: the form for a new memory, or for editing one
 * ([requestEditMode]).
 */
class AddMemoryGroupFragment : Fragment() {

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

    override fun onDetach() {
        super.onDetach()
        addMemoryGroupListener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the form's scroll position, its open picker and an open question under this id.
            id = R.id.add_memory_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme {
                    AddMemoryRoute(
                        viewModel,
                        commonViewModel,
                        onPickLocation = { place ->
                            addMemoryGroupListener?.onPickLocation(place?.lat, place?.lng, place?.name, place?.address)
                        },
                        onSaved = { id -> addMemoryGroupListener?.onMemorySaved(id) },
                    )
                }
            }
        }

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
     * Opens memory [memoryId] for editing. The request is kept in the ViewModel and handled once the form has a view
     * (right away if it has one), so it can be made while the tab is hidden or still being shown. If the form has
     * unsaved changes, the user is asked first; for the memory already being edited, the changes are kept.
     */
    fun requestEditMode(memoryId: Int) = viewModel.requestEdit(memoryId)

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
    }
}
