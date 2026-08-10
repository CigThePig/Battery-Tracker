package com.cranebatterytracker.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.backup.BackupStatus
import com.cranebatterytracker.domain.model.BatteryHealth
import com.cranebatterytracker.domain.model.BatteryTrend
import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DataQualitySummary
import com.cranebatterytracker.ui.common.formatDurationHoursMinutes
import com.cranebatterytracker.ui.feedback.EvidenceFeedbackFactory
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

            snapshot?.dataQuality?.let { quality -> EvidenceOverviewCard(quality) }

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
private fun EvidenceOverviewCard(quality: DataQualitySummary) {
    val nextTarget = when {
        quality.exactCycles < 10 -> 10
        quality.exactCycles < 30 -> 30
        quality.exactCycles < 60 -> 60
        else -> null
    }
    val fraction = if (nextTarget == null) 1f else (quality.exactCycles.toFloat() / nextTarget).coerceIn(0f, 1f)
    val useful = quality.shiftInterruptedCycles + quality.confirmedMinimumObservations
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = StatusGood.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, StatusGood.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = StatusGood.copy(alpha = 0.14f)) {
                Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = StatusGood, modifier = Modifier.padding(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BATTERY EVIDENCE", style = MaterialTheme.typography.titleMedium)
                    Text(quality.overallLevel.name, style = MaterialTheme.typography.labelLarge, color = StatusGood)
                }
                Text(
                    "${quality.exactCycles} exact runs • $useful useful partial observations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(7.dp).clip(CircleShape),
                    color = StatusGood,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    if (quality.overallLevel == DataQualityLevel.STRONG) "Strong evidence has been built across the battery fleet."
                    else if (nextTarget == null) "Exact-run milestones are complete, but data quality is not yet strong."
                    else "Every accurate change moves the fleet toward $nextTarget exact runs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusGood,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
        border = BorderStroke(1.dp, trendColor(health.trend).copy(alpha = 0.30f)),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "BATTERY ${health.displayNumber}", style = MaterialTheme.typography.titleLarge)
            if (health.reliableCycleCount < EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET) {
                BatteryEvidenceDots(health.reliableCycleCount)
                Text(
                    text = "${EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET - health.reliableCycleCount} more reliable " +
                        if (EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET - health.reliableCycleCount == 1) "run to first baseline" else "runs to first baseline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusGood,
                    modifier = Modifier.padding(top = 5.dp)
                )
            } else {
                Text(
                    text = "✓ BASELINE READY",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusGood,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Typical runtime: ${health.lifetimeReliableMedianMillis?.let(::formatDurationHoursMinutes) ?: "--"}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = trendLabel(health.trend),
                style = MaterialTheme.typography.bodyLarge,
                color = trendColor(health.trend)
            )
            Text(
                text = "${health.reliableCycleCount} reliable ${if (health.reliableCycleCount == 1) "run" else "runs"} captured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryEvidenceDots(reliableCount: Int) {
    Row(
        modifier = Modifier.padding(top = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET) { index ->
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < reliableCount) StatusGood
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
        Text(
            "$reliableCount / ${EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET}",
            style = MaterialTheme.typography.labelLarge,
            color = StatusGood,
            modifier = Modifier.padding(start = 3.dp)
        )
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
