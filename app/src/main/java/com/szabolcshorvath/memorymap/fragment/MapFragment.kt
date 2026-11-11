package com.szabolcshorvath.memorymap.fragment

import android.content.Context
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
import com.szabolcshorvath.memorymap.ui.map.MapRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/** Hosts the Map tab ([MapRoute]) until the app's shell is Compose. */
class MapFragment : Fragment() {

    private var mapListener: MapListener? = null

    private val commonViewModel: CommonViewModel by activityViewModels { CommonViewModel.Factory }
    private val viewModel: MapFragmentViewModel by viewModels { MapFragmentViewModel.Factory }

    interface MapListener {
        fun onMemoryClicked(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapListener) {
            mapListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the map's camera and an open date picker under this id.
            id = R.id.map_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme {
                    MapRoute(viewModel, commonViewModel, onOpenMemory = { id -> mapListener?.onMemoryClicked(id) })
                }
            }
        }

    /** The Map tab tapped while the map shows (see [MapFragmentViewModel.onTabTappedAgain]). */
    fun onTabTappedAgain() = viewModel.onTabTappedAgain()

    /** Shows memory [id] on the map once it is shown (see [MapFragmentViewModel.requestFocusOnMemory]). */
    fun requestFocusOnMemory(lat: Double, lng: Double, id: Int) = viewModel.requestFocusOnMemory(lat, lng, id)

    override fun onDetach() {
        super.onDetach()
        mapListener = null
    }

    companion object {
        const val TAG = "MapFragment"
    }
}
