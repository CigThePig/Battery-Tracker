package com.cranebatterytracker.ui.main

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cranebatterytracker.di.AppContainer
import com.cranebatterytracker.domain.analysis.EventFiltering
import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.analysis.ShiftEngine
import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.model.RemoteState
import com.cranebatterytracker.domain.model.ShiftActivityLevel
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
    val batteryDisplayNumber: Int?,
    val activeShiftTracking: Boolean
)

data class EvidenceProgressUiState(
    val exactCycleCount: Int,
    val usefulObservationCount: Int,
    val qualityLevel: DataQualityLevel,
    val nextExactCycleMilestone: Int?
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
    val evidenceProgress: EvidenceProgressUiState? = null,
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

    /**
     * Everything derivable from persisted data alone (spec review Issue 15): reducing the
     * full event log, filtering out undone groups, and finding the latest action group are
     * all O(event count) and only need to happen when events/batteries/remotes/settings
     * actually change - not once a second forever. A permanent event log can eventually
     * hold tens of thousands of rows, and none of that work depends on wall-clock time.
     */
    private data class EventDerivedState(
        val knowledge: Map<RemoteId, RemoteKnowledge>,
        val batteriesById: Map<Int, Battery>,
        val remotesById: Map<RemoteId, Remote>,
        val shiftEngine: ShiftEngine,
        val recentGroupId: String?,
        val recentTimestamp: Long?,
        val recentSummary: String?,
        val evidenceProgress: EvidenceProgressUiState,
        val openIntervalClockAnomalies: Map<RemoteId, Boolean>
    )

    private val eventDerivedState = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes(),
        container.settingsRepository.settings
    ) { events, batteries, remotes, settings ->
        val shiftEngine = container.shiftEngine(settings)
        val runtimeAnalysisEngine = container.runtimeAnalysisEngine(settings)
        val cycles = runtimeAnalysisEngine.deriveCycles(events)
        val openIntervalClockAnomalies = runtimeAnalysisEngine.openIntervalClockAnomalies(events)
        val correctionCount = container.diagnosticEngine.correctionCount(
            EventFiltering.effectiveChronologicalEvents(events)
        )
        val quality = container.diagnosticEngine.dataQuality(cycles, correctionCount)
        val knowledge = EventReducer.reduce(events)
        val remotesById = remotes.associateBy(Remote::remoteId) { remote ->
            // The admin-configurable name in Settings, not the immutable seeded
            // RemoteEntity row, is the source of truth for what's displayed - otherwise
            // Settings -> Admin PIN -> remote names has no visible effect anywhere.
            val configuredName = if (remote.remoteId == RemoteId.WEST) settings.westDisplayName else settings.eastDisplayName
            if (configuredName.isNotBlank()) remote.copy(displayName = configuredName) else remote
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

        EventDerivedState(
            knowledge = knowledge,
            batteriesById = batteries.associateBy(Battery::batteryId),
            remotesById = remotesById,
            shiftEngine = shiftEngine,
            recentGroupId = latestGroupEvents?.firstOrNull()?.actionGroupId,
            recentTimestamp = latestGroupEvents?.maxOfOrNull { it.timestampEpochMillis },
            recentSummary = latestGroupEvents?.let { buildActionSummary(it, batteries, remotesById) },
            evidenceProgress = EvidenceProgressUiState(
                exactCycleCount = quality.exactCycles,
                usefulObservationCount = (quality.shiftInterruptedCycles - quality.clockAnomalyShiftInterruptedCycles) +
                    quality.confirmedMinimumObservations,
                qualityLevel = quality.overallLevel,
                nextExactCycleMilestone = nextEvidenceMilestone(quality.exactCycles)
            ),
            openIntervalClockAnomalies = openIntervalClockAnomalies
        )
    }

    /** Cheap, purely time/UI-dependent presentation built from the cached [EventDerivedState] on every tick. */
    val uiState = combine(eventDerivedState, ticker) { data, now ->
        fun cardFor(remoteId: RemoteId): RemoteCardUiState? {
            val remote = data.remotesById[remoteId] ?: return null
            val state = data.shiftEngine.toDisplayState(data.knowledge.getValue(remoteId), now)
            return RemoteCardUiState(
                remoteId = remoteId,
                remote = remote,
                state = state,
                batteryDisplayNumber = state.batteryIdOrNull?.let { data.batteriesById[it]?.displayNumber },
                activeShiftTracking = state is RemoteState.Confirmed &&
                    data.shiftEngine.classify(now) == ShiftActivityLevel.DEFINITELY_ACTIVE &&
                    data.openIntervalClockAnomalies[remoteId] != true
            )
        }

        val isRecent = data.recentTimestamp != null && (now - data.recentTimestamp) in 0..RECENT_ACTION_WINDOW_MILLIS
        val recentMessage = if (isRecent && data.recentGroupId != dismissedActionGroupId.value) data.recentSummary else null

        val westCard = cardFor(RemoteId.WEST)
        val eastCard = cardFor(RemoteId.EAST)

        val bannerDismissedRecently = dismissedShiftBannerAt.value?.let { (now - it) < 20 * 60_000L } ?: false
        val shiftBanner = if (data.shiftEngine.isNearShiftStart(now) && !bannerDismissedRecently) {
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
            recentActionGroupId = if (recentMessage != null) data.recentGroupId else null,
            evidenceProgress = data.evidenceProgress,
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
            // Undo control reverses this single button press atomically. The banner is
            // only ever dismissed once that write actually succeeds - if the repository
            // transaction fails, the strongest prompt telling the next operator that
            // state was never confirmed must stay visible, not disappear along with the
            // error.
            runCatching {
                container.confirmStateUseCase(RemoteId.WEST, RemoteId.EAST, elapsedRealtimeMillis = SystemClock.elapsedRealtime())
            }
                .onSuccess { dismissedShiftBannerAt.value = System.currentTimeMillis() }
                .onFailure { transientError.value = it.message }
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
        val confirmations = groupEvents.filter { it.eventType == EventType.STATE_CONFIRMED }
        val confirmed = confirmations.firstOrNull()
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
                if (corrected.previousBatteryId == null) {
                    // No prior identity or open interval existed here (e.g. a shift-start
                    // FIX WEST/EAST on a previously unknown remote) - this sets a fresh
                    // starting point, not a correction of anything, so it shouldn't claim
                    // a "catch" was made.
                    "$shortName ${label(corrected.newBatteryId)} SET"
                } else {
                    "GOOD CATCH • $shortName corrected to ${label(corrected.newBatteryId)}"
                }
            }

            confirmations.mapNotNull { it.remoteId }.distinct().size > 1 -> {
                if (confirmations.any { it.wallClockAnomalyDetected || it.monotonicContinuityBroken }) {
                    "BOTH REMOTES CONFIRMED • CLOCK IRREGULARITY DETECTED"
                } else {
                    "BOTH REMOTES CONFIRMED • SHIFT TRACKING PROTECTED"
                }
            }

            confirmed != null -> {
                val shortName = remotesById[confirmed.remoteId]?.shortName ?: return null
                if (confirmed.wallClockAnomalyDetected || confirmed.monotonicContinuityBroken) {
                    "$shortName ${label(confirmed.batteryId)} CONFIRMED • CLOCK IRREGULARITY DETECTED"
                } else {
                    "$shortName ${label(confirmed.batteryId)} CONFIRMED • TRACKING PROTECTED"
                }
            }

            markedUnknown != null -> {
                val shortName = remotesById[markedUnknown.remoteId]?.shortName ?: return null
                "$shortName UNKNOWN • NO GUESS ADDED"
            }

            else -> null
        }
    }

    private fun nextEvidenceMilestone(exactCycles: Int): Int? = when {
        exactCycles < 10 -> 10
        exactCycles < 30 -> 30
        exactCycles < 60 -> 60
        else -> null
    }
}
