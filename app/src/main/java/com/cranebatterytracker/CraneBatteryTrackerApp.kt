package com.cranebatterytracker

import android.app.Application
import com.cranebatterytracker.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CraneBatteryTrackerApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        applicationScope.launch {
            container.ensureSeedData()
            container.backupManager.performDailyBackupIfNeeded(LocalDate.now())
            val retention = container.settingsRepository.settings.first()
            container.backupManager.pruneBackups(
                retention.dailyBackupRetentionCount,
                retention.archiveBackupRetentionCount
            )
        }
    }
}
