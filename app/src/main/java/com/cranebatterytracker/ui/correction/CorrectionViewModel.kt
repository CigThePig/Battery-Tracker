package com.cranebatterytracker.ui.correction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cranebatterytracker.di.AppContainer
import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.usecase.BatteryTrackerException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollisionPrompt(val batteryId: Int, val batteryDisplayNumber: Int, val otherRemoteShortName: String)

data class CorrectionUiState(
    val loading: Boolean = true,
    val remoteDisplayName: String = "",
    val currentBatteryDisplayNumber: Int? = null,
    val batteries: List<Battery> = emptyList(),
    val pendingCollision: CollisionPrompt? = null,
    val done: Boolean = false,
    val errorMessage: String? = null
)

private data class LocalUiState(
    val pendingCollision: CollisionPrompt? = null,
    val done: Boolean = false,
    val errorMessage: String? = null
)

class CorrectionViewModel(private val container: AppContainer, private val remoteId: RemoteId) : ViewModel() {

    private val localState = MutableStateFlow(LocalUiState())
    private var remotesCache: List<Remote> = emptyList()

    private val dataState = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes()
    ) { events, batteries, remotes -> Triple(events, batteries, remotes) }

    init {
        dataState.onEach { (_, _, remotes) -> remotesCache = remotes }.launchIn(viewModelScope)
    }

    val uiState = combine(dataState, localState) { (events, batteries, remotes), local ->
        val knowledge = EventReducer.reduce(events)
        val remote = remotes.firstOrNull { it.remoteId == remoteId }
        val currentBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId

        CorrectionUiState(
            loading = false,
            remoteDisplayName = remote?.displayName ?: "",
            currentBatteryDisplayNumber = currentBatteryId?.let { id -> batteries.firstOrNull { it.batteryId == id }?.displayNumber },
            batteries = batteries,
            pendingCollision = local.pendingCollision,
            done = local.done,
            errorMessage = local.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CorrectionUiState())

    fun selectBattery(batteryId: Int) {
        viewModelScope.launch {
            runCatching { container.correctStateUseCase(remoteId, batteryId) }
                .onSuccess { localState.value = localState.value.copy(done = true) }
                .onFailure { error ->
                    if (error is BatteryTrackerException.BatteryOwnedByOtherRemote) {
                        val otherShortName = remotesCache.firstOrNull { it.remoteId == error.otherRemote }?.shortName
                            ?: error.otherRemote.name
                        localState.value = localState.value.copy(
                            pendingCollision = CollisionPrompt(
                                batteryId = batteryId,
                                batteryDisplayNumber = error.batteryDisplayNumber,
                                otherRemoteShortName = otherShortName
                            )
                        )
                    } else {
                        localState.value = localState.value.copy(errorMessage = error.message)
                    }
                }
        }
    }

    fun confirmCollision() {
        val prompt = localState.value.pendingCollision ?: return
        viewModelScope.launch {
            runCatching { container.correctStateUseCase(remoteId, prompt.batteryId, resolveCollision = true) }
                .onSuccess { localState.value = localState.value.copy(pendingCollision = null, done = true) }
                .onFailure { localState.value = localState.value.copy(errorMessage = it.message) }
        }
    }

    fun cancelCollision() {
        localState.value = localState.value.copy(pendingCollision = null)
    }

    fun markUnknown() {
        viewModelScope.launch {
            runCatching { container.markUnknownUseCase(remoteId) }
                .onSuccess { localState.value = localState.value.copy(done = true) }
                .onFailure { localState.value = localState.value.copy(errorMessage = it.message) }
        }
    }

    fun consumeError() {
        localState.value = localState.value.copy(errorMessage = null)
    }
}
