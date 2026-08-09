package com.cranebatterytracker.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.cranebatterytracker.backup.CsvExporter
import com.cranebatterytracker.di.AppContainer
import java.io.OutputStream
import kotlinx.coroutines.flow.first

class ExportViewModel(private val container: AppContainer) : ViewModel() {

    suspend fun exportRawEvents(out: OutputStream) {
        val events = container.repository.currentEventsSnapshot()
        CsvExporter.exportRawEvents(events, out)
    }

    suspend fun exportDerivedCycles(out: OutputStream) {
        val events = container.repository.currentEventsSnapshot()
        val settings = container.settingsRepository.settings.first()
        val cycles = container.runtimeAnalysisEngine(settings).deriveCycles(events)
        CsvExporter.exportDerivedCycles(cycles, out)
    }

    suspend fun exportDatabaseCopy(out: OutputStream) {
        container.backupManager.exportDatabaseCopyTo(out)
    }
}
