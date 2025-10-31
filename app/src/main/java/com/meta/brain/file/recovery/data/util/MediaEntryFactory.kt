package com.meta.brain.file.recovery.data.util

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import com.meta.brain.file.recovery.data.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating MediaEntry instances from various sources
 * Single responsibility: Convert raw data (files, cursors) into MediaEntry objects
 */
@Singleton
class MediaEntryFactory @Inject constructor(
    private val mimeTypeUtils: MimeTypeUtils
) {

    /**
     * Create MediaEntry from a File
     */
    fun createFromFile(
        file: java.io.File,
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        isTrashed: Boolean = false
    ): MediaEntry? {
        try {
            // Apply size filter
            if (minSize != null && file.length() < minSize) return null

            // Apply time filter
            val fileTime = file.lastModified() / 1000
            if (fromSec != null && toSec != null && (fileTime < fromSec || fileTime > toSec)) return null

            // Determine MIME type and media kind
            val mimeType = mimeTypeUtils.getMimeTypeFromExtension(file.extension)
            val mediaKind = mimeTypeUtils.determineMediaKind(mimeType)

            // Apply type filter
            if (!matchesTypeFilter(types, mediaKind)) return null

            // Create file URI
            val uri = Uri.fromFile(file)

            // Auto-detect if file is in trash directory
            val isInTrash = isTrashed || isFileInTrashDirectory(file)

            return MediaEntry(
                uri = uri,
                displayName = file.name,
                mimeType = mimeType,
                size = file.length(),
                dateAdded = fileTime,
                dateTaken = fileTime,
                durationMs = null,
                isVideo = mediaKind == MediaKind.VIDEO,
                isTrashed = isInTrash,
                mediaKind = mediaKind,
                filePath = file.absolutePath
            )
        } catch (e: Exception) {
            android.util.Log.e("MediaEntryFactory", "Error creating MediaEntry from file ${file.path}: ${e.message}")
            return null
        }
    }

    /**
     * Create MediaEntry from a MediaStore cursor
     */
    fun createFromCursor(
        cursor: Cursor,
        uri: Uri,
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): MediaEntry? {
        return try {
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))

            // Apply filters
            if (minSize != null && size < minSize) return null
            if (fromSec != null && toSec != null && (dateAdded < fromSec || dateAdded > toSec)) return null

            val mediaKind = mimeTypeUtils.determineMediaKind(mimeType)

            // Apply type filter
            if (!matchesTypeFilter(types, mediaKind)) return null

            MediaEntry(
                uri = uri,
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)),
                mimeType = mimeType,
                size = size,
                dateAdded = dateAdded,
                dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                durationMs = null,
                isVideo = mediaKind == MediaKind.VIDEO,
                mediaKind = mediaKind
            )
        } catch (e: Exception) {
            android.util.Log.e("MediaEntryFactory", "Error creating MediaEntry from cursor: ${e.message}")
            null
        }
    }

    /**
     * Check if file is in a trash/recycle bin directory
     */
    private fun isFileInTrashDirectory(file: java.io.File): Boolean {
        val path = file.absolutePath.lowercase()
        return path.contains("trash") ||
                path.contains("recycle") ||
                path.contains(".trash-") ||
                path.contains("/.trash/") ||
                path.contains("/trash/") ||
                path.contains("/.trashed/") ||
                path.contains("/trashed/") ||
                path.contains("/.recyclebin/") ||
                path.contains("/recyclebin/")
    }

    /**
     * Check if MediaKind matches the requested types filter
     */
    private fun matchesTypeFilter(types: Set<MediaType>, mediaKind: MediaKind): Boolean {
        if (types.contains(MediaType.ALL)) return true

        return when (mediaKind) {
            MediaKind.IMAGE -> types.contains(MediaType.IMAGES)
            MediaKind.VIDEO -> types.contains(MediaType.VIDEOS)
            MediaKind.AUDIO -> types.contains(MediaType.AUDIO)
            MediaKind.DOCUMENT -> types.contains(MediaType.DOCUMENTS)
            MediaKind.OTHER -> false
        }
    }
}

