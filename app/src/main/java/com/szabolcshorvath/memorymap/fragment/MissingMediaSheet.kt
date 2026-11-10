package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.fragment.MissingMediaSheetViewModel.Outcome
import com.szabolcshorvath.memorymap.fragment.MissingMediaSheetViewModel.State
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaActions
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaSheetContent
import com.szabolcshorvath.memorymap.ui.memory.whenTaken
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.NearbyFile
import com.szabolcshorvath.memorymap.util.PermissionUtil
import kotlinx.coroutines.launch

/**
 * For a photo or video of a memory that can't be re-linked: looks for it once more, then suggests what was taken
 * around the same time, or lets the user choose another photo or remove it ([MissingMediaSheetContent]). Shown in the
 * memory screen's `childFragmentManager`; the item's id travels in its arguments, so it comes back after a rotation or
 * process death.
 */
class MissingMediaSheet : BottomSheetDialogFragment(), MissingMediaActions {
    private val viewModel: MissingMediaSheetViewModel by viewModels { MissingMediaSheetViewModel.Factory }
    private val itemId get() = requireArguments().getInt(ITEM_ID)

    private val accessLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onAccessChanged(itemId)
    }

    private val pickLauncher = registerForActivityResult(AppServices.mediaPicker) { uris ->
        uris.firstOrNull()?.let(viewModel::choose)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfirmDialog.listen(this, USE_REQUEST) { extras -> extras.getString(URI)?.let(viewModel::use) }
        ConfirmDialog.listen(this, REMOVE_REQUEST) { viewModel.remove() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            id = R.id.missing_media_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MemoryMapTheme {
                    MissingMediaSheetContent(state, viewModel.item, viewModel.thumbnail, this@MissingMediaSheet)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        viewModel.start(itemId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::onState) }
                launch { viewModel.outcomes.collect(::showOutcome) }
            }
        }
    }

    /** Closes the sheet once the photo is found again, or replaced or removed. */
    private fun onState(state: State) {
        when (state) {
            State.Found -> {
                Toast.makeText(requireContext(), R.string.missing_media_found, Toast.LENGTH_SHORT).show()
                dismiss()
            }
            State.Done -> dismiss()
            else -> Unit
        }
    }

    private fun showOutcome(outcome: Outcome) {
        val text = when (outcome) {
            Outcome.CHANGED -> R.string.missing_media_changed
            Outcome.ALREADY_IN_MEMORY -> R.string.missing_media_already_in_memory
            Outcome.UNREADABLE -> R.string.missing_media_unreadable
        }
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
        if (outcome == Outcome.CHANGED) dismiss()
    }

    override fun allowAccess() = accessLauncher.launch(PermissionUtil.getRequiredMediaPermissions())

    override fun showMore() = viewModel.showMore()

    override fun chooseAnother() = pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    /** Asks before [file] replaces the missing photo. */
    override fun use(file: NearbyFile) {
        if (isStateSaved) return
        val taken = whenTaken(requireContext(), file.dateTaken - (viewModel.item?.dateTaken ?: file.dateTaken))
        ConfirmDialog.show(
            childFragmentManager,
            USE_REQUEST,
            title = getString(R.string.missing_media_use_title),
            message = getString(R.string.missing_media_use_message, taken),
            confirm = getString(R.string.missing_media_use),
            cancel = getString(R.string.cancel),
            extras = bundleOf(URI to file.uri)
        )
    }

    /** Asks before the missing photo is removed from its memory. */
    override fun remove() {
        if (isStateSaved) return
        ConfirmDialog.show(
            childFragmentManager,
            REMOVE_REQUEST,
            title = getString(R.string.missing_media_remove_title),
            message = getString(R.string.missing_media_remove_message),
            confirm = getString(R.string.missing_media_remove_confirm),
            cancel = getString(R.string.cancel)
        )
    }

    companion object {
        const val TAG = "missing_media_sheet"
        private const val ITEM_ID = "item_id"
        private const val URI = "uri"
        private const val USE_REQUEST = "use_nearby_media"
        private const val REMOVE_REQUEST = "remove_missing_media"

        /** Shows the sheet for [item] in [manager], unless one is already open. */
        fun show(manager: FragmentManager, item: MediaItem) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            MissingMediaSheet().apply { arguments = bundleOf(ITEM_ID to item.id) }.show(manager, TAG)
        }
    }
}
