package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.media.MediaViewerContent
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/** A memory's photos and videos, full screen ([MediaViewerContent]), over the memory they were opened from. */
class MediaViewerFragment : Fragment() {

    /** What the viewer needs from the activity: the whole screen while it shows, and a way back. */
    interface MediaViewerListener {
        /** Hides the tab bar and the system bars while the viewer shows ([shown]), and brings them back after. */
        fun onMediaViewerShown(shown: Boolean)
        fun onBackFromMediaViewer()
    }

    private val viewModel: MediaViewerViewModel by viewModels { MediaViewerViewModel.Factory }
    private var listener: MediaViewerListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? MediaViewerListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            id = R.id.media_viewer_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val items by viewModel.mediaItems.collectAsStateWithLifecycle()
                // Always dark, like the black viewer, so the video controls read on it.
                MemoryMapTheme(dark = true) {
                    MediaViewerContent(
                        items = items,
                        initialItemId = viewModel.currentItemId,
                        onCurrentItem = { viewModel.currentItemId = it },
                        onBack = { listener?.onBackFromMediaViewer() },
                    )
                }
            }
        }

    // While it is started: a recreated activity (rotation) starts it again, and the screen it goes back to doesn't.
    override fun onStart() {
        super.onStart()
        listener?.onMediaViewerShown(true)
    }

    override fun onStop() {
        super.onStop()
        listener?.onMediaViewerShown(false)
    }

    companion object {
        const val TAG = "MediaViewerFragment"

        /** Shows memory [memoryId]'s media, starting at item [mediaItemId]. */
        @JvmStatic
        fun newInstance(memoryId: Int, mediaItemId: Int) = MediaViewerFragment().apply {
            arguments = bundleOf(
                MediaViewerViewModel.ARG_MEMORY_ID to memoryId,
                MediaViewerViewModel.ARG_MEDIA_ITEM_ID to mediaItemId
            )
        }
    }
}
