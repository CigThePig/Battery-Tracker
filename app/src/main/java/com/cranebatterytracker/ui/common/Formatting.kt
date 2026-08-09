package com.cranebatterytracker.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val clockFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val monthDayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatClockTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime().format(clockFormatter)

/** "Since 7:18 AM" style label used throughout the main screen. */
fun formatSinceLabel(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    "Since ${formatClockTime(epochMillis, zoneId)}"

fun formatDayHeader(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    val today = LocalDate.now(zoneId)
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(monthDayFormatter).uppercase()
    }
}

/** "5h 21m" / "48m" style duration label. Never shows a bare negative or absurd value silently. */
fun formatDurationHoursMinutes(millis: Long): String {
    val clamped = millis.coerceAtLeast(0)
    val totalMinutes = clamped / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatSignedPercent(percent: Double): String {
    val rounded = Math.round(percent)
    return if (rounded >= 0) "+${rounded}%" else "${rounded}%"
}
