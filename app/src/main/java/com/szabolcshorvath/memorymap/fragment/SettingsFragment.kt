package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.settings.SettingsRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/** Hosts the Settings tab ([SettingsRoute]) until the app's shell is Compose. */
class SettingsFragment : Fragment() {
    private val viewModel: SettingsFragmentViewModel by viewModels { SettingsFragmentViewModel.Factory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose keeps its saved state (the scroll position, an open question) under the view's id.
            id = R.id.settings_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme { SettingsRoute(viewModel) }
            }
        }

    companion object {
        const val TAG = "SettingsFragment"
    }
}
