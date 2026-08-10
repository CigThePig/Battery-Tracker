package com.cranebatterytracker.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
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
import com.cranebatterytracker.ui.theme.StatusGood
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.StatusWarn

@Composable
fun DataQualityScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val quality = snapshot?.dataQuality

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(text = "DATA QUALITY", style = MaterialTheme.typography.headlineMedium)

            quality?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = StatusGood.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, StatusGood.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Verified, contentDescription = null, tint = StatusGood)
                            Text(
                                text = qualityLabel(it.overallLevel),
                                style = MaterialTheme.typography.titleLarge,
                                color = StatusGood,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(
                            text = qualityExplanation(it.overallLevel),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Text("WHAT ACCURATE ENTRIES HAVE BUILT", style = MaterialTheme.typography.titleMedium)
                QualityRow("Exact runs captured", it.exactCycles.toString(), StatusGood)
                QualityRow("Useful partial runs", it.shiftInterruptedCycles.toString(), StatusWarn)
                QualityRow("Confirmed minimum observations", it.confirmedMinimumObservations.toString(), StatusWarn)

                Text("DATA PROTECTION", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                QualityRow("Honest unknown gaps", it.unknownGaps.toString(), StatusUnknown)
                QualityRow("Corrections that protected accuracy", it.corrections.toString(), StatusGood)
                QualityRow("Unusual records held for review", it.suspiciousHighCount.toString(), StatusUnknown)

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
private fun QualityRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

private fun qualityExplanation(level: DataQualityLevel): String = when (level) {
    DataQualityLevel.LIMITED -> "The foundation is being built. Each complete battery change adds evidence the app can trust."
    DataQualityLevel.DEVELOPING -> "Patterns are beginning to form, but more accurate runs are needed before making expensive decisions."
    DataQualityLevel.GOOD -> "The fleet now has useful evidence for comparing batteries and looking for repeatable problems."
    DataQualityLevel.STRONG -> "The battery conclusions are backed by a strong history of accurate, complete observations."
}

private fun qualityLabel(level: DataQualityLevel): String = when (level) {
    DataQualityLevel.LIMITED -> "LIMITED"
    DataQualityLevel.DEVELOPING -> "DEVELOPING"
    DataQualityLevel.GOOD -> "GOOD"
    DataQualityLevel.STRONG -> "STRONG"
}
