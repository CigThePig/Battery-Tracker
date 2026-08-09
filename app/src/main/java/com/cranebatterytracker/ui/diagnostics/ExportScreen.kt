package com.cranebatterytracker.ui.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class ExportTarget { RAW_EVENTS, DERIVED_CYCLES, DATABASE_COPY }

@Composable
fun ExportScreen(viewModel: ExportViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The system document picker can background this app long enough for the OS to
    // destroy and recreate the activity before delivering its result. A plain `remember`
    // would reset to RAW_EVENTS on recreation and silently write the wrong CSV/DB format
    // into the file the operator already named for a different export - rememberSaveable
    // (backed by a plain String, since enums aren't guaranteed Bundle-savable) survives that.
    var pendingTargetName by rememberSaveable { mutableStateOf(ExportTarget.RAW_EVENTS.name) }
    val pendingTarget: ExportTarget = ExportTarget.valueOf(pendingTargetName)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = pendingTarget
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    when (target) {
                        ExportTarget.RAW_EVENTS -> viewModel.exportRawEvents(out)
                        ExportTarget.DERIVED_CYCLES -> viewModel.exportDerivedCycles(out)
                        ExportTarget.DATABASE_COPY -> viewModel.exportDatabaseCopy(out)
                    }
                } ?: error("Could not open the selected location")
            }.onSuccess {
                statusMessage = "Export complete."
            }.onFailure {
                statusMessage = "Export failed: ${it.message}"
            }
        }
    }

    fun startExport(target: ExportTarget, suggestedName: String) {
        pendingTargetName = target.name
        statusMessage = null
        launcher.launch(suggestedName)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(text = "EXPORT", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Everything stays on this tablet unless you explicitly export it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            val today = LocalDate.now().toString()
            Button(
                onClick = { startExport(ExportTarget.RAW_EVENTS, "battery_tracker_events_$today.csv") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("EXPORT RAW EVENT CSV") }

            Button(
                onClick = { startExport(ExportTarget.DERIVED_CYCLES, "battery_tracker_cycles_$today.csv") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("EXPORT DERIVED CYCLE CSV") }

            Button(
                onClick = { startExport(ExportTarget.DATABASE_COPY, "battery_tracker_database_$today.db") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("EXPORT SQLITE DATABASE COPY") }

            statusMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
