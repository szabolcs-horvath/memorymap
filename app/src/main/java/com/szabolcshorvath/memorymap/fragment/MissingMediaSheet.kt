package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaSheetRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/**
 * Hosts the missing-photo sheet ([MissingMediaSheetRoute]) until the app's shell is Compose. Shown in the memory
 * screen's `childFragmentManager`; the item's id travels in its arguments, so it comes back after a rotation or process
 * death.
 */
class MissingMediaSheet : BottomSheetDialogFragment() {
    private val viewModel: MissingMediaSheetViewModel by viewModels { MissingMediaSheetViewModel.Factory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            id = R.id.missing_media_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme {
                    MissingMediaSheetRoute(viewModel, requireArguments().getInt(ITEM_ID), onDismiss = ::close)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun close() {
        dismissAllowingStateLoss()
    }

    companion object {
        const val TAG = "missing_media_sheet"
        private const val ITEM_ID = "item_id"

        /** Shows the sheet for [item] in [manager], unless one is already open. */
        fun show(manager: FragmentManager, item: MediaItem) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            MissingMediaSheet().apply { arguments = bundleOf(ITEM_ID to item.id) }.show(manager, TAG)
        }
    }
}
