package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.location.PickLocationRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/** Hosts the location picker ([PickLocationRoute]) until the app's shell is Compose. */
class PickLocationFragment : Fragment() {

    private val viewModel: PickLocationFragmentViewModel by viewModels()
    private var pickLocationListener: PickLocationListener? = null

    interface PickLocationListener {
        fun onLocationConfirmed(lat: Double, lng: Double, placeName: String?, address: String?)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is PickLocationListener) {
            pickLocationListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the map's camera under this id.
            id = R.id.pick_location_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme {
                    PickLocationRoute(
                        viewModel,
                        onClose = { activity?.onBackPressedDispatcher?.onBackPressed() },
                        onConfirm = { lat, lng, name, address -> pickLocationListener?.onLocationConfirmed(lat, lng, name, address) },
                    )
                }
            }
        }

    /** Opens the picker on a spot picked before. */
    fun setInitialLocation(lat: Double, lng: Double, placeName: String?, address: String?) =
        viewModel.setInitialLocation(lat, lng, placeName, address)

    /** Opens the picker with nothing picked. */
    fun clearSelection() = viewModel.clearSelection()

    override fun onDetach() {
        super.onDetach()
        pickLocationListener = null
    }

    companion object {
        const val TAG = "PickLocationFragment"
    }
}
