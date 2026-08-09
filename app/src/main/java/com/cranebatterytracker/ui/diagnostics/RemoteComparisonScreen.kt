package com.cranebatterytracker.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.model.BatteryRemoteComparison
import com.cranebatterytracker.domain.model.RemoteWarningLevel
import com.cranebatterytracker.ui.common.formatDurationHoursMinutes
import com.cranebatterytracker.ui.theme.EastAccent
import com.cranebatterytracker.ui.theme.StatusBad
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.StatusWarn
import com.cranebatterytracker.ui.theme.WestAccent

@Composable
fun RemoteComparisonScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val summary = snapshot?.remoteSummary

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(text = "WEST VS EAST", style = MaterialTheme.typography.headlineMedium)

            summary?.let {
                Surface(
                    color = warningColor(it.warningLevel).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Text(
                        text = it.message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = warningColor(it.warningLevel)
                    )
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(summary?.perBattery ?: emptyList()) { comparison -> ComparisonRow(comparison) }
            }
        }
    }
}

@Composable
private fun ComparisonRow(comparison: BatteryRemoteComparison) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "BATTERY ${comparison.displayNumber}", style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "West: ${comparison.westMedianMillis?.let(::formatDurationHoursMinutes) ?: "not enough data"}",
                    color = WestAccent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "East: ${comparison.eastMedianMillis?.let(::formatDurationHoursMinutes) ?: "not enough data"}",
                    color = EastAccent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            val west = comparison.westMedianMillis
            val east = comparison.eastMedianMillis
            if (west != null && east != null) {
                val diffMinutes = (west - east) / 60_000L
                val label = when {
                    diffMinutes > 0 -> "Difference: East -${diffMinutes}m"
                    diffMinutes < 0 -> "Difference: West -${-diffMinutes}m"
                    else -> "No difference"
                }
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun warningColor(level: RemoteWarningLevel) = when (level) {
    RemoteWarningLevel.NONE -> StatusUnknown
    RemoteWarningLevel.DEVELOPING -> StatusWarn
    RemoteWarningLevel.STRONG -> StatusBad
}
