package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.ViewModel

class MemoryPagerFragmentViewModel : ViewModel() {
    var isInitialSetupDone = false
    var memoryIds: List<Int> = emptyList()
}
