package com.meta.brain.file.recovery.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing the progress of a restore operation
 */
data class RestoreProgress(
    val current: Int,
    val total: Int,
    val currentFileName: String,
    val successCount: Int = 0,
    val failCount: Int = 0
)

/**
 * Repository for restoring media files to public storage
 */
@Singleton
class RestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val BUFFER_SIZE = 256 * 1024 // 256KB buffer

    /**
     * Restore multiple media entries to the specified folder in Downloads
     * Emits progress updates as a Flow
     */
    fun restoreMediaFiles(
        items: List<MediaEntry>,
        folderName: String = "RELive/Restored"
    ): Flow<RestoreProgress> = flow {
        val total = items.size
        var successCount = 0
        var failCount = 0

        items.forEachIndexed { index, mediaEntry ->
            val fileName = mediaEntry.displayName ?: "unknown_${System.currentTimeMillis()}"

            // Emit progress for current item
            emit(RestoreProgress(
                current = index + 1,
                total = total,
                currentFileName = fileName,
                successCount = successCount,
                failCount = failCount
            ))

            try {
                restoreSingleFile(mediaEntry, folderName)
                successCount++
            } catch (e: Exception) {
                android.util.Log.e("RestoreRepository", "Failed to restore $fileName: ${e.message}", e)
                failCount++
            }
        }

        // Emit final progress
        emit(RestoreProgress(
            current = total,
            total = total,
            currentFileName = "",
            successCount = successCount,
            failCount = failCount
        ))
    }

    /**
     * Restore a single file to public storage using MediaStore
     */
    private suspend fun restoreSingleFile(
        mediaEntry: MediaEntry,
        folderName: String
    ) = withContext(Dispatchers.IO) {
        val mimeType = mediaEntry.mimeType ?: "application/octet-stream"
        val displayName = mediaEntry.displayName ?: "unknown_${System.currentTimeMillis()}"

        // Determine the appropriate MediaStore collection
        val collection = getCollectionUri(mediaEntry.mediaKind)

        // Prepare ContentValues
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folderName"
        val uniqueName = getUniqueFileName(collection, relativePath, displayName)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        // Insert the new entry
        val destUri = contentResolver.insert(collection, contentValues)
            ?: throw IOException("Failed to create MediaStore entry for $displayName")

        try {
            // Copy the file content
            contentResolver.openOutputStream(destUri)?.use { outputStream ->
                contentResolver.openInputStream(mediaEntry.uri)?.use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            } ?: throw IOException("Failed to open output stream for $displayName")

            // Mark as completed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(destUri, contentValues, null, null)
            }

            android.util.Log.d("RestoreRepository", "Successfully restored: $uniqueName")
        } catch (e: Exception) {
            // Delete the partial file on failure
            contentResolver.delete(destUri, null, null)
            throw e
        }
    }

    /**
     * Get the appropriate MediaStore collection URI based on media kind
     */
    private fun getCollectionUri(mediaKind: MediaKind): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (mediaKind) {
                MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaKind.DOCUMENT, MediaKind.OTHER -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        } else {
            when (mediaKind) {
                MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                MediaKind.DOCUMENT, MediaKind.OTHER -> MediaStore.Files.getContentUri("external")
            }
        }
    }

    /**
     * Generate a unique filename by checking for conflicts and appending (1), (2), etc.
     */
    private fun getUniqueFileName(
        collection: Uri,
        relativePath: String,
        originalName: String
    ): String {
        val nameWithoutExt = originalName.substringBeforeLast(".", originalName)
        val extension = if (originalName.contains(".")) {
            ".${originalName.substringAfterLast(".")}"
        } else {
            ""
        }

        var counter = 0
        var candidateName = originalName

        // Check if file exists
        while (fileExists(collection, relativePath, candidateName)) {
            counter++
            candidateName = "$nameWithoutExt ($counter)$extension"

            // Safety limit to prevent infinite loop
            if (counter > 1000) {
                candidateName = "${nameWithoutExt}_${System.currentTimeMillis()}$extension"
                break
            }
        }

        return candidateName
    }

    /**
     * Check if a file with the given name already exists in the collection
     */
    private fun fileExists(collection: Uri, relativePath: String, displayName: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        } else {
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        }

        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(relativePath, displayName)
        } else {
            arrayOf(displayName)
        }

        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }

    /**
     * Calculate total size of selected items
     */
    fun calculateTotalSize(items: List<MediaEntry>): Long {
        return items.sumOf { it.size }
    }

    /**
     * Format size in human-readable format
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

