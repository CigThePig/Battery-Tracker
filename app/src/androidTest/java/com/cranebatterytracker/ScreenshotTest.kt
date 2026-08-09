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

    @Test
    fun captureAllScreens() {
        settle()
        screenshot("01_main_initial")

        // Set West's battery.
        composeTestRule.onAllNodesWithText("SET\nBATTERY")[0].performClick()
        settle()
        screenshot("02_battery_picker_west")
        composeTestRule.onNodeWithText("1").performClick()
        settle()
        screenshot("03_battery_picker_saved")
        Thread.sleep(1300)
        settle()

        // Set East's battery.
        composeTestRule.onAllNodesWithText("SET\nBATTERY")[0].performClick()
        settle()
        screenshot("04_battery_picker_east")
        composeTestRule.onNodeWithText("3").performClick()
        settle()
        Thread.sleep(1300)
        settle()
        screenshot("05_main_both_set")

        // Change West's battery again to produce a completed cycle and the recent-action bar.
        composeTestRule.onAllNodesWithText("CHANGE\nBATTERY")[0].performClick()
        settle()
        composeTestRule.onNodeWithText("2").performClick()
        settle()
        Thread.sleep(1300)
        settle()
        screenshot("06_main_recent_action")

        // Correction screen, reached by tapping the current battery number.
        composeTestRule.onNodeWithText("2").performClick()
        settle()
        screenshot("07_correction_screen")
        composeTestRule.onNodeWithText("CANCEL").performClick()
        settle()

        // Diagnostics and every sub-screen reachable from it.
        composeTestRule.onNodeWithText("BATTERY RESULTS").performClick()
        settle()
        screenshot("08_diagnostics_overview")

        composeTestRule.onNodeWithText("BATTERY 1").performClick()
        settle()
        screenshot("09_battery_detail")
        composeTestRule.onNodeWithText("← Back").performClick()
        settle()

        composeTestRule.onNodeWithText("WEST VS EAST").performClick()
        settle()
        screenshot("10_remote_comparison")
        composeTestRule.onNodeWithText("← Back").performClick()
        settle()

        composeTestRule.onNodeWithText("DATA QUALITY").performClick()
        settle()
        screenshot("11_data_quality")
        composeTestRule.onNodeWithText("← Back").performClick()
        settle()

        composeTestRule.onNodeWithText("EVENT HISTORY").performClick()
        settle()
        screenshot("12_event_history")
        composeTestRule.onNodeWithText("← Back").performClick()
        settle()

        composeTestRule.onNodeWithText("EXPORT").performClick()
        settle()
        screenshot("13_export")
        composeTestRule.onNodeWithText("← Back").performClick()
        settle()

        composeTestRule.onNodeWithText("SETTINGS").performClick()
        settle()
        screenshot("14_settings")
    }
}
