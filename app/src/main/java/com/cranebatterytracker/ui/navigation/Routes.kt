package com.cranebatterytracker.ui.navigation

object Routes {
    const val MAIN = "main"

    const val PICKER_PATTERN = "picker/{remoteId}"
    fun picker(remoteId: String) = "picker/$remoteId"

    const val CORRECTION_PATTERN = "correction/{remoteId}"
    fun correction(remoteId: String) = "correction/$remoteId"

    const val DIAGNOSTICS_GRAPH = "diagnostics_graph"
    const val DIAGNOSTICS_OVERVIEW = "diagnostics/overview"

    const val BATTERY_DETAIL_PATTERN = "diagnostics/battery/{batteryId}"
    fun batteryDetail(batteryId: Int) = "diagnostics/battery/$batteryId"

    const val REMOTE_COMPARISON = "diagnostics/remotes"
    const val DATA_QUALITY = "diagnostics/quality"
    const val EVENT_HISTORY = "diagnostics/history"
    const val EXPORT = "diagnostics/export"
    const val SETTINGS = "diagnostics/settings"

    const val ARG_REMOTE_ID = "remoteId"
    const val ARG_BATTERY_ID = "batteryId"
}
