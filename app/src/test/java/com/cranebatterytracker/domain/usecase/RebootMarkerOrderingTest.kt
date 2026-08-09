package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.RuntimeAnalysisEngine
import com.cranebatterytracker.domain.analysis.ShiftEngine
import com.cranebatterytracker.domain.analysis.ShiftSchedule
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification
import com.cranebatterytracker.testutil.FakeBatteryTrackerRepository
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The group-independent continuity marker BatteryChangeUseCase now writes alongside a
 * reboot-detecting battery change must not retroactively poison the very interval that
 * change just opened - only whatever was already open elsewhere at that instant.
 */
class RebootMarkerOrderingTest {

    private val zone = ZoneOffset.UTC
    private val runtimeEngine = RuntimeAnalysisEngine(ShiftEngine(ShiftSchedule(), zone))

    // 2024-01-01 is a Monday, a configured working day, so all three timestamps below fall
    // inside the day shift's definitely-active window (05:00-13:30).
    private fun millisAt(hour: Int, minute: Int): Long =
        LocalDate.of(2024, 1, 1).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `the battery installed by the reboot-detecting action itself can still become exact`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        // elapsedRealtimeMillis tracks real device uptime, so it must advance in step with
        // the wall clock except at the deliberate reboot dip below - otherwise the two
        // clocks would disagree by two hours and (correctly) register as a wall-clock
        // anomaly, which is not what this test is exercising.
        changeUseCase(RemoteId.WEST, 1, now = millisAt(6, 0), elapsedRealtimeMillis = 1_000_000)
        // Reboot: elapsedRealtime resets to a small post-boot value while the wall clock
        // continues normally two hours later. This both writes a group-independent marker
        // and opens battery 2's interval in the same action.
        changeUseCase(RemoteId.WEST, 2, now = millisAt(8, 0), elapsedRealtimeMillis = 5_000)
        // Battery 2 runs cleanly afterward, entirely on the post-reboot monotonic clock,
        // two more real hours later.
        changeUseCase(RemoteId.WEST, 3, now = millisAt(10, 0), elapsedRealtimeMillis = 5_000 + 2 * 3_600_000L)

        val cycles = runtimeEngine.deriveCycles(repository.allEvents())
        val battery2Cycle = cycles.single { it.batteryId == 2 }

        assertEquals(RuntimeClassification.EXACT, battery2Cycle.classification)
    }

    @Test
    fun `the interval opened by a reboot-detecting correction itself can still become exact`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val correctUseCase = CorrectStateUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = millisAt(6, 0), elapsedRealtimeMillis = 1_000_000)
        // A correction is the first interaction after the reboot instead of a normal
        // battery change - STATE_CORRECTED opens a new interval too, so it needs the same
        // marker-ordering protection as BATTERY_INSTALLED.
        correctUseCase(RemoteId.WEST, newBatteryId = 2, now = millisAt(8, 0), elapsedRealtimeMillis = 5_000)
        changeUseCase(RemoteId.WEST, 3, now = millisAt(10, 0), elapsedRealtimeMillis = 5_000 + 2 * 3_600_000L)

        val cycles = runtimeEngine.deriveCycles(repository.allEvents())
        val battery2Cycle = cycles.single { it.batteryId == 2 }

        assertEquals(RuntimeClassification.EXACT, battery2Cycle.classification)
    }
}
