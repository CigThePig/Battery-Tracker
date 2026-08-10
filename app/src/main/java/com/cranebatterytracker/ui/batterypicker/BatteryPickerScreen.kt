package com.cranebatterytracker.ui.batterypicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cranebatterytracker.ui.common.ErrorBanner
import com.cranebatterytracker.ui.common.formatDurationHoursMinutes
import com.cranebatterytracker.ui.feedback.BatteryChangeFeedback
import com.cranebatterytracker.ui.feedback.EvidenceFeedbackFactory
import com.cranebatterytracker.ui.feedback.EvidenceFeedbackKind
import com.cranebatterytracker.ui.feedback.EvidenceMilestone
import com.cranebatterytracker.ui.theme.EastAccent
import com.cranebatterytracker.ui.theme.StatusGood
import com.cranebatterytracker.ui.theme.StatusWarn
import com.cranebatterytracker.ui.theme.StatusUnknown
import com.cranebatterytracker.ui.theme.WestAccent
import kotlinx.coroutines.delay

@Composable
fun BatteryPickerScreen(
    viewModel: BatteryPickerViewModel,
    onCancel: () -> Unit,
    onSavedComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val accent = if (state.remoteId == com.cranebatterytracker.domain.model.RemoteId.WEST) WestAccent else EastAccent

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.saved != null) {
            EvidenceReceipt(
                feedback = state.saved!!,
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
                color = accent,
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
            Text(
                text = "One tap records the change and strengthens the battery results.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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
                                accent = accent,
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
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val selectable = enabled && unavailableLabel == null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selectable) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, if (selectable) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            enabled = selectable,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) accent else MaterialTheme.colorScheme.surfaceVariant,
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
private fun EvidenceReceipt(feedback: BatteryChangeFeedback, onDone: () -> Unit) {
    val accent = if (feedback.remoteId == com.cranebatterytracker.domain.model.RemoteId.WEST) WestAccent else EastAccent
    val haptics = LocalHapticFeedback.current
    val reveal = remember(feedback) { Animatable(0f) }

    LaunchedEffect(feedback) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reveal.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        delay(if (feedback.isMilestone) 2_800 else 2_100)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.20f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .graphicsLayer {
                    alpha = reveal.value
                    scaleX = 0.92f + reveal.value * 0.08f
                    scaleY = 0.92f + reveal.value * 0.08f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = StatusGood.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, StatusGood.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = StatusGood)
                    Text("ENTRY RECORDED", style = MaterialTheme.typography.labelLarge, color = StatusGood)
                }
            }

            Text(
                text = feedback.remoteShortName.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = accent
            )

            BatteryTransferCard(feedback = feedback, accent = accent)
            EvidenceValueCard(feedback = feedback)

            AnimatedVisibility(
                visible = feedback.milestone != null,
                enter = fadeIn(tween(450, delayMillis = 350)) +
                    slideInVertically(tween(450, delayMillis = 350)) { it / 3 }
            ) {
                feedback.milestone?.let { MilestoneCard(it) }
            }

            Text(
                text = "Accurate entries build trustworthy battery results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BatteryTransferCard(feedback: BatteryChangeFeedback, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BATTERY CHANGE", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (feedback.fromDisplayNumber != null) {
                    BatteryTile(feedback.fromDisplayNumber, MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("→", style = MaterialTheme.typography.headlineLarge, color = accent, modifier = Modifier.padding(horizontal = 16.dp))
                }
                BatteryTile(feedback.toDisplayNumber, accent)
            }
            Text(
                text = "BATTERY ${feedback.toDisplayNumber} IS NOW TRACKING IN ${feedback.remoteShortName.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun BatteryTile(number: Int, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(number.toString(), style = MaterialTheme.typography.headlineLarge, color = color)
    }
}

@Composable
private fun EvidenceValueCard(feedback: BatteryChangeFeedback) {
    val title: String
    val detail: String
    val icon = when (feedback.kind) {
        EvidenceFeedbackKind.RELIABLE_RUN -> Icons.Rounded.Verified
        EvidenceFeedbackKind.PARTIAL_RUN, EvidenceFeedbackKind.RUN_HELD_FOR_REVIEW -> Icons.Rounded.QueryStats
        EvidenceFeedbackKind.TRACKING_STARTED -> Icons.Rounded.Bolt
    }
    val color = when (feedback.kind) {
        EvidenceFeedbackKind.RELIABLE_RUN -> StatusGood
        EvidenceFeedbackKind.PARTIAL_RUN, EvidenceFeedbackKind.RUN_HELD_FOR_REVIEW -> StatusWarn
        EvidenceFeedbackKind.TRACKING_STARTED -> MaterialTheme.colorScheme.primary
    }

    when (feedback.kind) {
        EvidenceFeedbackKind.RELIABLE_RUN -> {
            title = "${formatDurationHoursMinutes(feedback.runtimeMillis ?: 0L).uppercase()} RELIABLE RUN"
            detail = "Battery ${feedback.batteryDisplayNumber} now has ${feedback.reliableCycleCount} reliable " +
                if (feedback.reliableCycleCount == 1) "measurement." else "measurements."
        }
        EvidenceFeedbackKind.PARTIAL_RUN -> {
            title = "USEFUL PARTIAL RUN CAPTURED"
            detail = partialRuntimeDetail(feedback)
        }
        EvidenceFeedbackKind.RUN_HELD_FOR_REVIEW -> {
            title = "RUN CAPTURED FOR REVIEW"
            detail = "This unusually long result was preserved, but it will not distort the battery's typical runtime."
        }
        EvidenceFeedbackKind.TRACKING_STARTED -> {
            title = "ACCURATE TRACKING STARTED"
            detail = "This confirmed state gives the next battery change a trustworthy starting point."
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp)
            )

            if (feedback.kind == EvidenceFeedbackKind.RELIABLE_RUN &&
                feedback.reliableCycleCount < EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET
            ) {
                LinearProgressIndicator(
                    progress = feedback.reliableCycleCount.toFloat() / EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(7.dp).clip(CircleShape),
                    color = StatusGood,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${EvidenceFeedbackFactory.BASELINE_CYCLE_TARGET - feedback.reliableCycleCount} MORE TO FIRST BASELINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusGood,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }
    }
}

private fun partialRuntimeDetail(feedback: BatteryChangeFeedback): String {
    val minimum = feedback.minimumRuntimeMillis ?: return "The observation was saved without pretending it was exact."
    val maximum = feedback.maximumRuntimeMillis ?: minimum
    return if (minimum == maximum) {
        "${formatDurationHoursMinutes(minimum)} was observed. The shift boundary keeps it out of the exact average."
    } else {
        "${formatDurationHoursMinutes(minimum)}–${formatDurationHoursMinutes(maximum)} active runtime was preserved without guessing."
    }
}

@Composable
private fun MilestoneCard(milestone: EvidenceMilestone) {
    val title = when (milestone) {
        is EvidenceMilestone.FirstReliableRun -> "FIRST RELIABLE RUN"
        is EvidenceMilestone.BaselineEstablished -> "BATTERY ${milestone.batteryDisplayNumber} BASELINE ESTABLISHED"
        is EvidenceMilestone.QualityImproved -> "BATTERY DATA IMPROVED TO ${milestone.level.name}"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StatusGood.copy(alpha = 0.18f),
        border = BorderStroke(2.dp, StatusGood)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Verified, contentDescription = null, tint = StatusGood)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = StatusGood,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
