package com.cranebatterytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TrackerDarkColorScheme = darkColorScheme(
    primary = WestAccent,
    secondary = EastAccent,
    background = TrackerBackground,
    surface = TrackerSurface,
    surfaceVariant = TrackerSurfaceVariant,
    onBackground = TrackerOnBackground,
    onSurface = TrackerOnBackground,
    error = StatusBad
)

private val TrackerLightColorScheme = lightColorScheme(
    primary = WestAccent,
    secondary = EastAccent,
    background = TrackerOnBackground,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = TrackerBackground,
    onSurface = TrackerBackground,
    error = StatusBad
)

@Composable
fun CraneBatteryTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TrackerDarkColorScheme else TrackerLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrackerTypography,
        content = content
    )
}
