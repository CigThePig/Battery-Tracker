package com.cranebatterytracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cranebatterytracker.di.AppContainer
import com.cranebatterytracker.domain.analysis.EventFiltering
import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteState
import com.cranebatterytracker.domain.model.batteryIdOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RemoteCardUiState(
    val remoteId: RemoteId,
    val remote: Remote,
    val state: RemoteState,
    val batteryDisplayNumber: Int?
)

data class ShiftBannerUiState(
    val westBatteryDisplayNumber: Int?,
    val eastBatteryDisplayNumber: Int?
)

data class MainUiState(
    val loading: Boolean = true,
    val west: RemoteCardUiState? = null,
    val east: RemoteCardUiState? = null,
    val shiftBanner: ShiftBannerUiState? = null,
    val recentActionMessage: String? = null,
    val recentActionGroupId: String? = null,
    val errorMessage: String? = null
)

private const val RECENT_ACTION_WINDOW_MILLIS = 6_000L
private const val TICK_INTERVAL_MILLIS = 1_000L

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val dismissedActionGroupId = MutableStateFlow<String?>(null)
    private val dismissedShiftBannerAt = MutableStateFlow<Long?>(null)
    private val transientError = MutableStateFlow<String?>(null)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    val uiState = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes(),
        container.settingsRepository.settings,
        ticker
    ) { events, batteries, remotes, settings, now ->
        val shiftEngine = container.shiftEngine(settings)
        val knowledge = EventReducer.reduce(events)
        val batteriesById = batteries.associateBy(Battery::batteryId)
        val remotesById = remotes.associateBy(Remote::remoteId)

        fun cardFor(remoteId: RemoteId): RemoteCardUiState? {
            val remote = remotesById[remoteId] ?: return null
            val state = shiftEngine.toDisplayState(knowledge.getValue(remoteId), now)
            return RemoteCardUiState(
                remoteId = remoteId,
                remote = remote,
                state = state,
                batteryDisplayNumber = state.batteryIdOrNull?.let { batteriesById[it]?.displayNumber }
            )
        }

        val effective = EventFiltering.effectiveChronologicalEvents(events)
        val latestGroupEvents = effective
            .filter { it.actionGroupId != null }
            .groupBy { it.actionGroupId }
            .values
            .maxByOrNull { group -> group.maxOf { it.timestampEpochMillis } }

        val recentGroupId = latestGroupEvents?.firstOrNull()?.actionGroupId
        val recentTimestamp = latestGroupEvents?.maxOfOrNull { it.timestampEpochMillis }
        val isRecent = recentTimestamp != null && (now - recentTimestamp) in 0..RECENT_ACTION_WINDOW_MILLIS
        val recentMessage = if (isRecent && recentGroupId != dismissedActionGroupId.value) {
            latestGroupEvents?.let { buildActionSummary(it, batteries, remotesById) }
        } else {
            null
        }

        val westCard = cardFor(RemoteId.WEST)
        val eastCard = cardFor(RemoteId.EAST)

        val bannerDismissedRecently = dismissedShiftBannerAt.value?.let { (now - it) < 20 * 60_000L } ?: false
        val shiftBanner = if (shiftEngine.isNearShiftStart(now) && !bannerDismissedRecently) {
            ShiftBannerUiState(
                westBatteryDisplayNumber = westCard?.batteryDisplayNumber,
                eastBatteryDisplayNumber = eastCard?.batteryDisplayNumber
            )
        } else {
            null
        }

        MainUiState(
            loading = false,
            west = westCard,
            east = eastCard,
            shiftBanner = shiftBanner,
            recentActionMessage = recentMessage,
            recentActionGroupId = if (recentMessage != null) recentGroupId else null,
            errorMessage = transientError.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun confirmStale(remoteId: RemoteId) {
        viewModelScope.launch {
            runCatching { container.confirmStateUseCase(remoteId) }
                .onFailure { transientError.value = it.message }
        }
    }

    fun undoMostRecent() {
        viewModelScope.launch {
            runCatching { container.undoUseCase() }
                .onFailure { transientError.value = it.message }
        }
    }

    fun undoActionGroup(actionGroupId: String) {
        viewModelScope.launch {
            runCatching { container.undoUseCase(targetActionGroupId = actionGroupId) }
                .onFailure { transientError.value = it.message }
        }
    }

    fun dismissRecentActionBanner(actionGroupId: String) {
        dismissedActionGroupId.value = actionGroupId
    }

    fun confirmBothAtShiftStart() {
        viewModelScope.launch {
            val westKnown = uiState.value.west?.state is RemoteState.Confirmed || uiState.value.west?.state is RemoteState.Stale
            val eastKnown = uiState.value.east?.state is RemoteState.Confirmed || uiState.value.east?.state is RemoteState.Stale
            if (westKnown) runCatching { container.confirmStateUseCase(RemoteId.WEST) }
            if (eastKnown) runCatching { container.confirmStateUseCase(RemoteId.EAST) }
            dismissedShiftBannerAt.value = System.currentTimeMillis()
        }
    }

    fun dismissShiftBanner() {
        dismissedShiftBannerAt.value = System.currentTimeMillis()
    }

    fun consumeError() {
        transientError.value = null
    }

    private fun buildActionSummary(
        groupEvents: List<DomainEvent>,
        batteries: List<Battery>,
        remotesById: Map<RemoteId, Remote>
    ): String? {
        val batteriesById = batteries.associateBy(Battery::batteryId)
        fun label(batteryId: Int?) = batteryId?.let { batteriesById[it]?.displayNumber } ?: "?"

        val installed = groupEvents.firstOrNull { it.eventType == EventType.BATTERY_INSTALLED }
        val removed = groupEvents.firstOrNull { it.eventType == EventType.BATTERY_REMOVED_DEAD }
        val corrected = groupEvents.firstOrNull { it.eventType == EventType.STATE_CORRECTED }
        val confirmed = groupEvents.firstOrNull { it.eventType == EventType.STATE_CONFIRMED }
        val markedUnknown = groupEvents.firstOrNull { it.eventType == EventType.STATE_MARKED_UNKNOWN }

        return when {
            installed != null -> {
                val shortName = remotesById[installed.remoteId]?.shortName ?: return null
                if (removed != null) {
                    "$shortName ${label(removed.batteryId)} → ${label(installed.batteryId)} SAVED"
                } else {
                    "$shortName ${label(installed.batteryId)} SET"
                }
            }

            corrected != null -> {
                val shortName = remotesById[corrected.remoteId]?.shortName ?: return null
                "$shortName corrected to ${label(corrected.newBatteryId)}"
            }

            confirmed != null -> {
                val shortName = remotesById[confirmed.remoteId]?.shortName ?: return null
                "$shortName ${label(confirmed.batteryId)} confirmed"
            }

            markedUnknown != null -> {
                val shortName = remotesById[markedUnknown.remoteId]?.shortName ?: return null
                "$shortName marked unknown"
            }

            else -> null
        }
    }
}
