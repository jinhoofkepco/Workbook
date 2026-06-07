package com.mathworkbook.app.core.backup

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.mathworkbook.app.core.database.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupExporter {
    private const val Tag = "BackupExporter"
    private const val DatabaseName = "math_workbook.db"
    private const val FilesRootName = "math_workbook"
    private const val LastBackupDateKey = "last_backup_date"
    private const val BackupPrefix = "math_workbook_backup_"
    private const val BackupSuffix = ".zip"
    private const val BackupMimeType = "application/zip"
    private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val BackupNameRegex = Regex("^math_workbook_backup_(\\d{4}-\\d{2}-\\d{2})\\.zip$")

    suspend fun runDailyBackupIfNeeded(
        context: Context,
        appPreferences: SharedPreferences,
        database: AppDatabase
    ) {
        val appContext = context.applicationContext
        try {
            val today = LocalDate.now().format(DateFormatter)
            if (appPreferences.getString(LastBackupDateKey, null) == today) return

            val success = exportNow(appContext, database, today)
            if (success) {
                appPreferences.edit().putString(LastBackupDateKey, today).apply()
            }
        } catch (error: Throwable) {
            Log.e(Tag, "Daily backup failed", error)
        }
    }

    suspend fun exportNow(
        context: Context,
        database: AppDatabase,
        dateString: String = LocalDate.now().format(DateFormatter)
    ): Boolean {
        val appContext = context.applicationContext
        val backupName = "$BackupPrefix$dateString$BackupSuffix"
        val tempZip = File(appContext.cacheDir, "$backupName.tmp")
        return try {
            checkpointWal(database)
            createBackupZip(appContext, tempZip)
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloadsWithMediaStore(appContext, backupName, tempZip)
            } else {
                saveToLegacyDownloads(appContext, backupName, tempZip)
            }
            if (saved) {
                pruneOldBackups(appContext)
            }
            saved
        } catch (error: Throwable) {
            Log.e(Tag, "Backup export failed", error)
            false
        } finally {
            runCatching { tempZip.delete() }
        }
    }

    private fun checkpointWal(database: AppDatabase) {
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
                while (it.moveToNext()) {
                    // Exhaust the cursor so SQLite completes the checkpoint.
                }
            }
        } catch (error: Throwable) {
            Log.e(Tag, "WAL checkpoint failed; backing up available database files", error)
        }
    }

    private fun createBackupZip(context: Context, target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(target, false)).use { zip ->
            val databaseFile = context.getDatabasePath(DatabaseName)
            addFileIfExists(zip, databaseFile, "databases/$DatabaseName")
            addFileIfExists(zip, File(databaseFile.parentFile, "$DatabaseName-wal"), "databases/$DatabaseName-wal")
            addFileIfExists(zip, File(databaseFile.parentFile, "$DatabaseName-shm"), "databases/$DatabaseName-shm")

            val filesRoot = File(context.filesDir, FilesRootName)
            addDirectoryRecursively(zip, filesRoot, "files/$FilesRootName")
        }
    }

    private fun addFileIfExists(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
    }

    private fun addDirectoryRecursively(zip: ZipOutputStream, directory: File, entryRoot: String) {
        if (!directory.exists()) return
        if (directory.isDirectory) {
            val children = directory.listFiles().orEmpty()
            if (children.isEmpty()) {
                zip.putNextEntry(ZipEntry("$entryRoot/"))
                zip.closeEntry()
            } else {
                children.forEach { child ->
                    addDirectoryRecursively(zip, child, "$entryRoot/${child.name}")
                }
            }
        } else if (directory.isFile) {
            addFileIfExists(zip, directory, entryRoot)
        }
    }

    private fun saveToLegacyDownloads(context: Context, backupName: String, tempZip: File): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Tag, "WRITE_EXTERNAL_STORAGE permission missing; skipping legacy backup")
            return false
        }
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadDir.mkdirs()
            FileOutputStream(File(downloadDir, backupName), false).use { output ->
                tempZip.inputStream().use { input -> input.copyTo(output) }
            }
            true
        } catch (error: Throwable) {
            Log.e(Tag, "Legacy downloads backup write failed", error)
            false
        }
    }

    private fun pruneOldBackups(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pruneOldMediaStoreBackups(context)
        } else {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            pruneOldLegacyBackups(downloadDir)
        }
    }

    private fun pruneOldLegacyBackups(downloadDir: File) {
        val backups = downloadDir.listFiles().orEmpty()
            .mapNotNull { file ->
                val date = backupDateOrNull(file.name) ?: return@mapNotNull null
                BackupFile(date = date, file = file)
            }
            .sortedByDescending { it.date }
        backups.drop(3).forEach { backup ->
            runCatching { backup.file.delete() }
                .onFailure { Log.e(Tag, "Failed to delete old backup: ${backup.file.name}", it) }
        }
    }

    private fun backupDateOrNull(name: String): LocalDate? {
        val dateText = BackupNameRegex.matchEntire(name)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { LocalDate.parse(dateText, DateFormatter) }.getOrNull()
    }

    private data class BackupFile(
        val date: LocalDate,
        val file: File
    )

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloadsWithMediaStore(context: Context, backupName: String, tempZip: File): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return try {
            findBackupUris(context, exactName = backupName).forEach { entry ->
                runCatching { resolver.delete(entry.uri, null, null) }
                    .onFailure { Log.e(Tag, "Failed to delete existing backup entry", it) }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backupName)
                put(MediaStore.MediaColumns.MIME_TYPE, BackupMimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return false
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    tempZip.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Cannot open output stream for backup")

                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
                true
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        } catch (error: Throwable) {
            Log.e(Tag, "MediaStore downloads backup write failed", error)
            false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun pruneOldMediaStoreBackups(context: Context) {
        val resolver = context.contentResolver
        val backups = findBackupUris(context)
            .mapNotNull { entry ->
                val date = backupDateOrNull(entry.name) ?: return@mapNotNull null
                MediaStoreBackup(date = date, uri = entry.uri, name = entry.name)
            }
            .sortedByDescending { it.date }

        backups.drop(3).forEach { backup ->
            runCatching { resolver.delete(backup.uri, null, null) }
                .onFailure { Log.e(Tag, "Failed to delete old backup: ${backup.name}", it) }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findBackupUris(context: Context, exactName: String? = null): List<MediaStoreBackupEntry> {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = buildString {
            append("${MediaStore.MediaColumns.RELATIVE_PATH} = ?")
            append(" AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
            if (exactName != null) {
                append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?")
            } else {
                append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
            }
        }
        val args = buildList {
            add("${Environment.DIRECTORY_DOWNLOADS}/")
            add(context.packageName)
            add(exactName ?: "$BackupPrefix%$BackupSuffix")
        }.toTypedArray()

        val entries = mutableListOf<MediaStoreBackupEntry>()
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)
                if (exactName == null && backupDateOrNull(name) == null) continue
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                entries += MediaStoreBackupEntry(uri = uri, name = name)
            }
        }
        return entries
    }

    private data class MediaStoreBackupEntry(
        val uri: Uri,
        val name: String
    )

    private data class MediaStoreBackup(
        val date: LocalDate,
        val uri: Uri,
        val name: String
    )
}
