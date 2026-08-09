package com.cranebatterytracker.ui.batterypicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(state.batteries) { battery ->
                    val unavailable = battery.batteryId == state.unavailableBatteryId
                    BatteryGridButton(
                        number = battery.displayNumber,
                        unavailableInRemote = if (unavailable) state.otherRemoteShortName else null,
                        enabled = !state.submitting,
                        onClick = { if (!unavailable) viewModel.selectBattery(battery.batteryId) }
                    )
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
private fun BatteryGridButton(number: Int, unavailableInRemote: String?, enabled: Boolean, onClick: () -> Unit) {
    val selectable = enabled && unavailableInRemote == null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selectable) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxSize()
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
                if (unavailableInRemote != null) {
                    Text(
                        text = "IN ${unavailableInRemote.uppercase()}",
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
