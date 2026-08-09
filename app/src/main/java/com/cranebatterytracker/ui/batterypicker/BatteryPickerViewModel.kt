package com.cranebatterytracker.ui.batterypicker

import android.os.SystemClock
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
    val currentBatteryId: Int? = null,
    val currentBatteryDisplayNumber: Int? = null,
    val unavailableBatteryId: Int? = null,
    val unavailableBatteryDisplayNumber: Int? = null,
    val otherRemoteShortName: String? = null,
    val batteries: List<Battery> = emptyList(),
    val saved: SavedResult? = null,
    val submitting: Boolean = false,
    val errorMessage: String? = null
)

private data class LocalUiState(
    val saved: SavedResult? = null,
    val submitting: Boolean = false,
    val errorMessage: String? = null
)

class BatteryPickerViewModel(private val container: AppContainer, private val remoteId: RemoteId) : ViewModel() {

    private val localState = MutableStateFlow(LocalUiState())

    private val dataState = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes(),
        container.settingsRepository.settings
    ) { events, batteries, remotes, settings -> PickerData(events, batteries, remotes, settings) }

    private data class PickerData(
        val events: List<com.cranebatterytracker.domain.model.DomainEvent>,
        val batteries: List<Battery>,
        val remotes: List<com.cranebatterytracker.domain.model.Remote>,
        val settings: com.cranebatterytracker.data.settings.AppSettings
    )

    val uiState = combine(dataState, localState) { data, local ->
        val knowledge = EventReducer.reduce(data.events)
        val remote = data.remotes.firstOrNull { it.remoteId == remoteId }
        val otherRemoteId = remoteId.other()
        val otherRemote = data.remotes.firstOrNull { it.remoteId == otherRemoteId }
        val currentBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId
        val otherKnown = knowledge[otherRemoteId] as? RemoteKnowledge.Known

        BatteryPickerUiState(
            loading = false,
            remoteDisplayName = configuredDisplayName(remoteId, data.settings) ?: remote?.displayName ?: "",
            remoteShortName = remote?.shortName ?: "",
            currentBatteryId = currentBatteryId,
            currentBatteryDisplayNumber = currentBatteryId?.let { id -> data.batteries.firstOrNull { it.batteryId == id }?.displayNumber },
            unavailableBatteryId = otherKnown?.batteryId,
            unavailableBatteryDisplayNumber = otherKnown?.batteryId?.let { id -> data.batteries.firstOrNull { it.batteryId == id }?.displayNumber },
            otherRemoteShortName = otherRemote?.shortName,
            batteries = data.batteries,
            saved = local.saved,
            submitting = local.submitting,
            errorMessage = local.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryPickerUiState())

    private fun configuredDisplayName(remoteId: RemoteId, settings: com.cranebatterytracker.data.settings.AppSettings): String? {
        val name = if (remoteId == RemoteId.WEST) settings.westDisplayName else settings.eastDisplayName
        return name.ifBlank { null }
    }

    /**
     * A double-tap before the "SAVED" confirmation replaces the grid must not fire a
     * second transaction - that would remove and reinstall the just-selected battery,
     * corrupting its dead-event count and creating a spurious near-zero cycle.
     */
    fun selectBattery(batteryId: Int) {
        if (localState.value.submitting) return
        viewModelScope.launch {
            localState.value = localState.value.copy(submitting = true)
            val snapshot = uiState.value
            runCatching {
                container.batteryChangeUseCase(remoteId, batteryId, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
            }
                .onSuccess {
                    val toNumber = snapshot.batteries.firstOrNull { it.batteryId == batteryId }?.displayNumber ?: batteryId
                    localState.value = localState.value.copy(
                        submitting = false,
                        saved = SavedResult(snapshot.currentBatteryDisplayNumber, toNumber)
                    )
                }
                .onFailure { error ->
                    localState.value = localState.value.copy(submitting = false, errorMessage = error.message)
                }
        }
    }

    fun consumeSaved() {
        localState.value = localState.value.copy(saved = null)
    }

    fun consumeError() {
        localState.value = localState.value.copy(errorMessage = null)
    }
}
