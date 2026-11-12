package com.szabolcshorvath.memorymap.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.ui.add.AddMemoryViewModel
import com.szabolcshorvath.memorymap.ui.map.MapViewModel
import com.szabolcshorvath.memorymap.ui.settings.SettingsViewModel
import com.szabolcshorvath.memorymap.ui.timeline.TimelineViewModel

/**
 * The tabs' ViewModels, kept by the activity: a tab lives as long as the app's screen, and the shell hands it requests
 * from other screens (show a memory on the map, edit one) whether or not it is shown. Each is created the first time
 * it is needed.
 */
class TabViewModels(private val owner: ViewModelStoreOwner) {
    private val addOnce = lazy { get<AddMemoryViewModel>(AddMemoryViewModel.Factory) }

    val common by lazy { get<CommonViewModel>(CommonViewModel.Factory) }
    val map by lazy { get<MapViewModel>(MapViewModel.Factory) }
    val timeline by lazy { get<TimelineViewModel>(TimelineViewModel.Factory) }
    val add by addOnce
    val settings by lazy { get<SettingsViewModel>(SettingsViewModel.Factory) }

    /** The Add form's ViewModel if it exists already: asking it about a form nobody opened would create one. */
    val addIfCreated: AddMemoryViewModel? get() = if (addOnce.isInitialized()) add else null

    private inline fun <reified T : ViewModel> get(factory: ViewModelProvider.Factory): T = ViewModelProvider(owner, factory)[T::class.java]
}
