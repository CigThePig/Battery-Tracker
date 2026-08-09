package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.testutil.testEvent
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFilteringTest {

    /**
     * Regression for a legacy-data gap: builds prior to this fix wrote SYSTEM_TIME_WARNING
     * with a non-null actionGroupId equal to the triggering action's group. Those
     * already-persisted rows must still survive undoing that historical action, even
     * though new warnings are written with a null actionGroupId instead.
     */
    @Test
    fun `a legacy warning sharing an undone action's group id still survives replay`() {
        val groupId = "legacy-group"
        val install = testEvent(
            actionGroupId = groupId,
            timestamp = 1_000,
            eventType = EventType.BATTERY_INSTALLED
        )
        val legacyWarning = testEvent(
            actionGroupId = groupId,
            timestamp = 1_000,
            eventType = EventType.SYSTEM_TIME_WARNING
        )
        val undo = testEvent(
            actionGroupId = "undo-group",
            timestamp = 2_000,
            eventType = EventType.UNDO_ACTION,
            targetActionGroupId = groupId
        )

        val effective = EventFiltering.effectiveChronologicalEvents(listOf(install, legacyWarning, undo))

        assertTrue(effective.none { it.eventType == EventType.BATTERY_INSTALLED })
        assertTrue(effective.any { it.eventType == EventType.SYSTEM_TIME_WARNING })
    }
}
