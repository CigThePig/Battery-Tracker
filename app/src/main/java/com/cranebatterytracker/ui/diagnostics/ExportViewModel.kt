package com.cranebatterytracker.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.cranebatterytracker.backup.CsvExporter
import com.cranebatterytracker.di.AppContainer
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Every export here is launched from Compose via `rememberCoroutineScope()`, which runs
 * on the main dispatcher. Sorting/deriving cycles and writing thousands of CSV rows (or
 * streaming a database file) are synchronous CPU/IO work that must never run there - on
 * a large event log it can freeze the UI or trigger an ANR - so each export explicitly
 * moves onto [Dispatchers.IO].
 */
class ExportViewModel(private val container: AppContainer) : ViewModel() {

    suspend fun exportRawEvents(out: OutputStream) = withContext(Dispatchers.IO) {
        val events = container.repository.currentEventsSnapshot()
        CsvExporter.exportRawEvents(events, out)
    }

    suspend fun exportDerivedCycles(out: OutputStream) = withContext(Dispatchers.IO) {
        val events = container.repository.currentEventsSnapshot()
        val settings = container.settingsRepository.settings.first()
        val cycles = container.runtimeAnalysisEngine(settings).deriveCycles(events)
        CsvExporter.exportDerivedCycles(cycles, out)
    }

    suspend fun exportDatabaseCopy(out: OutputStream) = withContext(Dispatchers.IO) {
        container.backupManager.exportDatabaseCopyTo(out)
    }
}
