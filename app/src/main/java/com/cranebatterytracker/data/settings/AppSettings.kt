package com.cranebatterytracker.data.settings

import com.cranebatterytracker.domain.analysis.ShiftSchedule
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * User/admin-configurable settings (spec section 78). Normal tracking never
 * needs these; they live behind Diagnostics -> Settings and an optional PIN.
 */
data class AppSettings(
    val dayStart: LocalTime = LocalTime.of(5, 0),
    val dayAmbiguousStart: LocalTime = LocalTime.of(13, 30),
    val dayAmbiguousEnd: LocalTime = LocalTime.of(14, 30),
    val nightStart: LocalTime = LocalTime.of(17, 0),
    val nightAmbiguousStart: LocalTime = LocalTime.of(1, 30),
    val nightAmbiguousEnd: LocalTime = LocalTime.of(2, 30),
    val workingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    ),
    val adminPin: String? = null,
    val dailyBackupRetentionCount: Int = 14,
    val archiveBackupRetentionCount: Int = 8,
    val westDisplayName: String = "West / Front Crane",
    val eastDisplayName: String = "East / Back Crane"
) {
    fun toShiftSchedule(): ShiftSchedule = ShiftSchedule(
        dayStart = dayStart,
        dayAmbiguousStart = dayAmbiguousStart,
        dayAmbiguousEnd = dayAmbiguousEnd,
        nightStart = nightStart,
        nightAmbiguousStart = nightAmbiguousStart,
        nightAmbiguousEnd = nightAmbiguousEnd,
        workingDays = workingDays
    )
}
