/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.vivi.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.music.vivi.BuildConfig
import com.music.vivi.constants.AutoBackupLocationKey
import com.music.vivi.db.InternalDatabase
import com.music.vivi.viewmodels.BackupRestoreViewModel
import com.music.vivi.extensions.div
import com.music.vivi.extensions.zipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry

object AutoBackupHelper {
    private const val WEEKLY_WORK_NAME = "weekly_auto_backup"
    private const val BACKUP_MIME_TYPE = "application/octet-stream"

    data class AutoBackupEntry(
        val name: String,
        val lastModified: Long,
        val size: Long,
        val file: File? = null,
        val uri: Uri? = null,
    )

    data class BackupLocation(
        val treeUri: Uri,
        val displayName: String?,
        val isAvailable: Boolean,
    )

    fun getBackupDir(context: Context): File {
        val dir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(null), "backups")
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            File(downloadsDir, "vivimusic")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getBackupLocation(context: Context, locationUri: String): BackupLocation? {
        if (locationUri.isBlank()) return null

        val treeUri = runCatching { Uri.parse(locationUri) }.getOrNull() ?: return null
        val hasWritePermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        val metadata = getTreeMetadata(context, treeUri)
        return BackupLocation(
            treeUri = treeUri,
            displayName = metadata.name,
            isAvailable = hasWritePermission && metadata.isAccessible,
        )
    }

    fun performBackup(context: Context, backupType: String): Boolean {
        val database = InternalDatabase.newInstance(context)
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            val timestamp = LocalDateTime.now().format(formatter)
            val fileName = if (backupType == "before_update") {
                "auto_backup_before_update_${BuildConfig.VERSION_NAME}_$timestamp.backup"
            } else {
                "auto_backup_${backupType}_$timestamp.backup"
            }
            val tempFile = File(context.cacheDir, fileName)

            FileOutputStream(tempFile).use { fos ->
                fos.buffered().zipOutputStream().use { outputStream ->
                    val settingsFile = context.filesDir / "datastore" / BackupRestoreViewModel.SETTINGS_FILENAME
                    if (settingsFile.exists()) {
                        settingsFile.inputStream().buffered().use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(BackupRestoreViewModel.SETTINGS_FILENAME))
                            inputStream.copyTo(outputStream)
                        }
                    }

                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }

                    val dbPath = context.getDatabasePath(InternalDatabase.DB_NAME)
                    if (dbPath.exists()) {
                        FileInputStream(dbPath).use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }

            val configuredLocation = context.dataStore[AutoBackupLocationKey].orEmpty()
            if (configuredLocation.isNotBlank()) {
                val location = getBackupLocation(context, configuredLocation)
                    ?: throw IOException("Invalid automatic backup folder")
                if (!location.isAvailable) {
                    throw IOException("Automatic backup folder is unavailable")
                }
                copyToTree(context, location.treeUri, tempFile, fileName)
            } else {
                saveToDefaultLocation(context, tempFile, fileName)
            }

            tempFile.delete()
            cleanUpOldBackups(context, backupType)
            Timber.tag("AutoBackup").d("Automatic backup completed successfully.")
            return true
        } catch (e: Exception) {
            reportException(e)
            Timber.tag("AutoBackup").e(e, "Automatic backup failed")
            return false
        } finally {
            database.close()
        }
    }

    private fun saveToDefaultLocation(context: Context, tempFile: File, fileName: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, BACKUP_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/vivimusic")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore entry in Downloads")
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw IOException("Failed to open MediaStore backup output stream")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val publicDir = File(downloadsDir, "vivimusic")
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }
            tempFile.copyTo(File(publicDir, fileName), overwrite = true)
        }
    }

    private fun copyToTree(context: Context, treeUri: Uri, tempFile: File, fileName: String) {
        val resolver = context.contentResolver
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val documentUri = DocumentsContract.createDocument(resolver, parentUri, BACKUP_MIME_TYPE, fileName)
            ?: throw IOException("Failed to create automatic backup in selected folder")
        try {
            resolver.openOutputStream(documentUri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Failed to open automatic backup output stream")
        } catch (e: Exception) {
            resolver.delete(documentUri, null, null)
            throw e
        }
    }

    private fun cleanUpOldBackups(context: Context, backupType: String) {
        val backups = getAutoBackups(context).filter { backup ->
            backup.name.startsWith("auto_backup_${backupType}_")
        }

        if (backups.size > 5) {
            for (i in 5 until backups.size) {
                val backup = backups[i]
                Timber.tag("AutoBackup").d("Deleting old backup: %s", backup.name)
                deleteBackup(context, backup)
            }
        }
    }

    fun getAutoBackups(context: Context): List<AutoBackupEntry> {
        val configuredLocation = context.dataStore[AutoBackupLocationKey].orEmpty()
        if (configuredLocation.isNotBlank()) {
            val location = getBackupLocation(context, configuredLocation) ?: return emptyList()
            return if (location.isAvailable) getTreeBackups(context, location.treeUri) else emptyList()
        }
        return getDefaultAutoBackups(context)
    }

    private fun getTreeBackups(context: Context, treeUri: Uri): List<AutoBackupEntry> {
        val backups = mutableListOf<AutoBackupEntry>()
        try {
            val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            context.contentResolver.query(childDocumentsUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    if (
                        name.startsWith("auto_backup_") &&
                        name.endsWith(".backup") &&
                        cursor.getString(mimeColumn) != DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        backups += AutoBackupEntry(
                            name = name,
                            lastModified = cursor.getLong(modifiedColumn),
                            size = cursor.getLong(sizeColumn),
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn)),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("AutoBackup").e(e, "Error reading backups from selected folder")
        }
        return backups.sortedByDescending { it.lastModified }
    }

    private fun getDefaultAutoBackups(context: Context): List<AutoBackupEntry> {
        val backupsList = mutableListOf<AutoBackupEntry>()
        try {
            val appDir = File(context.getExternalFilesDir(null), "backups")
            appDir.listFiles { file ->
                file.isFile && file.name.startsWith("auto_backup_") && file.name.endsWith(".backup")
            }?.forEach { file -> backupsList += file.toAutoBackupEntry() }
        } catch (e: Exception) {
            Timber.tag("AutoBackup").e(e, "Error reading backups from app dir")
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATA)
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? OR ${MediaStore.Downloads.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf("Download/vivimusic/%", "Download/vivimusic")
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        val path = cursor.getString(dataColumn)
                        if (name.startsWith("auto_backup_") && name.endsWith(".backup") && path != null) {
                            val file = File(path)
                            if (backupsList.none { it.name == file.name }) {
                                backupsList += file.toAutoBackupEntry()
                            }
                        }
                    }
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val publicDir = File(downloadsDir, "vivimusic")
                if (publicDir.exists()) {
                    publicDir.listFiles { file ->
                        file.isFile && file.name.startsWith("auto_backup_") && file.name.endsWith(".backup")
                    }?.forEach { file ->
                        if (backupsList.none { it.name == file.name }) {
                            backupsList += file.toAutoBackupEntry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("AutoBackup").e(e, "Error reading backups from public dir")
        }

        return backupsList.sortedByDescending { it.lastModified }
    }

    fun deleteBackup(context: Context, backup: AutoBackupEntry): Boolean {
        backup.uri?.let { uri ->
            return try {
                context.contentResolver.delete(uri, null, null) > 0
            } catch (e: Exception) {
                reportException(e)
                Timber.tag("AutoBackup").e(e, "Failed to delete backup document")
                false
            }
        }
        return backup.file?.let { deleteBackup(context, it) } ?: false
    }

    fun deleteBackup(context: Context, file: File): Boolean {
        return try {
            var deleted = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val selection = "${MediaStore.Downloads.DATA} = ?"
                val deletedRows = context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection,
                    arrayOf(file.absolutePath),
                )
                deleted = deletedRows > 0
            }
            if (file.exists()) {
                deleted = deleted || file.delete()
            }
            deleted
        } catch (e: Exception) {
            reportException(e)
            Timber.tag("AutoBackup").e(e, "Failed to delete backup file")
            false
        }
    }

    fun updateWeeklyBackupWork(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            Timber.tag("AutoBackup").d("Enqueuing periodic weekly backup worker")
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            val autoBackupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .addTag(WEEKLY_WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WEEKLY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                autoBackupRequest
            )
        } else {
            Timber.tag("AutoBackup").d("Cancelling periodic weekly backup worker")
            workManager.cancelUniqueWork(WEEKLY_WORK_NAME)
        }
    }

    private fun getTreeMetadata(context: Context, treeUri: Uri): TreeMetadata {
        return try {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    TreeMetadata(cursor.getString(0), true)
                } else {
                    TreeMetadata(null, false)
                }
            } ?: TreeMetadata(null, false)
        } catch (e: Exception) {
            TreeMetadata(null, false)
        }
    }

    private fun File.toAutoBackupEntry() = AutoBackupEntry(
        name = name,
        lastModified = lastModified(),
        size = length(),
        file = this,
    )

    private data class TreeMetadata(val name: String?, val isAccessible: Boolean)
}
