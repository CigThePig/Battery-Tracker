package com.cranebatterytracker.ui.correction

import android.os.SystemClock
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

enum class CorrectionFeedbackKind { CONFIRMED, CORRECTED, MARKED_UNKNOWN, COLLISION_RESOLVED }

data class CorrectionFeedback(
    val kind: CorrectionFeedbackKind,
    val remoteId: RemoteId,
    val remoteDisplayName: String,
    val previousBatteryDisplayNumber: Int?,
    val newBatteryDisplayNumber: Int? = null,
    val otherRemoteShortName: String? = null
)

data class CorrectionUiState(
    val loading: Boolean = true,
    val remoteDisplayName: String = "",
    val currentBatteryDisplayNumber: Int? = null,
    val batteries: List<Battery> = emptyList(),
    val pendingCollision: CollisionPrompt? = null,
    val submitting: Boolean = false,
    val feedback: CorrectionFeedback? = null,
    val errorMessage: String? = null
)

private data class LocalUiState(
    val pendingCollision: CollisionPrompt? = null,
    val submitting: Boolean = false,
    val feedback: CorrectionFeedback? = null,
    val errorMessage: String? = null
)

class CorrectionViewModel(private val container: AppContainer, private val remoteId: RemoteId) : ViewModel() {

    private val localState = MutableStateFlow(LocalUiState())
    private var remotesCache: List<Remote> = emptyList()

    private data class CorrectionData(
        val events: List<com.cranebatterytracker.domain.model.DomainEvent>,
        val batteries: List<Battery>,
        val remotes: List<Remote>,
        val settings: com.cranebatterytracker.data.settings.AppSettings
    )

    private val dataState = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes(),
        container.settingsRepository.settings
    ) { events, batteries, remotes, settings -> CorrectionData(events, batteries, remotes, settings) }

    init {
        dataState.onEach { remotesCache = it.remotes }.launchIn(viewModelScope)
    }

    val uiState = combine(dataState, localState) { data, local ->
        val knowledge = EventReducer.reduce(data.events)
        val remote = data.remotes.firstOrNull { it.remoteId == remoteId }
        val configuredName = (if (remoteId == RemoteId.WEST) data.settings.westDisplayName else data.settings.eastDisplayName)
            .ifBlank { null }
        val currentBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId

        CorrectionUiState(
            loading = false,
            remoteDisplayName = configuredName ?: remote?.displayName ?: "",
            currentBatteryDisplayNumber = currentBatteryId?.let { id -> data.batteries.firstOrNull { it.batteryId == id }?.displayNumber },
            batteries = data.batteries,
            pendingCollision = local.pendingCollision,
            submitting = local.submitting,
            feedback = local.feedback,
            errorMessage = local.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CorrectionUiState())

    fun selectBattery(batteryId: Int) {
        if (localState.value.submitting) return
        viewModelScope.launch {
            localState.value = localState.value.copy(submitting = true)
            val snapshot = uiState.value
            runCatching {
                container.correctStateUseCase(remoteId, batteryId, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
            }
                .onSuccess {
                    val newNumber = snapshot.batteries.firstOrNull { it.batteryId == batteryId }?.displayNumber ?: batteryId
                    localState.value = localState.value.copy(
                        submitting = false,
                        feedback = CorrectionFeedback(
                            kind = if (snapshot.currentBatteryDisplayNumber == newNumber) {
                                CorrectionFeedbackKind.CONFIRMED
                            } else {
                                CorrectionFeedbackKind.CORRECTED
                            },
                            remoteId = remoteId,
                            remoteDisplayName = snapshot.remoteDisplayName,
                            previousBatteryDisplayNumber = snapshot.currentBatteryDisplayNumber,
                            newBatteryDisplayNumber = newNumber
                        )
                    )
                }
                .onFailure { error ->
                    if (error is BatteryTrackerException.BatteryOwnedByOtherRemote) {
                        val otherShortName = remotesCache.firstOrNull { it.remoteId == error.otherRemote }?.shortName
                            ?: error.otherRemote.name
                        localState.value = localState.value.copy(
                            submitting = false,
                            pendingCollision = CollisionPrompt(
                                batteryId = batteryId,
                                batteryDisplayNumber = error.batteryDisplayNumber,
                                otherRemoteShortName = otherShortName
                            )
                        )
                    } else {
                        localState.value = localState.value.copy(submitting = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun confirmCollision() {
        val prompt = localState.value.pendingCollision ?: return
        if (localState.value.submitting) return
        viewModelScope.launch {
            localState.value = localState.value.copy(submitting = true)
            val snapshot = uiState.value
            runCatching {
                container.correctStateUseCase(
                    remoteId,
                    prompt.batteryId,
                    elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                    resolveCollision = true
                )
            }
                .onSuccess {
                    localState.value = localState.value.copy(
                        submitting = false,
                        pendingCollision = null,
                        feedback = CorrectionFeedback(
                            kind = CorrectionFeedbackKind.COLLISION_RESOLVED,
                            remoteId = remoteId,
                            remoteDisplayName = snapshot.remoteDisplayName,
                            previousBatteryDisplayNumber = snapshot.currentBatteryDisplayNumber,
                            newBatteryDisplayNumber = prompt.batteryDisplayNumber,
                            otherRemoteShortName = prompt.otherRemoteShortName
                        )
                    )
                }
                .onFailure { localState.value = localState.value.copy(submitting = false, errorMessage = it.message) }
        }
    }

    fun cancelCollision() {
        localState.value = localState.value.copy(pendingCollision = null)
    }

    fun markUnknown() {
        if (localState.value.submitting) return
        viewModelScope.launch {
            localState.value = localState.value.copy(submitting = true)
            val snapshot = uiState.value
            runCatching {
                container.markUnknownUseCase(remoteId, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
            }
                .onSuccess {
                    localState.value = localState.value.copy(
                        submitting = false,
                        feedback = CorrectionFeedback(
                            kind = CorrectionFeedbackKind.MARKED_UNKNOWN,
                            remoteId = remoteId,
                            remoteDisplayName = snapshot.remoteDisplayName,
                            previousBatteryDisplayNumber = snapshot.currentBatteryDisplayNumber
                        )
                    )
                }
                .onFailure { localState.value = localState.value.copy(submitting = false, errorMessage = it.message) }
        }
    }

    fun consumeError() {
        localState.value = localState.value.copy(errorMessage = null)
    }
}
