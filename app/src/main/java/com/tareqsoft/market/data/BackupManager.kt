package com.tareqsoft.market.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/**
 * Manual backup / restore of the whole database to a file chosen by the user
 * (e.g. Google Drive, USB, Downloads). This is the safety net until cloud sync
 * exists: uninstalling the app or "Clear data" would otherwise delete everything.
 */
class BackupManager(
    private val context: Context,
    private val db: MarketDatabase,
) {
    private val dbFile: File get() = context.getDatabasePath(MarketDatabase.NAME)

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        // Flush the write-ahead log into the main file so the copy is complete.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("تعذر فتح الملف")
    }

    /** Replaces the database with the backup file, then restarts the app. */
    suspend fun restoreFrom(uri: Uri) {
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "restore.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalStateException("تعذر قراءة الملف")

            val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
            if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                tmp.delete()
                throw IllegalArgumentException("الملف ليس نسخة احتياطية صالحة")
            }

            // Keep a copy of the current data in case the user picked the wrong file.
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val safety = File(context.filesDir, "backups").apply { mkdirs() }
            dbFile.copyTo(File(safety, "before-restore-${System.currentTimeMillis()}.db"), overwrite = true)

            db.close()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()
        }
        restartApp()
    }

    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent?.let { context.startActivity(it) }
        exitProcess(0)
    }
}
