package com.cranebatterytracker.ui.batterypicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.ui.common.ErrorBanner
import com.cranebatterytracker.ui.theme.StatusUnknown
import kotlinx.coroutines.delay

@Composable
fun BatteryPickerScreen(
    viewModel: BatteryPickerViewModel,
    onCancel: () -> Unit,
    onSavedComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.saved != null) {
            SavedConfirmation(
                remoteShortName = state.remoteShortName,
                fromNumber = state.saved!!.fromDisplayNumber,
                toNumber = state.saved!!.toDisplayNumber,
                onDone = {
                    viewModel.consumeSaved()
                    onSavedComplete()
                }
            )
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = state.remoteDisplayName.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = state.currentBatteryDisplayNumber?.let { "BATTERY $it DIED" } ?: "SETTING BATTERY",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
            Text(
                text = "WHICH BATTERY DID YOU PUT IN?",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let { message ->
                ErrorBanner(message = message, onDismiss = viewModel::consumeError, modifier = Modifier.padding(top = 8.dp))
            }

            // A plain, non-lazy 2-wide grid rather than LazyVerticalGrid (spec review Issue
            // 9): lazy layout children along the scrolling axis aren't measured like
            // ordinary bounded cells, so the four battery buttons could collapse toward
            // their minimum intrinsic height inside a mostly-empty grid. There will never
            // be more than a handful of batteries, so a bounded Column of Rows gives each
            // cell an explicit, predictable, glove-friendly share of the available space.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.batteries.chunked(2).forEach { rowBatteries ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowBatteries.forEach { battery ->
                            val ownedByOtherRemote = battery.batteryId == state.unavailableBatteryId
                            val isCurrentBattery = battery.batteryId == state.currentBatteryId
                            val unavailable = ownedByOtherRemote || isCurrentBattery
                            val unavailableLabel = when {
                                ownedByOtherRemote -> state.otherRemoteShortName?.let { "IN ${it.uppercase()}" }
                                isCurrentBattery -> "CURRENT / JUST REMOVED"
                                else -> null
                            }
                            BatteryGridButton(
                                number = battery.displayNumber,
                                unavailableLabel = unavailableLabel,
                                enabled = !state.submitting,
                                onClick = { if (!unavailable) viewModel.selectBattery(battery.batteryId) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        // An odd final row (e.g. 3 batteries) still reserves the second
                        // cell's space instead of stretching the lone button full-width.
                        if (rowBatteries.size < 2) {
                            Column(modifier = Modifier.weight(1f)) {}
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("CANCEL")
            }
        }
    }
}

@Composable
private fun BatteryGridButton(
    number: Int,
    unavailableLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val selectable = enabled && unavailableLabel == null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selectable) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            enabled = selectable,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = number.toString(), style = MaterialTheme.typography.headlineLarge)
                if (unavailableLabel != null) {
                    Text(
                        text = unavailableLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusUnknown
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedConfirmation(remoteShortName: String, fromNumber: Int?, toNumber: Int, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1100)
        onDone()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = remoteShortName.uppercase(), style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (fromNumber != null) "BATTERY $fromNumber → BATTERY $toNumber" else "BATTERY $toNumber SET",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(text = "SAVED", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
    }
}
