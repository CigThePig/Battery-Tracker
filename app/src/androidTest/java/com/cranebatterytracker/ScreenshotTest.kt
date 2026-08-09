package com.cranebatterytracker

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test - a driver that walks the real app through every screen and saves
 * a screenshot of each, so a reviewer can see rendered UI without running an emulator
 * locally. Pulled off-device by the CI workflow after this test completes.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        composeTestRule.waitForIdle()
        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null)?.resolve("screenshots")
        dir?.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun settle() {
        composeTestRule.waitForIdle()
        // Room's Flow-backed state and the SAVED-confirmation auto-dismiss both resolve on
        // real wall-clock delays that Compose's idle check does not fast-forward.
        Thread.sleep(500)
        composeTestRule.waitForIdle()
    }

    /**
     * Touch injection right after a cold emulator boot is flaky ("Failed to inject touch
     * input") independent of anything the app does - retry with a short backoff rather
     * than let one transient input-framework hiccup fail the whole screenshot run.
     */
    private fun retryAction(block: () -> Unit) {
        var lastError: Throwable? = null
        repeat(5) {
            try {
                composeTestRule.waitForIdle()
                block()
                return
            } catch (t: Throwable) {
                lastError = t
                Thread.sleep(1000)
            }
        }
        throw lastError!!
    }

    private fun clickText(text: String) = retryAction { composeTestRule.onNodeWithText(text).performClick() }

    private fun clickAllText(text: String, index: Int = 0) =
        retryAction { composeTestRule.onAllNodesWithText(text)[index].performClick() }

    @Test
    fun captureAllScreens() {
        // Extra warm-up beyond settle(): give the emulator's window/input framework time to
        // stabilize after a cold boot before the very first touch, since that first tap is
        // where "Failed to inject touch input" is most likely to happen.
        Thread.sleep(3000)
        settle()
        screenshot("01_main_initial")

        // Set West's battery.
        clickAllText("SET\nBATTERY")
        settle()
        screenshot("02_battery_picker_west")
        clickText("1")
        settle()
        screenshot("03_battery_picker_saved")
        Thread.sleep(1300)
        settle()

        // Set East's battery.
        clickAllText("SET\nBATTERY")
        settle()
        screenshot("04_battery_picker_east")
        clickText("3")
        settle()
        Thread.sleep(1300)
        settle()
        screenshot("05_main_both_set")

        // Change West's battery again to produce a completed cycle and the recent-action bar.
        clickAllText("CHANGE\nBATTERY")
        settle()
        clickText("2")
        settle()
        Thread.sleep(1300)
        settle()
        screenshot("06_main_recent_action")

        // Correction screen, reached by tapping the current battery number.
        clickText("2")
        settle()
        screenshot("07_correction_screen")
        clickText("CANCEL")
        settle()

        // Diagnostics and every sub-screen reachable from it.
        clickText("BATTERY RESULTS")
        settle()
        screenshot("08_diagnostics_overview")

        clickText("BATTERY 1")
        settle()
        screenshot("09_battery_detail")
        clickText("← Back")
        settle()

        clickText("WEST VS EAST")
        settle()
        screenshot("10_remote_comparison")
        clickText("← Back")
        settle()

        clickText("DATA QUALITY")
        settle()
        screenshot("11_data_quality")
        clickText("← Back")
        settle()

        clickText("EVENT HISTORY")
        settle()
        screenshot("12_event_history")
        clickText("← Back")
        settle()

        clickText("EXPORT")
        settle()
        screenshot("13_export")
        clickText("← Back")
        settle()

        clickText("SETTINGS")
        settle()
        screenshot("14_settings")
    }
}
