package com.cranebatterytracker.backup

import com.cranebatterytracker.domain.analysis.EventFiltering
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Raw-event and derived-cycle CSV export (spec sections 70-72). Raw events
 * are always exported in full, including undone ones, with an explicit
 * "undone" column - nothing is ever silently dropped from the export.
 */
object CsvExporter {

    private val timestampFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private fun formatTimestamp(epochMillis: Long?, zoneId: ZoneId): String =
        epochMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(timestampFormatter) } ?: ""

    private fun csvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuoting) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /**
     * Exports events in [DomainEvent.sequenceNumber] order - the same authoritative
     * replay order [EventFiltering] and [EventReducer] use - rather than by wall-clock
     * timestamp. A backward clock correction can make an event recorded later carry an
     * earlier [DomainEvent.timestampEpochMillis]; sorting by timestamp would let a
     * downstream tool reconstruct a different state than the app itself computes.
     */
    fun exportRawEvents(events: List<DomainEvent>, out: OutputStream, zoneId: ZoneId = ZoneId.systemDefault()) {
        val undoneGroups = EventFiltering.undoneActionGroupIds(events)
        OutputStreamWriter(out).use { writer ->
            writer.appendLine(
                listOf(
                    "sequence_number", "event_id", "action_group_id", "timestamp", "remote", "battery",
                    "event_type", "previous_battery", "new_battery", "target_action_group_id", "undone",
                    "wall_clock_anomaly_detected", "monotonic_continuity_broken", "elapsed_realtime_millis",
                    "created_by_app_version", "notes"
                ).joinToString(",")
            )
            events.sortedBy { it.sequenceNumber }.forEach { event ->
                // Mirrors EventFiltering.effectiveChronologicalEvents exactly: a
                // SYSTEM_TIME_WARNING is never treated as undone, even a legacy row
                // persisted by an older build whose actionGroupId still equals the
                // triggering action's now-undone group. Computing this any other way would
                // let a downstream reconstruction that trusts this column discard the only
                // evidence a clock/continuity anomaly happened.
                val undone = event.eventType != EventType.SYSTEM_TIME_WARNING &&
                    event.actionGroupId != null &&
                    event.actionGroupId in undoneGroups
                writer.appendLine(
                    listOf(
                        event.sequenceNumber.toString(),
                        event.eventId,
                        event.actionGroupId.orEmpty(),
                        formatTimestamp(event.timestampEpochMillis, zoneId),
                        event.remoteId?.name.orEmpty(),
                        event.batteryId?.toString().orEmpty(),
                        event.eventType.name,
                        event.previousBatteryId?.toString().orEmpty(),
                        event.newBatteryId?.toString().orEmpty(),
                        event.targetActionGroupId.orEmpty(),
                        undone.toString(),
                        event.wallClockAnomalyDetected.toString(),
                        // Without this, a downstream reconstruction of a log containing a
                        // reboot has no way to know RuntimeAnalysisEngine downgraded any
                        // cycles touching it - this is the only persisted fact that records
                        // that a monotonic-continuity gap happened at this event at all.
                        event.monotonicContinuityBroken.toString(),
                        event.elapsedRealtimeMillis?.toString().orEmpty(),
                        event.createdByAppVersion,
                        event.notes.orEmpty()
                    ).joinToString(",") { csvField(it) }
                )
            }
        }
    }

    fun exportDerivedCycles(cycles: List<DerivedCycle>, out: OutputStream, zoneId: ZoneId = ZoneId.systemDefault()) {
        OutputStreamWriter(out).use { writer ->
            writer.appendLine(
                listOf(
                    "cycle_id", "battery", "remote", "start_time", "end_time",
                    "minimum_runtime_minutes", "maximum_runtime_minutes", "classification",
                    "high_outlier", "short_runtime", "included_in_primary_statistics"
                ).joinToString(",")
            )
            cycles.sortedBy { it.startSequenceNumber }.forEach { cycle ->
                writer.appendLine(
                    listOf(
                        cycle.cycleId,
                        cycle.batteryId.toString(),
                        cycle.remoteId.name,
                        formatTimestamp(cycle.startTimestamp, zoneId),
                        formatTimestamp(cycle.endTimestamp, zoneId),
                        (cycle.minimumActiveRuntimeMillis / 60_000L).toString(),
                        (cycle.maximumActiveRuntimeMillis / 60_000L).toString(),
                        cycle.classification.name,
                        cycle.isHighOutlier.toString(),
                        cycle.isShortRuntimeEvent.toString(),
                        cycle.includedInPrimaryStatistics.toString()
                    ).joinToString(",") { csvField(it) }
                )
            }
        }
    }
}
