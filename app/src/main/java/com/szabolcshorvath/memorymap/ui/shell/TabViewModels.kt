package com.szabolcshorvath.memorymap.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragmentViewModel
import com.szabolcshorvath.memorymap.fragment.MapFragmentViewModel
import com.szabolcshorvath.memorymap.fragment.SettingsFragmentViewModel
import com.szabolcshorvath.memorymap.fragment.TimelineFragmentViewModel

/**
 * The tabs' ViewModels, kept by the activity: a tab lives as long as the app's screen, as the tab fragments did, and
 * the shell hands it requests from other screens (show a memory on the map, edit one) whether or not it is shown. Each
 * is created the first time it is needed.
 */
class TabViewModels(private val owner: ViewModelStoreOwner) {
    private val addOnce = lazy { get<AddMemoryGroupFragmentViewModel>(AddMemoryGroupFragmentViewModel.Factory) }

    val common by lazy { get<CommonViewModel>(CommonViewModel.Factory) }
    val map by lazy { get<MapFragmentViewModel>(MapFragmentViewModel.Factory) }
    val timeline by lazy { get<TimelineFragmentViewModel>(TimelineFragmentViewModel.Factory) }
    val add by addOnce
    val settings by lazy { get<SettingsFragmentViewModel>(SettingsFragmentViewModel.Factory) }

    /** The Add form's ViewModel if it exists already: asking it about a form nobody opened would create one. */
    val addIfCreated: AddMemoryGroupFragmentViewModel? get() = if (addOnce.isInitialized()) add else null

    private inline fun <reified T : ViewModel> get(factory: ViewModelProvider.Factory): T = ViewModelProvider(owner, factory)[T::class.java]
}
