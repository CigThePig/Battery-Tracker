package com.cranebatterytracker.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Small explicit factory so ViewModels can take plain constructor parameters without a DI framework. */
class SimpleViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
