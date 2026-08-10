package com.cranebatterytracker.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteState
import com.cranebatterytracker.ui.common.ErrorBanner
import com.cranebatterytracker.ui.common.formatSinceLabel
import com.cranebatterytracker.ui.theme.BatteryNumberStyle
import com.cranebatterytracker.ui.theme.EastAccent
import com.cranebatterytracker.ui.theme.StatusGood
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.StatusWarn
import com.cranebatterytracker.ui.theme.WestAccent

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onChangeBattery: (RemoteId) -> Unit,
    onCorrectState: (RemoteId) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.shiftBanner?.let { banner ->
                ShiftStartBanner(
                    westNumber = banner.westBatteryDisplayNumber,
                    eastNumber = banner.eastBatteryDisplayNumber,
                    enabled = !state.busy,
                    onBothCorrect = viewModel::confirmBothAtShiftStart,
                    onFixWest = { onCorrectState(RemoteId.WEST) },
                    onFixEast = { onCorrectState(RemoteId.EAST) },
                    onDismiss = viewModel::dismissShiftBanner
                )
            }

            state.errorMessage?.let { message ->
                ErrorBanner(message = message, onDismiss = viewModel::consumeError, modifier = Modifier.padding(top = 4.dp))
            }

            Text(
                text = "CRANE BATTERY TRACKER",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    compassLabel = "← WEST",
                    subLabel = state.west?.remote?.displayName ?: "Front Crane",
                    accentColor = WestAccent,
                    remoteState = state.west?.state,
                    batteryDisplayNumber = state.west?.batteryDisplayNumber,
                    activeShiftTracking = state.west?.activeShiftTracking == true,
                    confirmEnabled = !state.busy,
                    onChangeBattery = { onChangeBattery(RemoteId.WEST) },
                    onConfirmStale = { viewModel.confirmStale(RemoteId.WEST) },
                    onCorrectState = { onCorrectState(RemoteId.WEST) }
                )
                RemoteCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    compassLabel = "EAST →",
                    subLabel = state.east?.remote?.displayName ?: "Back Crane",
                    accentColor = EastAccent,
                    remoteState = state.east?.state,
                    batteryDisplayNumber = state.east?.batteryDisplayNumber,
                    activeShiftTracking = state.east?.activeShiftTracking == true,
                    confirmEnabled = !state.busy,
                    onChangeBattery = { onChangeBattery(RemoteId.EAST) },
                    onConfirmStale = { viewModel.confirmStale(RemoteId.EAST) },
                    onCorrectState = { onCorrectState(RemoteId.EAST) }
                )
            }

            state.recentActionMessage?.let { message ->
                RecentActionBar(
                    message = message,
                    enabled = !state.busy,
                    onUndo = {
                        state.recentActionGroupId?.let { viewModel.undoActionGroup(it) }
                    }
                )
            }

            state.evidenceProgress?.let { progress ->
                EvidenceStatusStrip(progress = progress, onClick = onOpenDiagnostics)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::undoMostRecent,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("WRONG BUTTON / UNDO")
                }
            }

        }
    }
}

@Composable
private fun RemoteCard(
    modifier: Modifier,
    compassLabel: String,
    subLabel: String,
    accentColor: androidx.compose.ui.graphics.Color,
    remoteState: RemoteState?,
    batteryDisplayNumber: Int?,
    activeShiftTracking: Boolean,
    confirmEnabled: Boolean,
    onChangeBattery: () -> Unit,
    onConfirmStale: () -> Unit,
    onCorrectState: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = compassLabel,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            when (remoteState) {
                is RemoteState.Confirmed -> {
                    BatteryNumberDisplay(batteryDisplayNumber, onTap = onCorrectState)
                    Text(
                        text = formatSinceLabel(remoteState.installedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TrackingPulse(active = activeShiftTracking, accentColor = accentColor)
                }

                is RemoteState.Stale -> {
                    BatteryNumberDisplay(batteryDisplayNumber, onTap = onCorrectState)
                    Text(
                        text = "NOT CONFIRMED THIS SHIFT",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusWarn,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onConfirmStale, enabled = confirmEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text("STILL ${batteryDisplayNumber ?: "?"}")
                    }
                }

                is RemoteState.Unknown, null -> {
                    Text(
                        text = "BATTERY",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "?",
                        style = BatteryNumberStyle,
                        color = StatusUnknown
                    )
                    Text(
                        text = "UNKNOWN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusUnknown,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val isDead = remoteState is RemoteState.Confirmed || remoteState is RemoteState.Stale
            if (isDead) {
                Text(
                    text = "BATTERY DEAD?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Button(
                onClick = onChangeBattery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = if (isDead) "CHANGE\nBATTERY" else "SET\nBATTERY",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrackingPulse(active: Boolean, accentColor: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "tracking pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1_050), repeatMode = RepeatMode.Reverse),
        label = "tracking pulse alpha"
    )
    Row(
        modifier = Modifier.padding(top = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background((if (active) StatusGood else accentColor).copy(alpha = if (active) pulseAlpha else 0.55f))
        )
        Text(
            text = if (active) "DATA TRACKING ACTIVE" else "STATE CONFIRMED",
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EvidenceStatusStrip(progress: EvidenceProgressUiState, onClick: () -> Unit) {
    val nextTarget = progress.nextExactCycleMilestone
    val fraction = if (nextTarget == null) 1f else (progress.exactCycleCount.toFloat() / nextTarget).coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, StatusGood.copy(alpha = 0.42f)),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = StatusGood.copy(alpha = 0.14f)) {
                Icon(
                    Icons.Rounded.QueryStats,
                    contentDescription = null,
                    tint = StatusGood,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BATTERY EVIDENCE", style = MaterialTheme.typography.labelLarge)
                    Text(progress.qualityLevel.name, style = MaterialTheme.typography.labelLarge, color = StatusGood)
                }
                Text(
                    text = buildString {
                        append(progress.exactCycleCount)
                        append(if (progress.exactCycleCount == 1) " EXACT RUN" else " EXACT RUNS")
                        if (progress.usefulObservationCount > 0) append(" • ${progress.usefulObservationCount} PARTIAL")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(CircleShape),
                    color = StatusGood,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = if (progress.qualityLevel == DataQualityLevel.STRONG) "STRONG EVIDENCE BUILT • VIEW RESULTS →"
                    else if (nextTarget == null) "EVIDENCE MILESTONES COMPLETE • VIEW RESULTS →"
                    else "NEXT EVIDENCE MILESTONE: $nextTarget EXACT RUNS • VIEW →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusGood,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BatteryNumberDisplay(displayNumber: Int?, onTap: () -> Unit) {
    Text(
        text = displayNumber?.toString() ?: "?",
        style = BatteryNumberStyle,
        modifier = Modifier.clickable(onClick = onTap)
    )
}

@Composable
private fun RecentActionBar(message: String, enabled: Boolean, onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onUndo, enabled = enabled) {
                Text("WRONG BUTTON?  UNDO")
            }
        }
    }
}

@Composable
private fun ShiftStartBanner(
    westNumber: Int?,
    eastNumber: Int?,
    enabled: Boolean,
    onBothCorrect: () -> Unit,
    onFixWest: () -> Unit,
    onFixEast: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "START OF SHIFT",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "WEST: ${westNumber ?: "?"}        EAST: ${eastNumber ?: "?"}",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBothCorrect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("BOTH STILL CORRECT")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFixWest, modifier = Modifier.weight(1f)) {
                    Text("FIX WEST")
                }
                OutlinedButton(onClick = onFixEast, modifier = Modifier.weight(1f)) {
                    Text("FIX EAST")
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}
