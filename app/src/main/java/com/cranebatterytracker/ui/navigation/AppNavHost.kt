package com.cranebatterytracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cranebatterytracker.di.AppContainer
import kotlinx.coroutines.delay
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.ui.batterypicker.BatteryPickerScreen
import com.cranebatterytracker.ui.batterypicker.BatteryPickerViewModel
import com.cranebatterytracker.ui.common.SimpleViewModelFactory
import com.cranebatterytracker.ui.correction.CorrectionScreen
import com.cranebatterytracker.ui.correction.CorrectionViewModel
import com.cranebatterytracker.ui.diagnostics.BatteryDetailScreen
import com.cranebatterytracker.ui.diagnostics.DataQualityScreen
import com.cranebatterytracker.ui.diagnostics.DiagnosticsOverviewScreen
import com.cranebatterytracker.ui.diagnostics.DiagnosticsViewModel
import com.cranebatterytracker.ui.diagnostics.ExportScreen
import com.cranebatterytracker.ui.diagnostics.ExportViewModel
import com.cranebatterytracker.ui.diagnostics.RemoteComparisonScreen
import com.cranebatterytracker.ui.history.EventHistoryScreen
import com.cranebatterytracker.ui.main.MainScreen
import com.cranebatterytracker.ui.main.MainViewModel
import com.cranebatterytracker.ui.settings.SettingsScreen
import com.cranebatterytracker.ui.settings.SettingsViewModel

private const val DIAGNOSTICS_INACTIVITY_TIMEOUT_MILLIS = 3 * 60_000L

@Composable
fun AppNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        if (currentRoute == null || currentRoute == Routes.MAIN) return@LaunchedEffect
        while (true) {
            delay(10_000)
            if (System.currentTimeMillis() - lastInteractionAt >= DIAGNOSTICS_INACTIVITY_TIMEOUT_MILLIS) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastInteractionAt = System.currentTimeMillis()
                    }
                }
            }
    ) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            val viewModel: MainViewModel = viewModel(factory = SimpleViewModelFactory { MainViewModel(container) })
            MainScreen(
                viewModel = viewModel,
                onChangeBattery = { remoteId -> navController.navigate(Routes.picker(remoteId.name)) },
                onCorrectState = { remoteId -> navController.navigate(Routes.correction(remoteId.name)) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS_OVERVIEW) }
            )
        }

        composable(
            route = Routes.PICKER_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_REMOTE_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val remoteId = RemoteId.valueOf(backStackEntry.arguments?.getString(Routes.ARG_REMOTE_ID) ?: RemoteId.WEST.name)
            val viewModel: BatteryPickerViewModel = viewModel(
                factory = SimpleViewModelFactory { BatteryPickerViewModel(container, remoteId) }
            )
            BatteryPickerScreen(
                viewModel = viewModel,
                onCancel = { navController.popBackStack() },
                onSavedComplete = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CORRECTION_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_REMOTE_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val remoteId = RemoteId.valueOf(backStackEntry.arguments?.getString(Routes.ARG_REMOTE_ID) ?: RemoteId.WEST.name)
            val viewModel: CorrectionViewModel = viewModel(
                factory = SimpleViewModelFactory { CorrectionViewModel(container, remoteId) }
            )
            CorrectionScreen(
                viewModel = viewModel,
                onCancel = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }

        composable(Routes.DIAGNOSTICS_OVERVIEW) {
            val viewModel: DiagnosticsViewModel = viewModel(factory = SimpleViewModelFactory { DiagnosticsViewModel(container) })
            DiagnosticsOverviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenBatteryDetail = { batteryId -> navController.navigate(Routes.batteryDetail(batteryId)) },
                onOpenRemoteComparison = { navController.navigate(Routes.REMOTE_COMPARISON) },
                onOpenDataQuality = { navController.navigate(Routes.DATA_QUALITY) },
                onOpenHistory = { navController.navigate(Routes.EVENT_HISTORY) },
                onOpenExport = { navController.navigate(Routes.EXPORT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.BATTERY_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_BATTERY_ID) { type = NavType.IntType })
        ) { backStackEntry ->
            val batteryId = backStackEntry.arguments?.getInt(Routes.ARG_BATTERY_ID) ?: return@composable
            val viewModel: DiagnosticsViewModel = viewModel(factory = SimpleViewModelFactory { DiagnosticsViewModel(container) })
            BatteryDetailScreen(viewModel = viewModel, batteryId = batteryId, onBack = { navController.popBackStack() })
        }

        composable(Routes.REMOTE_COMPARISON) {
            val viewModel: DiagnosticsViewModel = viewModel(factory = SimpleViewModelFactory { DiagnosticsViewModel(container) })
            RemoteComparisonScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.DATA_QUALITY) {
            val viewModel: DiagnosticsViewModel = viewModel(factory = SimpleViewModelFactory { DiagnosticsViewModel(container) })
            DataQualityScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.EVENT_HISTORY) {
            val viewModel: DiagnosticsViewModel = viewModel(factory = SimpleViewModelFactory { DiagnosticsViewModel(container) })
            EventHistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.EXPORT) {
            val viewModel: ExportViewModel = viewModel(factory = SimpleViewModelFactory { ExportViewModel(container) })
            ExportScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = SimpleViewModelFactory { SettingsViewModel(container) })
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
    }
}
