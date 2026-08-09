package com.cranebatterytracker.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.domain.model.DataQualityLevel

@Composable
fun DataQualityScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val quality = snapshot?.dataQuality

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(text = "DATA QUALITY", style = MaterialTheme.typography.headlineMedium)

            quality?.let {
                Text(
                    text = qualityLabel(it.overallLevel),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                QualityRow("Exact cycles", it.exactCycles.toString())
                QualityRow("Shift-interrupted cycles", it.shiftInterruptedCycles.toString())
                QualityRow("Confirmed minimum observations", it.confirmedMinimumObservations.toString())
                QualityRow("Unknown gaps", it.unknownGaps.toString())
                QualityRow("Corrections", it.corrections.toString())
                QualityRow("Suspicious-high records", it.suspiciousHighCount.toString())

                Text(
                    text = "Weak evidence is always shown as weak evidence - the app never presents a strong " +
                        "conclusion when the underlying data is thin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun qualityLabel(level: DataQualityLevel): String = when (level) {
    DataQualityLevel.LIMITED -> "LIMITED"
    DataQualityLevel.DEVELOPING -> "DEVELOPING"
    DataQualityLevel.GOOD -> "GOOD"
    DataQualityLevel.STRONG -> "STRONG"
}
