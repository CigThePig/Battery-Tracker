package com.cranebatterytracker.ui.main

import android.os.SystemClock
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
    val busy: Boolean = false,
    val errorMessage: String? = null
)

private const val RECENT_ACTION_WINDOW_MILLIS = 6_000L
private const val TICK_INTERVAL_MILLIS = 1_000L

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val dismissedActionGroupId = MutableStateFlow<String?>(null)
    private val dismissedShiftBannerAt = MutableStateFlow<Long?>(null)
    private val transientError = MutableStateFlow<String?>(null)

    /**
     * Guards every mutating action (confirm/undo) against a double-tap firing two
     * independent transactions - e.g. two rapid taps on the permanent Undo control
     * would otherwise undo two separate action groups instead of one.
     */
    private val busy = MutableStateFlow(false)

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
            val seededRemote = remotesById[remoteId] ?: return null
            // The admin-configurable name in Settings, not the immutable seeded
            // RemoteEntity row, is the source of truth for what's displayed - otherwise
            // Settings -> Admin PIN -> remote names has no visible effect anywhere.
            val configuredName = if (remoteId == RemoteId.WEST) settings.westDisplayName else settings.eastDisplayName
            val remote = if (configuredName.isNotBlank()) seededRemote.copy(displayName = configuredName) else seededRemote
            val state = shiftEngine.toDisplayState(knowledge.getValue(remoteId), now)
            return RemoteCardUiState(
                remoteId = remoteId,
                remote = remote,
                state = state,
                batteryDisplayNumber = state.batteryIdOrNull?.let { batteriesById[it]?.displayNumber }
            )
        }

        val effective = EventFiltering.effectiveChronologicalEvents(events)
        // Insertion sequence, not wall-clock time, decides which group is "latest" - a
        // backward clock correction can give a freshly saved group a lower timestamp than
        // an older one, which would hide its undo banner entirely. The timestamp is still
        // used below, but only to decide how long to keep displaying the banner.
        val latestGroupEvents = effective
            .filter { it.actionGroupId != null }
            .groupBy { it.actionGroupId }
            .values
            .maxByOrNull { group -> group.maxOf { it.sequenceNumber } }

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
            busy = busy.value,
            errorMessage = transientError.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /** Runs [action] guarded so a double-tap while it's in flight is ignored outright. */
    private fun runGuarded(action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            runCatching { action() }.onFailure { transientError.value = it.message }
            busy.value = false
        }
    }

    fun confirmStale(remoteId: RemoteId) = runGuarded {
        container.confirmStateUseCase(remoteId, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
    }

    fun undoMostRecent() = runGuarded {
        container.undoUseCase(elapsedRealtimeMillis = SystemClock.elapsedRealtime())
    }

    fun undoActionGroup(actionGroupId: String) = runGuarded {
        container.undoUseCase(targetActionGroupId = actionGroupId, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
    }

    fun dismissRecentActionBanner(actionGroupId: String) {
        dismissedActionGroupId.value = actionGroupId
    }

    fun confirmBothAtShiftStart() {
        val westKnown = uiState.value.west?.state is RemoteState.Confirmed || uiState.value.west?.state is RemoteState.Stale
        val eastKnown = uiState.value.east?.state is RemoteState.Confirmed || uiState.value.east?.state is RemoteState.Stale
        if (!westKnown || !eastKnown) {
            // A partial confirmation must never be reported as "both correct" - that would
            // dismiss the banner and silently drop the still-unknown remote's prominent
            // correction prompt while implying it had already been handled.
            transientError.value = "Set the unknown remote's battery before confirming both are correct."
            return
        }
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            // Both confirmations are written as one action group so the permanent
            // Undo control reverses this single button press atomically.
            runCatching {
                container.confirmStateUseCase(RemoteId.WEST, RemoteId.EAST, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
            }.onFailure { transientError.value = it.message }
            dismissedShiftBannerAt.value = System.currentTimeMillis()
            busy.value = false
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
