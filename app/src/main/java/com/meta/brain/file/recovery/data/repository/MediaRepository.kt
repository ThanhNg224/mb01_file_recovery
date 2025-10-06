package com.meta.brain.file.recovery.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.model.ScanCursor
import com.meta.brain.file.recovery.data.model.ScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Performs quick scan of media files with pagination and filtering
     */
    suspend fun quickScan(
        types: Set<MediaType>,
        minSize: Long? = null,
        fromSec: Long? = null,
        toSec: Long? = null,
        pageSize: Int = 300,
        cursor: ScanCursor? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        android.util.Log.d("MediaRepository", "Starting quickScan with types: $types, minSize: $minSize")

        val allItems = mutableListOf<MediaEntry>()

        // Scan images if requested
        if (types.contains(MediaType.IMAGES) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Scanning images...")
            val images = queryImages(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${images.size} images")
            allItems.addAll(images)
        }

        // Scan videos if requested
        if (types.contains(MediaType.VIDEOS) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Scanning videos...")
            val videos = queryVideos(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${videos.size} videos")
            allItems.addAll(videos)
        }

        // Scan documents if requested
        if (types.contains(MediaType.DOCUMENTS) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Scanning documents...")
            val documents = queryDocuments(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${documents.size} documents")
            allItems.addAll(documents)
        }

        // Sort by date taken (newest first), then by date added
        val sortedItems = allItems.sortedWith(
            compareByDescending<MediaEntry> { it.dateTaken ?: it.dateAdded }
                .thenByDescending { it.dateAdded }
        ).take(pageSize)

        // Generate next cursor if we have full page
        val nextCursor = if (sortedItems.size >= pageSize) {
            val lastItem = sortedItems.last()
            ScanCursor(
                lastDate = lastItem.dateTaken ?: lastItem.dateAdded,
                lastId = lastItem.uri.lastPathSegment?.toLongOrNull() ?: 0L
            )
        } else null

        android.util.Log.d("MediaRepository", "Scan complete. Total items: ${sortedItems.size}")
        ScanResult(sortedItems, nextCursor)
    }

    /**
     * Load thumbnail for media item (API 29+)
     */
    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(width, height), null)
            } else {
                // Fallback for older APIs
                MediaStore.Images.Thumbnails.getThumbnail(
                    contentResolver,
                    uri.lastPathSegment?.toLongOrNull() ?: 0L,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryImages(
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
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val selection = buildSelection(minSize, fromSec, toSec, cursor, false)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection.first,
                selection.second,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = cursor.getString(nameColumn),
                            mimeType = cursor.getString(mimeColumn),
                            size = cursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = null,
                            isVideo = false
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error querying images: ${e.message}")
        }

        return items
    }

    private fun queryVideos(
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
            MediaStore.Video.Media.DURATION
        )

        val selection = buildSelection(minSize, fromSec, toSec, cursor, true)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection.first,
                selection.second,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = cursor.getString(nameColumn),
                            mimeType = cursor.getString(mimeColumn),
                            size = cursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = cursor.getLong(durationColumn),
                            isVideo = true
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error querying videos: ${e.message}")
        }

        return items
    }

    private fun queryDocuments(
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
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        // Document MIME types to scan for
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

        // MIME type filter for documents
        conditions.add("${MediaStore.Files.FileColumns.MIME_TYPE} IN (${documentMimeTypes.joinToString { "?" }})")
        args.addAll(documentMimeTypes)

        // Size filter
        minSize?.let {
            conditions.add("${MediaStore.Files.FileColumns.SIZE} >= ?")
            args.add(it.toString())
        }

        // Time range filter
        if (fromSec != null && toSec != null) {
            conditions.add("${MediaStore.Files.FileColumns.DATE_ADDED} BETWEEN ? AND ?")
            args.add(fromSec.toString())
            args.add(toSec.toString())
        }

        // Pagination cursor
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
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
                    val mimeType = cursor.getString(mimeColumn)

                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)

                    // Determine MediaKind based on MIME type
                    val mediaKind = when {
                        mimeType?.startsWith("application/") == true ->
                            com.meta.brain.file.recovery.data.model.MediaKind.DOCUMENT
                        else -> com.meta.brain.file.recovery.data.model.MediaKind.OTHER
                    }

                    items.add(
                        MediaEntry(
                            uri = uri,
                            displayName = cursor.getString(nameColumn),
                            mimeType = mimeType,
                            size = cursor.getLong(sizeColumn),
                            dateAdded = dateAdded,
                            dateTaken = if (dateModified > 0) dateModified else dateAdded,
                            durationMs = null,
                            isVideo = false,
                            mediaKind = mediaKind
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error querying documents: ${e.message}")
        }

        return items
    }

    private fun buildSelection(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        cursor: ScanCursor?,
        isVideo: Boolean
    ): Pair<String?, Array<String>?> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        // Size filter
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
