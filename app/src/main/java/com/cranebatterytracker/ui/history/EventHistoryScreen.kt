package com.cranebatterytracker.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.analysis.EventFiltering
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.ui.common.formatClockTime
import com.cranebatterytracker.ui.common.formatDayHeader
import com.cranebatterytracker.ui.diagnostics.DiagnosticsViewModel
import com.cranebatterytracker.ui.theme.StatusBad

@Composable
fun EventHistoryScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val events = snapshot?.rawEvents.orEmpty()
    val remotesById = snapshot?.remotes.orEmpty().associateBy { it.remoteId }
    val undoneGroups = EventFiltering.undoneActionGroupIds(events)

    val grouped = events
        .filter { it.eventType != EventType.UNDO_ACTION }
        .sortedByDescending { it.timestampEpochMillis }
        .groupBy { formatDayHeader(it.timestampEpochMillis) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(text = "EVENT HISTORY", style = MaterialTheme.typography.headlineMedium)

            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                grouped.forEach { (day, dayEvents) ->
                    item {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(dayEvents) { event ->
                        val undone = event.actionGroupId != null && event.actionGroupId in undoneGroups
                        EventRow(event, remotesById, undone)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: DomainEvent, remotesById: Map<com.cranebatterytracker.domain.model.RemoteId, Remote>, undone: Boolean) {
    val remoteLabel = event.remoteId?.let { remotesById[it]?.displayName?.uppercase() } ?: "BOTH REMOTES"
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = formatClockTime(event.timestampEpochMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = remoteLabel, style = MaterialTheme.typography.titleMedium)
        Text(text = describeEvent(event), style = MaterialTheme.typography.bodyLarge)
        if (undone) {
            Text(text = "UNDONE", style = MaterialTheme.typography.bodyMedium, color = StatusBad)
        }
    }
}

private fun describeEvent(event: DomainEvent): String = when (event.eventType) {
    EventType.BATTERY_INSTALLED -> "Battery ${event.newBatteryId ?: event.batteryId} installed."
    EventType.BATTERY_REMOVED_DEAD -> "Battery ${event.batteryId} died."
    EventType.STATE_CONFIRMED -> "Battery ${event.batteryId} confirmed."
    EventType.STATE_CORRECTED -> "Corrected: Battery ${event.previousBatteryId ?: "?"} → Battery ${event.newBatteryId}."
    EventType.STATE_MARKED_UNKNOWN -> "Marked unknown."
    EventType.UNDO_ACTION -> "Undo."
    EventType.SYSTEM_TIME_WARNING -> "Device clock anomaly detected."
}
