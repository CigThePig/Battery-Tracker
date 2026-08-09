package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.testutil.testBatteryChange
import com.cranebatterytracker.testutil.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventReducerTest {

    @Test
    fun `initial state is unknown for both remotes`() {
        val result = EventReducer.reduce(emptyList())
        assertTrue(result[RemoteId.WEST] is RemoteKnowledge.Unknown)
        assertTrue(result[RemoteId.EAST] is RemoteKnowledge.Unknown)
    }

    @Test
    fun `install battery sets known state`() {
        val events = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 2)
        val result = EventReducer.reduce(events)
        val known = result.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(2, known.batteryId)
        assertEquals(1_000L, known.installedAt)
        assertEquals(1_000L, known.confirmedAt)
    }

    @Test
    fun `confirm battery refreshes confidence without moving install time`() {
        val install = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 2)
        val confirm = testEvent(timestamp = 5_000, remoteId = RemoteId.WEST, batteryId = 2, eventType = EventType.STATE_CONFIRMED)
        val result = EventReducer.reduce(install + confirm)
        val known = result.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(1_000L, known.installedAt)
        assertEquals(5_000L, known.confirmedAt)
    }

    @Test
    fun `correct battery creates a fresh anchor and replaces identity`() {
        val install = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 2)
        val correction = testEvent(
            timestamp = 9_000,
            remoteId = RemoteId.WEST,
            batteryId = 4,
            eventType = EventType.STATE_CORRECTED,
            previousBatteryId = 2,
            newBatteryId = 4
        )
        val result = EventReducer.reduce(install + correction)
        val known = result.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(4, known.batteryId)
        assertEquals(9_000L, known.installedAt)
    }

    @Test
    fun `mark unknown clears known state`() {
        val install = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 2)
        val markUnknown = testEvent(timestamp = 9_000, remoteId = RemoteId.WEST, eventType = EventType.STATE_MARKED_UNKNOWN)
        val result = EventReducer.reduce(install + markUnknown)
        val unknown = result.getValue(RemoteId.WEST) as RemoteKnowledge.Unknown
        assertEquals(9_000L, unknown.sinceAt)
    }

    @Test
    fun `undo installation reverts to the prior state`() {
        val firstInstall = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 2)
        val secondGroupId = "group-2"
        val secondInstall = testBatteryChange(
            timestamp = 5_000, remoteId = RemoteId.WEST, oldBatteryId = 2, newBatteryId = 3, groupId = secondGroupId
        )
        val undo = testEvent(timestamp = 5_500, eventType = EventType.UNDO_ACTION, targetActionGroupId = secondGroupId)

        val result = EventReducer.reduce(firstInstall + secondInstall + undo)
        val known = result.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(2, known.batteryId)
    }

    @Test
    fun `undo correction reverts to the prior state`() {
        val install = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 2)
        val correctionGroupId = "correction-group"
        val correction = testEvent(
            eventId = "correction-event",
            actionGroupId = correctionGroupId,
            timestamp = 9_000,
            remoteId = RemoteId.WEST,
            batteryId = 4,
            eventType = EventType.STATE_CORRECTED,
            previousBatteryId = 2,
            newBatteryId = 4
        )
        val undo = testEvent(timestamp = 9_500, eventType = EventType.UNDO_ACTION, targetActionGroupId = correctionGroupId)

        val result = EventReducer.reduce(install + correction + undo)
        val known = result.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(2, known.batteryId)
    }

    @Test
    fun `event ordering does not depend on insertion order`() {
        val events = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 1) +
            testBatteryChange(timestamp = 2_000, remoteId = RemoteId.WEST, oldBatteryId = 1, newBatteryId = 2) +
            testBatteryChange(timestamp = 3_000, remoteId = RemoteId.WEST, oldBatteryId = 2, newBatteryId = 3)

        val inOrder = EventReducer.reduce(events)
        val shuffled = EventReducer.reduce(events.shuffled(java.util.Random(42)))

        assertEquals(inOrder, shuffled)
        val known = inOrder.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(3, known.batteryId)
    }

    @Test
    fun `replaying the same event log always reconstructs the same state`() {
        val events = testBatteryChange(timestamp = 1_000, remoteId = RemoteId.WEST, oldBatteryId = null, newBatteryId = 1) +
            testBatteryChange(timestamp = 2_000, remoteId = RemoteId.EAST, oldBatteryId = null, newBatteryId = 4)

        val first = EventReducer.reduce(events)
        val second = EventReducer.reduce(events)

        assertEquals(first, second)
    }

    @Test
    fun `a backward clock correction does not reorder events by wall-clock time`() {
        // Battery 1 installed at 10:00 (real, higher sequence number).
        val firstInstall = testEvent(
            sequenceNumber = 1,
            timestamp = 10_000,
            remoteId = RemoteId.WEST,
            batteryId = 1,
            eventType = EventType.BATTERY_INSTALLED,
            newBatteryId = 1
        )
        // The operator then corrects the tablet's clock backward to 09:00 before the
        // next real action, so this change is recorded later (sequence 2) but stamped
        // with an earlier wall-clock timestamp.
        val groupId = "later-group"
        val secondChange = listOf(
            testEvent(
                sequenceNumber = 2,
                actionGroupId = groupId,
                timestamp = 9_000,
                remoteId = RemoteId.WEST,
                batteryId = 1,
                eventType = EventType.BATTERY_REMOVED_DEAD,
                previousBatteryId = 1
            ),
            testEvent(
                sequenceNumber = 3,
                actionGroupId = groupId,
                timestamp = 9_000,
                remoteId = RemoteId.WEST,
                batteryId = 2,
                eventType = EventType.BATTERY_INSTALLED,
                previousBatteryId = 1,
                newBatteryId = 2
            )
        )

        // Sorting by wall-clock time would replay the 10:00 install last, incorrectly
        // landing back on Battery 1. Replay must follow the real insertion order instead.
        val result = EventReducer.reduce(listOf(firstInstall) + secondChange)
        val known = result.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(2, known.batteryId)
    }
}
