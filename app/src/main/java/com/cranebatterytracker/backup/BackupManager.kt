package com.cranebatterytracker.backup

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.cranebatterytracker.data.database.AppDatabase
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Automatic local backups (spec section 69). Because the database is tiny,
 * retention can be generous: a rolling window of recent daily backups, plus
 * older backups thinned down into a longer-lived monthly archive.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val databaseFileName: String = "crane_battery_tracker.db"
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val dailyDir: File get() = File(context.filesDir, "backups/daily").apply { mkdirs() }
    private val archiveDir: File get() = File(context.filesDir, "backups/archive").apply { mkdirs() }

    suspend fun performDailyBackupIfNeeded(today: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        val targetFile = File(dailyDir, "${today.format(dateFormatter)}.db")
        if (targetFile.exists()) return@withContext

        // Flush the write-ahead log into the main database file before copying it,
        // otherwise a plain file copy can miss recently committed rows.
        database.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }

        val sourceFile = context.getDatabasePath(databaseFileName)
        if (!sourceFile.exists()) return@withContext

        // Copy to a temp file and only publish it under the real name once the copy has
        // fully succeeded. If the process dies mid-copy, the target path never exists,
        // so the next hourly check retries instead of treating a truncated file as done.
        val tempFile = File(dailyDir, "${today.format(dateFormatter)}.db.tmp")
        runCatching {
            sourceFile.copyTo(tempFile, overwrite = true)
            if (!tempFile.renameTo(targetFile)) {
                error("Could not publish backup for $today")
            }
        }.onFailure {
            tempFile.delete()
        }
    }

    suspend fun pruneBackups(dailyRetentionCount: Int, archiveRetentionCount: Int) = withContext(Dispatchers.IO) {
        val dailyBackups = dailyDir.listFiles { file -> file.extension == "db" }
            ?.sortedByDescending { it.nameWithoutExtension }
            ?: emptyList()

        if (dailyBackups.size > dailyRetentionCount) {
            val overflow = dailyBackups.drop(dailyRetentionCount)
            overflow.forEach { file ->
                // Archive by the backup's own calendar date, not its position in this
                // run's overflow batch - in steady state exactly one file ages out per
                // day, so a position-based cadence would archive either everything or
                // nothing depending on run timing. The 1st of each month gives a stable,
                // predictable monthly snapshot that survives however pruning is batched.
                val date = runCatching { LocalDate.parse(file.nameWithoutExtension, dateFormatter) }.getOrNull()
                if (date?.dayOfMonth == 1) {
                    file.copyTo(File(archiveDir, file.name), overwrite = true)
                }
                file.delete()
            }
        }

        val archiveBackups = archiveDir.listFiles { file -> file.extension == "db" }
            ?.sortedByDescending { it.nameWithoutExtension }
            ?: emptyList()
        if (archiveBackups.size > archiveRetentionCount) {
            archiveBackups.drop(archiveRetentionCount).forEach { it.delete() }
        }
    }

    fun listDailyBackups(): List<File> = dailyDir.listFiles { file -> file.extension == "db" }
        ?.sortedByDescending { it.nameWithoutExtension } ?: emptyList()

    fun listArchiveBackups(): List<File> = archiveDir.listFiles { file -> file.extension == "db" }
        ?.sortedByDescending { it.nameWithoutExtension } ?: emptyList()

    suspend fun exportDatabaseCopyTo(destination: java.io.OutputStream) = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
        val sourceFile = context.getDatabasePath(databaseFileName)
        sourceFile.inputStream().use { input -> input.copyTo(destination) }
    }
}
