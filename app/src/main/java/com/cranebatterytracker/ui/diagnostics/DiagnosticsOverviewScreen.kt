package com.cranebatterytracker.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.model.BatteryHealth
import com.cranebatterytracker.domain.model.BatteryTrend
import com.cranebatterytracker.ui.common.formatDurationHoursMinutes
import com.cranebatterytracker.ui.theme.StatusBad
import com.cranebatterytracker.ui.theme.StatusGood
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.StatusWarn

@Composable
fun DiagnosticsOverviewScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    onOpenBatteryDetail: (Int) -> Unit,
    onOpenRemoteComparison: () -> Unit,
    onOpenDataQuality: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "BATTERY RESULTS", style = MaterialTheme.typography.headlineMedium)

            LazyColumn(
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(snapshot?.healths ?: emptyList()) { health ->
                    BatteryOverviewCard(health = health, onClick = { onOpenBatteryDetail(health.batteryId) })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenRemoteComparison, modifier = Modifier.fillMaxWidth()) {
                    Text("WEST VS EAST")
                }
                OutlinedButton(onClick = onOpenDataQuality, modifier = Modifier.fillMaxWidth()) {
                    Text("DATA QUALITY")
                }
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Text("EVENT HISTORY")
                }
                OutlinedButton(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) {
                    Text("EXPORT")
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("SETTINGS")
                }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("BACK TO TRACKER")
                }
            }
        }
    }
}

@Composable
private fun BatteryOverviewCard(health: BatteryHealth, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "BATTERY ${health.displayNumber}", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Typical: ${health.lifetimeReliableMedianMillis?.let(::formatDurationHoursMinutes) ?: "--"}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = trendLabel(health.trend),
                style = MaterialTheme.typography.bodyLarge,
                color = trendColor(health.trend)
            )
            Text(
                text = "${health.reliableCycleCount} reliable cycles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun trendLabel(trend: BatteryTrend): String = when (trend) {
    BatteryTrend.NOT_ENOUGH_DATA -> "NOT ENOUGH DATA"
    BatteryTrend.STABLE -> "STABLE"
    BatteryTrend.WATCH -> "WATCH"
    BatteryTrend.DECLINING -> "DECLINING"
    BatteryTrend.STRONG_REPLACEMENT_CANDIDATE -> "STRONG REPLACEMENT CANDIDATE"
}

fun trendColor(trend: BatteryTrend) = when (trend) {
    BatteryTrend.NOT_ENOUGH_DATA -> StatusUnknown
    BatteryTrend.STABLE -> StatusGood
    BatteryTrend.WATCH -> StatusWarn
    BatteryTrend.DECLINING -> StatusWarn
    BatteryTrend.STRONG_REPLACEMENT_CANDIDATE -> StatusBad
}
