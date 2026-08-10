package com.cranebatterytracker.ui.correction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.ui.common.ErrorBanner
import com.cranebatterytracker.ui.theme.EastAccent
import com.cranebatterytracker.ui.theme.StatusGood
import com.cranebatterytracker.ui.theme.TrackerBackground
import com.cranebatterytracker.ui.theme.TrackerSurface
import com.cranebatterytracker.ui.theme.WestAccent
import kotlinx.coroutines.delay

@Composable
fun CorrectionScreen(
    viewModel: CorrectionViewModel,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        state.feedback?.let { feedback ->
            IntegrityReceipt(feedback = feedback, onDone = onDone)
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

            // Non-lazy 2-wide grid, not LazyVerticalGrid (spec review Issue 9): lazy grid
            // children aren't measured like ordinary bounded cells along the scrolling
            // axis, so these buttons could collapse toward their minimum size inside a
            // mostly-empty grid instead of filling the space glove-friendly targets need.
            // There will never be more than a handful of batteries.
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
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                Button(
                                    onClick = { viewModel.selectBattery(battery.batteryId) },
                                    enabled = !state.submitting,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(text = battery.displayNumber.toString(), style = MaterialTheme.typography.headlineLarge)
                                }
                            }
                        }
                        if (rowBatteries.size < 2) {
                            Column(modifier = Modifier.weight(1f)) {}
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

@Composable
private fun IntegrityReceipt(feedback: CorrectionFeedback, onDone: () -> Unit) {
    val accent = if (feedback.remoteId == com.cranebatterytracker.domain.model.RemoteId.WEST) WestAccent else EastAccent
    val haptics = LocalHapticFeedback.current
    val reveal = remember(feedback) { Animatable(0f) }

    LaunchedEffect(feedback) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reveal.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        delay(2_200)
        onDone()
    }

    val headline: String
    val detail: String
    val stateLine: String
    when (feedback.kind) {
        CorrectionFeedbackKind.CONFIRMED -> {
            headline = "STATE CONFIRMED"
            stateLine = "BATTERY ${feedback.newBatteryDisplayNumber} IS STILL IN ${feedback.remoteDisplayName.uppercase()}"
            detail = "This fresh confirmation extends a verified observation and protects shift tracking."
        }
        CorrectionFeedbackKind.CORRECTED -> {
            headline = "GOOD CATCH"
            stateLine = "${feedback.remoteDisplayName.uppercase()} NOW MATCHES BATTERY ${feedback.newBatteryDisplayNumber}"
            detail = "An inaccurate runtime was prevented. Trustworthy tracking starts from this correction."
        }
        CorrectionFeedbackKind.MARKED_UNKNOWN -> {
            headline = "NO GUESS ADDED"
            stateLine = "${feedback.remoteDisplayName.uppercase()} IS NOW MARKED UNKNOWN"
            detail = "Honest uncertainty protects the battery results until the real battery can be confirmed."
        }
        CorrectionFeedbackKind.COLLISION_RESOLVED -> {
            headline = "CONFLICT RESOLVED"
            stateLine = "BATTERY ${feedback.newBatteryDisplayNumber} CONFIRMED IN ${feedback.remoteDisplayName.uppercase()}"
            detail = "${feedback.otherRemoteShortName?.uppercase() ?: "THE OTHER REMOTE"} was marked unknown so one battery is never recorded in two places."
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), TrackerBackground, TrackerBackground))
        ).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 42f
                scaleX = 0.94f + reveal.value * 0.06f
                scaleY = 0.94f + reveal.value * 0.06f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = CircleShape, color = StatusGood.copy(alpha = 0.16f), border = BorderStroke(1.dp, StatusGood)) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = StatusGood,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Text(headline, style = MaterialTheme.typography.headlineLarge, color = StatusGood, textAlign = TextAlign.Center)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = TrackerSurface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = accent)
                    Text(stateLine, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = accent)
                    Text(detail, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
            Text(
                "Accurate corrections are valuable data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
