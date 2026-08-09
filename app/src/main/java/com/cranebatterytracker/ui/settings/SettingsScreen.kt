package com.cranebatterytracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.data.settings.AppSettings
import java.time.LocalTime

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var unlocked by remember(settings.adminPin) { mutableStateOf(settings.adminPin.isNullOrBlank()) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!unlocked) {
            PinGate(onUnlock = { entered -> if (entered == settings.adminPin) unlocked = true }, onBack = onBack)
        } else {
            SettingsContent(settings = settings, viewModel = viewModel, onBack = onBack)
        }
    }
}

@Composable
private fun PinGate(onUnlock: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(text = "ENTER ADMIN PIN", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onUnlock(pin) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("UNLOCK")
        }
    }
}

@Composable
private fun SettingsContent(settings: AppSettings, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var dayStart by remember(settings) { mutableStateOf(settings.dayStart.toString()) }
    var dayAmbiguousStart by remember(settings) { mutableStateOf(settings.dayAmbiguousStart.toString()) }
    var dayAmbiguousEnd by remember(settings) { mutableStateOf(settings.dayAmbiguousEnd.toString()) }
    var nightStart by remember(settings) { mutableStateOf(settings.nightStart.toString()) }
    var nightAmbiguousStart by remember(settings) { mutableStateOf(settings.nightAmbiguousStart.toString()) }
    var nightAmbiguousEnd by remember(settings) { mutableStateOf(settings.nightAmbiguousEnd.toString()) }
    var shiftError by remember { mutableStateOf<String?>(null) }

    var dailyRetention by remember(settings) { mutableStateOf(settings.dailyBackupRetentionCount.toString()) }
    var archiveRetention by remember(settings) { mutableStateOf(settings.archiveBackupRetentionCount.toString()) }

    var newPin by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(text = "SETTINGS", style = MaterialTheme.typography.headlineMedium)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "SHIFT TIMES (24h, HH:mm)", style = MaterialTheme.typography.titleMedium)
            TimeField("Day start", dayStart) { dayStart = it }
            TimeField("Day ambiguous start", dayAmbiguousStart) { dayAmbiguousStart = it }
            TimeField("Day ambiguous end", dayAmbiguousEnd) { dayAmbiguousEnd = it }
            TimeField("Night start", nightStart) { nightStart = it }
            TimeField("Night ambiguous start", nightAmbiguousStart) { nightAmbiguousStart = it }
            TimeField("Night ambiguous end", nightAmbiguousEnd) { nightAmbiguousEnd = it }
            shiftError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    runCatching {
                        viewModel.updateShiftTimes(
                            LocalTime.parse(dayStart),
                            LocalTime.parse(dayAmbiguousStart),
                            LocalTime.parse(dayAmbiguousEnd),
                            LocalTime.parse(nightStart),
                            LocalTime.parse(nightAmbiguousStart),
                            LocalTime.parse(nightAmbiguousEnd)
                        )
                        shiftError = null
                    }.onFailure { shiftError = "Times must look like 05:00" }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("SAVE SHIFT TIMES") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "BACKUP RETENTION", style = MaterialTheme.typography.titleMedium)
            TimeField("Daily backups to keep", dailyRetention) { dailyRetention = it.filter(Char::isDigit) }
            TimeField("Archive backups to keep", archiveRetention) { archiveRetention = it.filter(Char::isDigit) }
            Button(
                onClick = {
                    viewModel.updateBackupRetention(
                        dailyRetention.toIntOrNull() ?: settings.dailyBackupRetentionCount,
                        archiveRetention.toIntOrNull() ?: settings.archiveBackupRetentionCount
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("SAVE BACKUP RETENTION") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "ADMIN PIN", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (settings.adminPin.isNullOrBlank()) "No PIN set - Settings are open." else "PIN is set.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it },
                label = { Text("New PIN (blank to remove)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { viewModel.updateAdminPin(newPin.ifBlank { null }); newPin = "" }, modifier = Modifier.weight(1f)) {
                    Text("SAVE PIN")
                }
                OutlinedButton(onClick = { viewModel.updateAdminPin(null); newPin = "" }, modifier = Modifier.weight(1f)) {
                    Text("REMOVE PIN")
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}
