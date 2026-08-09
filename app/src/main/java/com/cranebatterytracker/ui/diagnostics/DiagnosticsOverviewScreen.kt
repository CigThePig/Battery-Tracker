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
import com.cranebatterytracker.backup.BackupStatus
import com.cranebatterytracker.domain.model.BatteryHealth
import com.cranebatterytracker.domain.model.BatteryTrend
import com.cranebatterytracker.ui.common.formatDurationHoursMinutes
import com.cranebatterytracker.ui.theme.StatusBad
import com.cranebatterytracker.ui.theme.StatusGood
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.StatusWarn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    val backupStatus by viewModel.backupStatus.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "BATTERY RESULTS", style = MaterialTheme.typography.headlineMedium)
            BackupStatusRow(backupStatus)

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

private val backupDateFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

/**
 * Surfaces automatic backup health here rather than on the main operator screen (spec
 * review Issue 14): backups run silently in the background, and a failure that never
 * shows up anywhere is effectively invisible until data is already needed and missing.
 */
@Composable
private fun BackupStatusRow(status: BackupStatus) {
    val zoneId = ZoneId.systemDefault()
    val lastSuccessText = status.lastSuccessAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(zoneId).format(backupDateFormatter)
    }
    val daysSinceSuccess = status.lastSuccessAtMillis?.let {
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(it), Instant.now())
    }
    val isFailing = status.lastFailureMessage != null &&
        (status.lastAttemptAtMillis == null || status.lastAttemptAtMillis != status.lastSuccessAtMillis)

    val (text, color) = when {
        isFailing -> {
            val lastGood = when {
                daysSinceSuccess == null -> "no successful backup yet"
                daysSinceSuccess <= 0L -> "last good backup: today"
                daysSinceSuccess == 1L -> "last good backup: 1 day ago"
                else -> "last good backup: $daysSinceSuccess days ago"
            }
            "AUTOMATIC BACKUP FAILED — $lastGood" to StatusBad
        }
        lastSuccessText != null -> "Last successful backup: $lastSuccessText" to StatusGood
        else -> null to null
    }

    if (text != null && color != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(top = 4.dp)
        )
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
