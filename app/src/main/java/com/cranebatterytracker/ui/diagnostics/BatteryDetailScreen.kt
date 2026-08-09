package com.cranebatterytracker.ui.diagnostics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification
import com.cranebatterytracker.ui.common.formatDurationHoursMinutes
import com.cranebatterytracker.ui.common.formatSignedPercent
import com.cranebatterytracker.ui.theme.EastAccent
import com.cranebatterytracker.ui.theme.StatusBad
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.WestAccent

@Composable
fun BatteryDetailScreen(viewModel: DiagnosticsViewModel, batteryId: Int, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val health = snapshot?.healths?.firstOrNull { it.batteryId == batteryId }
    val cycles = snapshot?.cyclesByBattery?.get(batteryId).orEmpty()
        .filter { it.classification != RuntimeClassification.UNKNOWN }
        .sortedByDescending { it.startTimestamp }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }

            if (health == null) {
                Text("No data for this battery yet.")
                return@Column
            }

            Text(text = "BATTERY ${health.displayNumber}", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = trendLabel(health.trend),
                style = MaterialTheme.typography.titleLarge,
                color = trendColor(health.trend)
            )
            Text(text = health.assessment, style = MaterialTheme.typography.bodyMedium)

            LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    LabeledValue("Typical runtime", health.lifetimeReliableMedianMillis?.let(::formatDurationHoursMinutes) ?: "--")
                    LabeledValue("Recent runtime", health.recentReliableMedianMillis?.let(::formatDurationHoursMinutes) ?: "--")
                    LabeledValue("Historical baseline", health.historicalBaselineMillis?.let(::formatDurationHoursMinutes) ?: "--")
                    LabeledValue("Reliable cycles", health.reliableCycleCount.toString())
                    health.recentChangePercent?.let { LabeledValue("Recent change", formatSignedPercent(it)) }
                    LabeledValue("Short runtimes", "${health.shortRuntimeEventCount}")
                    LabeledValue("Suspicious-high runtimes", "${health.suspiciousHighCount}")
                    LabeledValue("Dead-battery events", "${health.deadEventCount}")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(text = "RUNTIME OVER TIME", style = MaterialTheme.typography.titleMedium)
                    RuntimeGraph(cycles = cycles, modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    LabeledValue("WEST / FRONT", health.westMedianMillis?.let(::formatDurationHoursMinutes) ?: "Not enough data")
                    LabeledValue("EAST / BACK", health.eastMedianMillis?.let(::formatDurationHoursMinutes) ?: "Not enough data")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(text = "RECENT CYCLES", style = MaterialTheme.typography.titleMedium)
                }

                items(cycles.take(20)) { cycle -> CycleRow(cycle) }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CycleRow(cycle: DerivedCycle) {
    val remoteColor = if (cycle.remoteId == RemoteId.WEST) WestAccent else EastAccent
    val runtimeLabel = when (cycle.classification) {
        RuntimeClassification.EXACT -> formatDurationHoursMinutes(cycle.minimumActiveRuntimeMillis)
        else -> "${formatDurationHoursMinutes(cycle.minimumActiveRuntimeMillis)} – ${formatDurationHoursMinutes(cycle.maximumActiveRuntimeMillis)}"
    }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = "${cycle.remoteId.name} · ${cycle.displayClassification.name.replace('_', ' ')}",
            style = MaterialTheme.typography.bodyMedium,
            color = remoteColor
        )
        Text(text = runtimeLabel, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RuntimeGraph(cycles: List<DerivedCycle>, modifier: Modifier = Modifier) {
    val chronological = cycles.sortedBy { it.startTimestamp }
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointColor = MaterialTheme.colorScheme.primary
    val outlierColor = StatusBad
    val unknownColor = StatusUnknown

    Canvas(modifier = modifier) {
        if (chronological.isEmpty()) return@Canvas
        val maxRuntime = chronological.maxOf { it.maximumActiveRuntimeMillis }.coerceAtLeast(1L)
        val leftPadding = 8.dp.toPx()
        val bottomPadding = 8.dp.toPx()
        val plotWidth = size.width - leftPadding * 2
        val plotHeight = size.height - bottomPadding * 2

        drawLine(axisColor, Offset(leftPadding, bottomPadding), Offset(leftPadding, size.height - bottomPadding))
        drawLine(axisColor, Offset(leftPadding, size.height - bottomPadding), Offset(size.width - leftPadding, size.height - bottomPadding))

        val count = chronological.size
        chronological.forEachIndexed { index, cycle ->
            val x = if (count == 1) leftPadding + plotWidth / 2 else leftPadding + plotWidth * index / (count - 1)
            val y = size.height - bottomPadding - (cycle.minimumActiveRuntimeMillis.toFloat() / maxRuntime) * plotHeight

            when {
                cycle.isHighOutlier -> drawOutlierMark(Offset(x, y), outlierColor)
                cycle.classification == RuntimeClassification.EXACT -> drawCircle(pointColor, radius = 6.dp.toPx(), center = Offset(x, y))
                else -> drawCircle(unknownColor, radius = 6.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawOutlierMark(center: Offset, color: androidx.compose.ui.graphics.Color) {
    val size = 6.dp.toPx()
    drawLine(color, Offset(center.x - size, center.y - size), Offset(center.x + size, center.y + size), strokeWidth = 3.dp.toPx())
    drawLine(color, Offset(center.x - size, center.y + size), Offset(center.x + size, center.y - size), strokeWidth = 3.dp.toPx())
}
