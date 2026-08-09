package com.cranebatterytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cranebatterytracker.data.settings.AppSettings
import com.cranebatterytracker.di.AppContainer
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun updateShiftTimes(
        dayStart: LocalTime,
        dayAmbiguousStart: LocalTime,
        dayAmbiguousEnd: LocalTime,
        nightStart: LocalTime,
        nightAmbiguousStart: LocalTime,
        nightAmbiguousEnd: LocalTime
    ) {
        viewModelScope.launch {
            container.settingsRepository.updateShiftTimes(
                dayStart, dayAmbiguousStart, dayAmbiguousEnd, nightStart, nightAmbiguousStart, nightAmbiguousEnd
            )
        }
    }

    fun updateWorkingDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { container.settingsRepository.updateWorkingDays(days) }
    }

    fun updateAdminPin(pin: String?) {
        viewModelScope.launch { container.settingsRepository.updateAdminPin(pin) }
    }

    fun updateBackupRetention(dailyCount: Int, archiveCount: Int) {
        viewModelScope.launch { container.settingsRepository.updateBackupRetention(dailyCount, archiveCount) }
    }

    fun updateRemoteDisplayNames(west: String, east: String) {
        viewModelScope.launch { container.settingsRepository.updateRemoteDisplayNames(west, east) }
    }
}
