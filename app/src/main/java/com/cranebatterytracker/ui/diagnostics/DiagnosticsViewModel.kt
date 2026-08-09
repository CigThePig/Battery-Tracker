package com.cranebatterytracker.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cranebatterytracker.di.AppContainer
import com.cranebatterytracker.domain.analysis.EventFiltering
import com.cranebatterytracker.domain.analysis.RuntimeAnalysisEngine
import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.BatteryHealth
import com.cranebatterytracker.domain.model.DataQualitySummary
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.model.RemoteDiagnosticSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiagnosticsSnapshot(
    val batteries: List<Battery>,
    val remotes: List<Remote>,
    val cyclesByBattery: Map<Int, List<DerivedCycle>>,
    val healths: List<BatteryHealth>,
    val remoteSummary: RemoteDiagnosticSummary,
    val dataQuality: DataQualitySummary,
    val rawEvents: List<DomainEvent>
)

/**
 * Shared across every diagnostics sub-screen (overview, battery detail,
 * remote comparison, data quality, history) so they always agree on the
 * same computed snapshot and never recompute it redundantly.
 */
class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {

    val snapshot = combine(
        container.repository.observeEvents(),
        container.repository.observeBatteries(),
        container.repository.observeRemotes(),
        container.settingsRepository.settings
    ) { events, batteries, remotes, settings ->
        val analysisEngine = container.runtimeAnalysisEngine(settings)
        val cycles = analysisEngine.deriveCycles(events)
        val cyclesByBattery = cycles.groupBy { it.batteryId }
        val effective = EventFiltering.effectiveChronologicalEvents(events)
        val deadCounts = container.diagnosticEngine.deadEventCountsByBattery(effective)
        val correctionCount = container.diagnosticEngine.correctionCount(effective)

        val healths = batteries.map { battery ->
            container.diagnosticEngine.batteryHealth(
                battery,
                cyclesByBattery[battery.batteryId] ?: emptyList(),
                deadCounts[battery.batteryId] ?: 0
            )
        }

        DiagnosticsSnapshot(
            batteries = batteries,
            remotes = remotes,
            cyclesByBattery = cyclesByBattery,
            healths = healths,
            remoteSummary = container.diagnosticEngine.remoteDiagnostics(batteries, cyclesByBattery),
            dataQuality = container.diagnosticEngine.dataQuality(cycles, correctionCount),
            rawEvents = events
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
