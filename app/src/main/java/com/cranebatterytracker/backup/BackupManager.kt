package com.cranebatterytracker.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.cranebatterytracker.data.database.AppDatabase
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diagnostic-facing summary of automatic backup health, persisted across process restarts. */
data class BackupStatus(
    val lastSuccessAtMillis: Long?,
    val lastAttemptAtMillis: Long?,
    val lastFailureMessage: String?
) {
    companion object {
        val UNKNOWN = BackupStatus(null, null, null)
    }
}

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
    private val statusFile: File get() = File(context.filesDir, "backups/status.properties")

    /**
     * Flushes the write-ahead log into the main database file and reports whether the
     * checkpoint actually fully drained it. SQLite can report the checkpoint as "busy"
     * (a concurrent reader/writer blocked a full flush) in the returned result row
     * without throwing an exception - silently ignoring that, as a bare `moveToFirst()`
     * effectively did, can let committed rows still sitting in the WAL be missing from a
     * plain copy of the main .db file.
     */
    private fun checkpointFully(): Boolean =
        database.openHelper.writableDatabase
            .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
            .use { cursor ->
                // Row shape is (busy, log_frames, checkpointed_frames). busy != 0 means some
                // frames could not be checkpointed; checkpointed < log means the same thing
                // even when busy happens to read 0.
                if (!cursor.moveToFirst()) return@use false
                val busy = cursor.getInt(0)
                val logFrames = cursor.getInt(1)
                val checkpointedFrames = cursor.getInt(2)
                busy == 0 && checkpointedFrames >= logFrames
            }

    /** Opens a copied database read-only and confirms it is actually a recoverable snapshot. */
    private fun verifyBackupIntegrity(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val ok = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            if (!ok) return@runCatching false
            // Confirm the table the whole app depends on actually made it into the copy.
            db.rawQuery("SELECT COUNT(*) FROM events", null).use { cursor -> cursor.moveToFirst() }
        }
    }.getOrDefault(false)

    suspend fun performDailyBackupIfNeeded(today: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        val targetFile = File(dailyDir, "${today.format(dateFormatter)}.db")
        if (targetFile.exists()) return@withContext

        if (!checkpointFully()) {
            recordStatus(success = false, message = "WAL checkpoint did not fully complete; will retry later.")
            return@withContext
        }

        val sourceFile = context.getDatabasePath(databaseFileName)
        if (!sourceFile.exists()) {
            recordStatus(success = false, message = "Database file not found.")
            return@withContext
        }

        // Copy to a temp file and only publish it under the real name once the copy has
        // fully succeeded and passed an integrity check. If the process dies mid-copy, or
        // the copy is corrupt, the target path never exists, so the next hourly check
        // retries instead of treating a truncated/broken file as done.
        val tempFile = File(dailyDir, "${today.format(dateFormatter)}.db.tmp")
        val result = runCatching {
            sourceFile.copyTo(tempFile, overwrite = true)
            if (!verifyBackupIntegrity(tempFile)) {
                error("Backup copy for $today failed integrity verification")
            }
            if (!tempFile.renameTo(targetFile)) {
                error("Could not publish backup for $today")
            }
        }
        result.onFailure { tempFile.delete() }
        recordStatus(success = result.isSuccess, message = result.exceptionOrNull()?.message)
    }

    /**
     * Thins the daily window down while guaranteeing every calendar month keeps exactly
     * one representative snapshot in the long-lived archive - not only months where a
     * backup happened to exist on the 1st. If the tablet was off, the app wasn't running,
     * or a backup failed around the start of a month, the earliest daily backup that
     * later ages out of that month becomes its permanent snapshot instead of the month
     * being skipped entirely.
     */
    suspend fun pruneBackups(dailyRetentionCount: Int, archiveRetentionCount: Int) = withContext(Dispatchers.IO) {
        val dailyBackups = dailyDir.listFiles { file -> file.extension == "db" }
            ?.sortedByDescending { it.nameWithoutExtension }
            ?: emptyList()

        if (dailyBackups.size > dailyRetentionCount) {
            val overflow = dailyBackups.drop(dailyRetentionCount)
            val archivedMonths = archiveDir.listFiles { file -> file.extension == "db" }
                ?.mapNotNull { file -> backupDate(file)?.let { it.year to it.monthValue } }
                ?.toMutableSet()
                ?: mutableSetOf()

            // Oldest-first: if several backups from the same month age out in a single
            // pruning pass (e.g. after the tablet was off for a while), the earliest
            // available day in that month becomes the permanent monthly snapshot.
            overflow.sortedBy { it.nameWithoutExtension }.forEach { file ->
                val monthKey = backupDate(file)?.let { it.year to it.monthValue }
                val needsArchiving = monthKey != null && monthKey !in archivedMonths
                // publishArchiveCopy is atomic (temp file + rename), so a failed/interrupted
                // copy never leaves a truncated .db sitting in the archive directory. If it
                // does fail, this month is deliberately left un-archived and this daily
                // source is kept (not deleted) so the next prune retries instead of
                // permanently losing the only remaining copy for that month.
                val archived = !needsArchiving || publishArchiveCopy(file, file.name)
                if (archived) {
                    if (needsArchiving) archivedMonths += monthKey!!
                    file.delete()
                }
            }
        }

        val archiveBackups = archiveDir.listFiles { file -> file.extension == "db" }
            ?.sortedByDescending { it.nameWithoutExtension }
            ?: emptyList()
        if (archiveBackups.size > archiveRetentionCount) {
            archiveBackups.drop(archiveRetentionCount).forEach { it.delete() }
        }
    }

    private fun backupDate(file: File): LocalDate? =
        runCatching { LocalDate.parse(file.nameWithoutExtension, dateFormatter) }.getOrNull()

    /**
     * Copies [source] into the archive under [targetName] through a temp file, publishing
     * it only via an atomic rename once the copy has fully succeeded. Without this, a
     * `copyTo` that fails partway (e.g. disk full) can leave a truncated `.db` at the
     * final archive path; on the next prune that truncated file would already look like a
     * valid archived snapshot for its month, so the copy is never retried and the source
     * daily backup that could have provided a good copy gets deleted anyway - permanently
     * losing that month's archive.
     */
    private fun publishArchiveCopy(source: File, targetName: String): Boolean {
        val target = File(archiveDir, targetName)
        val temp = File(archiveDir, "$targetName.tmp")
        return runCatching {
            source.copyTo(temp, overwrite = true)
            if (!temp.renameTo(target)) error("Could not publish archive copy for $targetName")
            true
        }.onFailure { temp.delete() }.getOrDefault(false)
    }

    fun listDailyBackups(): List<File> = dailyDir.listFiles { file -> file.extension == "db" }
        ?.sortedByDescending { it.nameWithoutExtension } ?: emptyList()

    fun listArchiveBackups(): List<File> = archiveDir.listFiles { file -> file.extension == "db" }
        ?.sortedByDescending { it.nameWithoutExtension } ?: emptyList()

    suspend fun exportDatabaseCopyTo(destination: java.io.OutputStream) = withContext(Dispatchers.IO) {
        check(checkpointFully()) { "Could not flush the write-ahead log to a stable point; try again shortly." }
        val sourceFile = context.getDatabasePath(databaseFileName)
        sourceFile.inputStream().use { input -> input.copyTo(destination) }
    }

    /** Best-effort, defensively-parsed status surfaced in Diagnostics; never throws. */
    suspend fun readStatus(): BackupStatus = withContext(Dispatchers.IO) {
        runCatching {
            if (!statusFile.exists()) return@runCatching BackupStatus.UNKNOWN
            val props = Properties().apply { statusFile.inputStream().use { load(it) } }
            BackupStatus(
                lastSuccessAtMillis = props.getProperty("lastSuccessAtMillis")?.toLongOrNull(),
                lastAttemptAtMillis = props.getProperty("lastAttemptAtMillis")?.toLongOrNull(),
                lastFailureMessage = props.getProperty("lastFailureMessage")?.takeIf { it.isNotBlank() }
            )
        }.getOrDefault(BackupStatus.UNKNOWN)
    }

    private fun recordStatus(success: Boolean, message: String?, nowMillis: Long = System.currentTimeMillis()) {
        runCatching {
            val previous = runCatching {
                if (!statusFile.exists()) null else Properties().apply { statusFile.inputStream().use { load(it) } }
            }.getOrNull()
            val previousSuccessAt = previous?.getProperty("lastSuccessAtMillis")

            val props = Properties()
            props.setProperty("lastAttemptAtMillis", nowMillis.toString())
            props.setProperty("lastSuccessAtMillis", if (success) nowMillis.toString() else previousSuccessAt.orEmpty())
            props.setProperty("lastFailureMessage", if (success) "" else (message ?: "Unknown backup failure"))
            statusFile.parentFile?.mkdirs()
            statusFile.outputStream().use { props.store(it, null) }
        }
    }
}
