package com.cranebatterytracker.ui.batterypicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cranebatterytracker.di.AppContainer
import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.model.other
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedResult(val fromDisplayNumber: Int?, val toDisplayNumber: Int)

data class BatteryPickerUiState(
    val loading: Boolean = true,
    val remoteDisplayName: String = "",
    val remoteShortName: String = "",
    val currentBatteryDisplayNumber: Int? = null,
    val unavailableBatteryId: Int? = null,
    val unavailableBatteryDisplayNumber: Int? = null,
    val otherRemoteShortName: String? = null,
    val batteries: List<Battery> = emptyList(),
    val saved: SavedResult? = null,
    val errorMessage: String? = null
)

class BatteryPickerViewModel(private val container: AppContainer, private val remoteId: RemoteId) : ViewModel() {

    private val savedResult = MutableStateFlow<SavedResult?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes(),
        savedResult,
        errorMessage
    ) { events, batteries, remotes, saved, error ->
        val knowledge = EventReducer.reduce(events)
        val remote = remotes.firstOrNull { it.remoteId == remoteId }
        val otherRemoteId = remoteId.other()
        val otherRemote = remotes.firstOrNull { it.remoteId == otherRemoteId }
        val currentBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId
        val otherKnown = knowledge[otherRemoteId] as? RemoteKnowledge.Known

        BatteryPickerUiState(
            loading = false,
            remoteDisplayName = remote?.displayName ?: "",
            remoteShortName = remote?.shortName ?: "",
            currentBatteryDisplayNumber = currentBatteryId?.let { id -> batteries.firstOrNull { it.batteryId == id }?.displayNumber },
            unavailableBatteryId = otherKnown?.batteryId,
            unavailableBatteryDisplayNumber = otherKnown?.batteryId?.let { id -> batteries.firstOrNull { it.batteryId == id }?.displayNumber },
            otherRemoteShortName = otherRemote?.shortName,
            batteries = batteries,
            saved = saved,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryPickerUiState())

    fun selectBattery(batteryId: Int) {
        viewModelScope.launch {
            val snapshot = uiState.value
            runCatching { container.batteryChangeUseCase(remoteId, batteryId) }
                .onSuccess {
                    val toNumber = snapshot.batteries.firstOrNull { it.batteryId == batteryId }?.displayNumber ?: batteryId
                    savedResult.value = SavedResult(snapshot.currentBatteryDisplayNumber, toNumber)
                }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun consumeSaved() {
        savedResult.value = null
    }

    fun consumeError() {
        errorMessage.value = null
    }
}
