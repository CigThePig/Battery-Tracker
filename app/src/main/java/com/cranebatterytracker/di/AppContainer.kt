package com.cranebatterytracker.di

import android.content.Context
import com.cranebatterytracker.BuildConfig
import com.cranebatterytracker.backup.BackupManager
import com.cranebatterytracker.data.database.AppDatabase
import com.cranebatterytracker.data.database.SeedData
import com.cranebatterytracker.data.repository.BatteryTrackerRepositoryImpl
import com.cranebatterytracker.data.settings.AppSettings
import com.cranebatterytracker.data.settings.SettingsRepository
import com.cranebatterytracker.domain.analysis.DiagnosticEngine
import com.cranebatterytracker.domain.analysis.RuntimeAnalysisEngine
import com.cranebatterytracker.domain.analysis.ShiftEngine
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import com.cranebatterytracker.domain.usecase.BatteryChangeUseCase
import com.cranebatterytracker.domain.usecase.ConfirmStateUseCase
import com.cranebatterytracker.domain.usecase.CorrectStateUseCase
import com.cranebatterytracker.domain.usecase.MarkUnknownUseCase
import com.cranebatterytracker.domain.usecase.UndoUseCase

/**
 * Hand-rolled dependency container. The app is small enough that a full DI
 * framework would add more ceremony than it saves; every dependency here is
 * a simple, explicit constructor call.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appVersion: String = BuildConfig.VERSION_NAME

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val repository: BatteryTrackerRepository by lazy { BatteryTrackerRepositoryImpl(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val backupManager: BackupManager by lazy { BackupManager(appContext, database) }
    val diagnosticEngine: DiagnosticEngine by lazy { DiagnosticEngine() }

    val batteryChangeUseCase: BatteryChangeUseCase by lazy { BatteryChangeUseCase(repository, appVersion) }
    val correctStateUseCase: CorrectStateUseCase by lazy { CorrectStateUseCase(repository, appVersion) }
    val confirmStateUseCase: ConfirmStateUseCase by lazy { ConfirmStateUseCase(repository, appVersion) }
    val markUnknownUseCase: MarkUnknownUseCase by lazy { MarkUnknownUseCase(repository, appVersion) }
    val undoUseCase: UndoUseCase by lazy { UndoUseCase(repository, appVersion) }

    fun shiftEngine(settings: AppSettings): ShiftEngine = ShiftEngine(settings.toShiftSchedule())

    fun runtimeAnalysisEngine(settings: AppSettings): RuntimeAnalysisEngine =
        RuntimeAnalysisEngine(shiftEngine(settings))

    suspend fun ensureSeedData() {
        SeedData.ensureSeeded(database.batteryDao(), database.remoteDao())
    }
}
