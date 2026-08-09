package com.cranebatterytracker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteState
import com.cranebatterytracker.ui.common.ErrorBanner
import com.cranebatterytracker.ui.common.formatSinceLabel
import com.cranebatterytracker.ui.theme.BatteryNumberStyle
import com.cranebatterytracker.ui.theme.EastAccent
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
                    subLabel = state.west?.remote?.shortName?.let { "Front Crane" } ?: "Front Crane",
                    accentColor = WestAccent,
                    remoteState = state.west?.state,
                    batteryDisplayNumber = state.west?.batteryDisplayNumber,
                    onChangeBattery = { onChangeBattery(RemoteId.WEST) },
                    onConfirmStale = { viewModel.confirmStale(RemoteId.WEST) },
                    onCorrectState = { onCorrectState(RemoteId.WEST) }
                )
                RemoteCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    compassLabel = "EAST →",
                    subLabel = "Back Crane",
                    accentColor = EastAccent,
                    remoteState = state.east?.state,
                    batteryDisplayNumber = state.east?.batteryDisplayNumber,
                    onChangeBattery = { onChangeBattery(RemoteId.EAST) },
                    onConfirmStale = { viewModel.confirmStale(RemoteId.EAST) },
                    onCorrectState = { onCorrectState(RemoteId.EAST) }
                )
            }

            state.recentActionMessage?.let { message ->
                RecentActionBar(
                    message = message,
                    onUndo = {
                        state.recentActionGroupId?.let { viewModel.undoActionGroup(it) }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::undoMostRecent,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("WRONG BUTTON / UNDO")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("BATTERY RESULTS")
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
    onChangeBattery: () -> Unit,
    onConfirmStale: () -> Unit,
    onCorrectState: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
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
                    OutlinedButton(onClick = onConfirmStale, modifier = Modifier.fillMaxWidth()) {
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
private fun BatteryNumberDisplay(displayNumber: Int?, onTap: () -> Unit) {
    Text(
        text = displayNumber?.toString() ?: "?",
        style = BatteryNumberStyle,
        modifier = Modifier.clickable(onClick = onTap)
    )
}

@Composable
private fun RecentActionBar(message: String, onUndo: () -> Unit) {
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
            TextButton(onClick = onUndo) {
                Text("WRONG BUTTON?  UNDO")
            }
        }
    }
}

@Composable
private fun ShiftStartBanner(
    westNumber: Int?,
    eastNumber: Int?,
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
            Button(onClick = onBothCorrect, modifier = Modifier.fillMaxWidth()) {
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
