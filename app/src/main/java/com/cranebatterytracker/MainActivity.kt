package com.cranebatterytracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cranebatterytracker.ui.navigation.AppNavHost
import com.cranebatterytracker.ui.theme.CraneBatteryTrackerTheme

/**
 * The tablet is a dedicated, permanently-mounted appliance (spec section
 * 74): portrait is locked in the manifest, and the screen is kept awake
 * here so the tracker is always visible at the charging station.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = (application as CraneBatteryTrackerApp).container

        setContent {
            CraneBatteryTrackerTheme {
                AppNavHost(container = container)
            }
        }
    }
}
