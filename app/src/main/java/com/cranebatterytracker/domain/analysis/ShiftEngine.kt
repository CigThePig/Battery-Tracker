package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.model.RemoteState
import com.cranebatterytracker.domain.model.ShiftActivityLevel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Configurable shift schedule. Defaults match spec section 4 / 26.
 * Times are wall-clock local times; the night shift's ambiguous and
 * likely-inactive windows land on the following calendar date because the
 * night shift crosses midnight.
 */
data class ShiftSchedule(
    val dayStart: LocalTime = LocalTime.of(5, 0),
    val dayAmbiguousStart: LocalTime = LocalTime.of(13, 30),
    val dayAmbiguousEnd: LocalTime = LocalTime.of(14, 30),
    val nightStart: LocalTime = LocalTime.of(17, 0),
    val nightAmbiguousStart: LocalTime = LocalTime.of(1, 30),
    val nightAmbiguousEnd: LocalTime = LocalTime.of(2, 30),
    val workingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )
)

data class ShiftWindow(val startMillis: Long, val endMillis: Long, val level: ShiftActivityLevel)

/**
 * Classifies time intervals against the work schedule instead of treating
 * shift times as simple timers (spec section 26). Used both to decide
 * whether a remote's last-known battery is still "fresh" and to compute
 * minimum/maximum plausible active runtime across scheduled downtime.
 */
class ShiftEngine(
    private val schedule: ShiftSchedule = ShiftSchedule(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    private fun at(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()

    /** The six sub-windows anchored to [date]'s shifts, empty if [date] is not a working day. */
    private fun windowsForWorkingDate(date: LocalDate): List<ShiftWindow> {
        if (date.dayOfWeek !in schedule.workingDays) return emptyList()

        val nextDate = date.plusDays(1)
        val dayStart = at(date, schedule.dayStart)
        val dayAmbiguousStart = at(date, schedule.dayAmbiguousStart)
        val dayAmbiguousEnd = at(date, schedule.dayAmbiguousEnd)
        val nightStart = at(date, schedule.nightStart)
        val nightAmbiguousStart = at(nextDate, schedule.nightAmbiguousStart)
        val nightAmbiguousEnd = at(nextDate, schedule.nightAmbiguousEnd)
        val nextDayStart = at(nextDate, schedule.dayStart)

        return listOf(
            ShiftWindow(dayStart, dayAmbiguousStart, ShiftActivityLevel.DEFINITELY_ACTIVE),
            ShiftWindow(dayAmbiguousStart, dayAmbiguousEnd, ShiftActivityLevel.AMBIGUOUS),
            ShiftWindow(dayAmbiguousEnd, nightStart, ShiftActivityLevel.LIKELY_INACTIVE),
            ShiftWindow(nightStart, nightAmbiguousStart, ShiftActivityLevel.DEFINITELY_ACTIVE),
            ShiftWindow(nightAmbiguousStart, nightAmbiguousEnd, ShiftActivityLevel.AMBIGUOUS),
            ShiftWindow(nightAmbiguousEnd, nextDayStart, ShiftActivityLevel.LIKELY_INACTIVE)
        )
    }

    private fun windowsCovering(startMillis: Long, endMillis: Long): List<ShiftWindow> {
        val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate().minusDays(1)
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate().plusDays(1)
        val windows = mutableListOf<ShiftWindow>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            windows += windowsForWorkingDate(date)
            date = date.plusDays(1)
        }
        return windows
    }

    /** Which activity level a single instant falls into. Weekends and non-shift hours are LIKELY_INACTIVE. */
    fun classify(instant: Long): ShiftActivityLevel {
        val date = Instant.ofEpochMilli(instant).atZone(zoneId).toLocalDate()
        val candidates = windowsForWorkingDate(date) + windowsForWorkingDate(date.minusDays(1))
        return candidates.firstOrNull { instant >= it.startMillis && instant < it.endMillis }?.level
            ?: ShiftActivityLevel.LIKELY_INACTIVE
    }

    /** Sum of overlap between [startMillis, endMillis] and every window whose level is in [levels]. */
    fun activeOverlapMillis(startMillis: Long, endMillis: Long, levels: Set<ShiftActivityLevel>): Long {
        if (endMillis <= startMillis) return 0
        return windowsCovering(startMillis, endMillis)
            .filter { it.level in levels }
            .sumOf { overlapMillis(it.startMillis, it.endMillis, startMillis, endMillis) }
    }

    private fun overlapMillis(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long {
        val start = maxOf(aStart, bStart)
        val end = minOf(aEnd, bEnd)
        return maxOf(0L, end - start)
    }

    /** Minimum (definitely-active only) and maximum (definitely-active + ambiguous) plausible active runtime. */
    fun activeRuntimeRange(startMillis: Long, endMillis: Long): Pair<Long, Long> {
        val minimum = activeOverlapMillis(startMillis, endMillis, setOf(ShiftActivityLevel.DEFINITELY_ACTIVE))
        val maximum = activeOverlapMillis(
            startMillis, endMillis,
            setOf(ShiftActivityLevel.DEFINITELY_ACTIVE, ShiftActivityLevel.AMBIGUOUS)
        )
        return minimum to maximum
    }

    /** True when the whole interval sits inside definitely-active time with no crossing at all. */
    fun isFullyWithinDefinitelyActive(startMillis: Long, endMillis: Long): Boolean {
        val raw = endMillis - startMillis
        if (raw <= 0) return false
        val minimum = activeOverlapMillis(startMillis, endMillis, setOf(ShiftActivityLevel.DEFINITELY_ACTIVE))
        return minimum == raw
    }

    /** The most recent working-day shift-start boundary (day or night) at or before [nowMillis]. */
    fun mostRecentShiftBoundaryAtOrBefore(nowMillis: Long): Long? {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val boundaries = mutableListOf<Long>()
        var date = nowDate.minusDays(9)
        while (!date.isAfter(nowDate)) {
            if (date.dayOfWeek in schedule.workingDays) {
                boundaries += at(date, schedule.dayStart)
                boundaries += at(date, schedule.nightStart)
            }
            date = date.plusDays(1)
        }
        return boundaries.filter { it <= nowMillis }.maxOrNull()
    }

    /** A known state is stale once a new shift has started since it was last confirmed. */
    fun isStale(confirmedAtMillis: Long, nowMillis: Long): Boolean {
        val boundary = mostRecentShiftBoundaryAtOrBefore(nowMillis) ?: return false
        return confirmedAtMillis < boundary
    }

    fun toDisplayState(knowledge: RemoteKnowledge, nowMillis: Long): RemoteState = when (knowledge) {
        is RemoteKnowledge.Known -> if (isStale(knowledge.confirmedAt, nowMillis)) {
            RemoteState.Stale(knowledge.batteryId, knowledge.installedAt, knowledge.confirmedAt)
        } else {
            RemoteState.Confirmed(knowledge.batteryId, knowledge.installedAt, knowledge.confirmedAt)
        }

        is RemoteKnowledge.Unknown -> RemoteState.Unknown(knowledge.sinceAt)
    }

    /** True near the top of an active shift's start time, used to gate the shift-start banner. */
    fun isNearShiftStart(nowMillis: Long, toleranceMinutes: Long = 20): Boolean {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        if (date.dayOfWeek !in schedule.workingDays) return false
        val toleranceMillis = toleranceMinutes * 60_000L
        val dayStart = at(date, schedule.dayStart)
        val nightStart = at(date, schedule.nightStart)
        return (nowMillis in dayStart..(dayStart + toleranceMillis)) ||
            (nowMillis in nightStart..(nightStart + toleranceMillis))
    }
}
