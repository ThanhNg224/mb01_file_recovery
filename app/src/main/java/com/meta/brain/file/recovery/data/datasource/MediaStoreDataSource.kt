package com.meta.brain.file.recovery.data.datasource

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import com.meta.brain.file.recovery.data.model.ScanCursor
import com.meta.brain.file.recovery.data.util.MediaEntryFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for MediaStore queries
 * Handles all direct interactions with Android MediaStore APIs
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaEntryFactory: MediaEntryFactory
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Load thumbnail for media item (API 29+)
     */
    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(width, height), null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Query images from MediaStore
     */
    fun queryImages(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        pageSize: Int,
        cursor: ScanCursor?
    ): List<MediaEntry> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val (selection, selectionArgs) = buildSelection(minSize, fromSec, toSec, cursor)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { resultCursor ->
                val idColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateAddedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val dataColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val widthColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                var count = 0
                while (resultCursor.moveToNext() && count < pageSize) {
                    val id = resultCursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val filePath = resultCursor.getString(dataColumn)

                    // Skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

                    val dateAdded = resultCursor.getLong(dateAddedColumn)
                    val dateModified = resultCursor.getLong(dateModifiedColumn)

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = resultCursor.getString(nameColumn),
                            mimeType = resultCursor.getString(mimeColumn),
                            size = resultCursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = null,
                            isVideo = false,
                            filePath = filePath,
                            width = resultCursor.getInt(widthColumn),
                            height = resultCursor.getInt(heightColumn)
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreDataSource", "Error querying images: ${e.message}")
        }
        return items
    }

    /**
     * Query videos from MediaStore
     */
    fun queryVideos(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        pageSize: Int,
        cursor: ScanCursor?
    ): List<MediaEntry> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val (selection, selectionArgs) = buildSelection(minSize, fromSec, toSec, cursor)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { resultCursor ->
                val idColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateAddedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val widthColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                var count = 0
                while (resultCursor.moveToNext() && count < pageSize) {
                    val id = resultCursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val filePath = resultCursor.getString(dataColumn)

                    // Skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

                    val dateAdded = resultCursor.getLong(dateAddedColumn)
                    val dateModified = resultCursor.getLong(dateModifiedColumn)

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = resultCursor.getString(nameColumn),
                            mimeType = resultCursor.getString(mimeColumn),
                            size = resultCursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = resultCursor.getLong(durationColumn),
                            isVideo = true,
                            filePath = filePath,
                            width = resultCursor.getInt(widthColumn),
                            height = resultCursor.getInt(heightColumn)
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreDataSource", "Error querying videos: ${e.message}")
        }
        return items
    }

    /**
     * Query audio files from MediaStore
     */
    fun queryAudio(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        pageSize: Int,
        cursor: ScanCursor?
    ): List<MediaEntry> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val (selection, selectionArgs) = buildSelection(minSize, fromSec, toSec, cursor)
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { resultCursor ->
                val idColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durationColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                var count = 0
                while (resultCursor.moveToNext() && count < pageSize) {
                    val id = resultCursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val filePath = resultCursor.getString(dataColumn)

                    // Skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

                    val dateAdded = resultCursor.getLong(dateAddedColumn)
                    val dateModified = resultCursor.getLong(dateModifiedColumn)

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = resultCursor.getString(nameColumn),
                            mimeType = resultCursor.getString(mimeColumn),
                            size = resultCursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = resultCursor.getLong(durationColumn),
                            isVideo = false,
                            filePath = filePath
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreDataSource", "Error querying audio: ${e.message}")
        }
        return items
    }

    /**
     * Query documents from MediaStore
     */
    fun queryDocuments(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        pageSize: Int,
        cursor: ScanCursor?
    ): List<MediaEntry> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        val documentMimeTypes = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/vnd.android.package-archive"
        )

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        conditions.add("${MediaStore.Files.FileColumns.MIME_TYPE} IN (${documentMimeTypes.joinToString { "?" }})")
        args.addAll(documentMimeTypes)

        minSize?.let {
            conditions.add("${MediaStore.Files.FileColumns.SIZE} >= ?")
            args.add(it.toString())
        }

        if (fromSec != null && toSec != null) {
            conditions.add("${MediaStore.Files.FileColumns.DATE_ADDED} BETWEEN ? AND ?")
            args.add(fromSec.toString())
            args.add(toSec.toString())
        }

        cursor?.let {
            conditions.add("(${MediaStore.Files.FileColumns.DATE_ADDED} < ? OR (${MediaStore.Files.FileColumns.DATE_ADDED} = ? AND ${MediaStore.Files.FileColumns._ID} < ?))")
            args.add(it.lastDate.toString())
            args.add(it.lastDate.toString())
            args.add(it.lastId.toString())
        }

        val selection = conditions.joinToString(" AND ")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args.toTypedArray(),
                sortOrder
            )?.use { resultCursor ->
                val idColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataColumn = resultCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                var count = 0
                while (resultCursor.moveToNext() && count < pageSize) {
                    val id = resultCursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
                    val mimeType = resultCursor.getString(mimeColumn)
                    val filePath = resultCursor.getString(dataColumn)

                    // Skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

                    val dateAdded = resultCursor.getLong(dateAddedColumn)
                    val dateModified = resultCursor.getLong(dateModifiedColumn)

                    val mediaKind = when {
                        mimeType?.startsWith("application/") == true -> MediaKind.DOCUMENT
                        else -> MediaKind.OTHER
                    }

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = resultCursor.getString(nameColumn),
                            mimeType = mimeType,
                            size = resultCursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = null,
                            isVideo = false,
                            mediaKind = mediaKind,
                            filePath = filePath
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreDataSource", "Error querying documents: ${e.message}")
        }
        return items
    }

    /**
     * Check if a file exists in MediaStore
     */
    fun isFileInMediaStore(file: java.io.File): Boolean {
        return try {
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)

            contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (_: Exception) {
            true // Assume indexed if we can't check
        }
    }

    /**
     * Get file path from content URI
     */
    fun getFilePath(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> uri.path ?: uri.toString()
            "content" -> {
                try {
                    val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                            if (columnIndex >= 0) {
                                return cursor.getString(columnIndex) ?: uri.toString()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fall back to URI string
                }
                uri.toString()
            }
            else -> uri.toString()
        }
    }

    /**
     * Build selection clause and arguments for MediaStore queries
     */
    private fun buildSelection(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        cursor: ScanCursor?
    ): Pair<String?, Array<String>?> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        minSize?.let {
            conditions.add("${MediaStore.MediaColumns.SIZE} >= ?")
            args.add(it.toString())
        }

        if (fromSec != null && toSec != null) {
            conditions.add("${MediaStore.MediaColumns.DATE_ADDED} BETWEEN ? AND ?")
            args.add(fromSec.toString())
            args.add(toSec.toString())
        }

        cursor?.let {
            conditions.add("(${MediaStore.MediaColumns.DATE_ADDED} < ? OR (${MediaStore.MediaColumns.DATE_ADDED} = ? AND ${MediaStore.MediaColumns._ID} < ?))")
            args.add(it.lastDate.toString())
            args.add(it.lastDate.toString())
            args.add(it.lastId.toString())
        }

        return if (conditions.isEmpty()) {
            null to null
        } else {
            conditions.joinToString(" AND ") to args.toTypedArray()
        }
    }
}

