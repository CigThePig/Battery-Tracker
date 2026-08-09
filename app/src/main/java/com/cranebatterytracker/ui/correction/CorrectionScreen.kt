package com.cranebatterytracker.ui.correction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.ui.common.ErrorBanner

@Composable
fun CorrectionScreen(
    viewModel: CorrectionViewModel,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                text = "THE TABLET CURRENTLY SAYS:",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Text(
                text = state.currentBatteryDisplayNumber?.let { "BATTERY $it" } ?: "UNKNOWN",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "WHAT BATTERY IS ACTUALLY IN THE REMOTE?",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
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
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Button(
                            onClick = { viewModel.selectBattery(battery.batteryId) },
                            enabled = !state.submitting,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(text = battery.displayNumber.toString(), style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::markUnknown,
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("I DON'T KNOW")
            }

            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("CANCEL")
            }
        }

        state.pendingCollision?.let { collision ->
            AlertDialog(
                onDismissRequest = viewModel::cancelCollision,
                title = { Text("Battery ${collision.batteryDisplayNumber} is in ${collision.otherRemoteShortName}") },
                text = {
                    Text(
                        "The tablet currently shows battery ${collision.batteryDisplayNumber} in " +
                            "${collision.otherRemoteShortName}. If you confirm, ${collision.otherRemoteShortName} " +
                            "will be marked unknown and this remote will show battery ${collision.batteryDisplayNumber}."
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmCollision) { Text("CONFIRM") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelCollision) { Text("CANCEL") }
                }
            )
        }
    }
}
