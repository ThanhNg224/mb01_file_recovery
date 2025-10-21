package com.meta.brain.file.recovery.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
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
    @param:ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver


    /**
     * Load thumbnail for media item (API 29+)
     */
    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(width, height), null)
            } else {
                // Fallback for older APIs: return null
                null
            }
        } catch (_: Exception) {
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
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA // added
        )

        val (baseSelection, baseArgs) = buildSelection(minSize, fromSec, toSec, cursor)
        val selection = baseSelection


        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                baseArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val filePath = cursor.getString(dataColumn)

                    // skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

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
                            isVideo = false,
                            filePath = filePath
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
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA // added
        )

        val (baseSelection, baseArgs) = buildSelection(minSize, fromSec, toSec, cursor)

        // REMOVED: IS_TRASHED = 0 filter - we want to INCLUDE trashed items
        val selection = baseSelection

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                baseArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val filePath = cursor.getString(dataColumn)

                    // ONLY skip our own restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

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
                            isVideo = true,
                            filePath = filePath
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


    private fun queryAudio(
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
            MediaStore.Audio.Media.DATA // added
        )

        val (baseSelection, baseArgs) = buildSelection(minSize, fromSec, toSec, cursor)
        val selection = baseSelection

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val items = mutableListOf<MediaEntry>()

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                baseArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val filePath = cursor.getString(dataColumn)

                    // skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

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
                            isVideo = false,
                            filePath = filePath
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error querying audio: ${e.message}")
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
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA // added
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
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                var count = 0
                while (cursor.moveToNext() && count < pageSize) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
                    val mimeType = cursor.getString(mimeColumn)
                    val filePath = cursor.getString(dataColumn)

                    // skip restored folder
                    if (filePath != null && filePath.contains("/RELive/Restored", ignoreCase = true)) continue

                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)

                    val mediaKind = when {
                        mimeType?.startsWith("application/") == true -> MediaKind.DOCUMENT
                        else -> MediaKind.OTHER
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
                            mediaKind = mediaKind,
                            filePath = filePath
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


    /**
     * Performs comprehensive deep scan including hidden files, archive files, trash, and unindexed files
     * Requires MANAGE_EXTERNAL_STORAGE permission on Android 11+
     */
    suspend fun deepScan(
        types: Set<MediaType>,
        minSize: Long? = null,
        fromSec: Long? = null,
        toSec: Long? = null,
        pageSize: Int = 300,
        cursor: ScanCursor? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        android.util.Log.d("MediaRepository", "Starting deepScan with types: $types, minSize: $minSize")

        val allItems = mutableListOf<MediaEntry>()

        // 1. Regular MediaStore scan (same as quick scan)
        if (types.contains(MediaType.IMAGES) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning images...")
            val images = queryImages(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${images.size} images")
            allItems.addAll(images)
        }

        if (types.contains(MediaType.VIDEOS) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning videos...")
            val videos = queryVideos(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${videos.size} videos")
            allItems.addAll(videos)
        }

        if (types.contains(MediaType.AUDIO) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning audio...")
            val audio = queryAudio(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${audio.size} audio files")
            allItems.addAll(audio)
        }

        if (types.contains(MediaType.DOCUMENTS) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning documents...")
            val documents = queryDocuments(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${documents.size} documents")
            allItems.addAll(documents)
        }

        // 2. Scan hidden files and directories
        android.util.Log.d("MediaRepository", "Scanning hidden files...")
        val hiddenFiles = scanHiddenFiles(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${hiddenFiles.size} hidden files")
        allItems.addAll(hiddenFiles)

        // 3. Scan trash/recycle bin directories
        android.util.Log.d("MediaRepository", "Scanning trash directories...")
        val trashFiles = scanTrashDirectories(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${trashFiles.size} trash files")
        allItems.addAll(trashFiles)

        // 4. Scan for recently archive files (Android 11+ with MANAGE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.util.Log.d("MediaRepository", "Scanning archive files...")
            val deletedFiles = scanDeletedFiles(types, minSize, fromSec, toSec)
            android.util.Log.d("MediaRepository", "Found ${deletedFiles.size} archive files")
            allItems.addAll(deletedFiles)
        }

        // 5. File system deep scan for unindexed files
        android.util.Log.d("MediaRepository", "Scanning unindexed files...")
        val unindexedFiles = scanUnindexedFiles(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${unindexedFiles.size} unindexed files")
        allItems.addAll(unindexedFiles)

        // Remove duplicates based on actual file path (not just URI)
        // Group by file path to handle both content:// and file:// URIs pointing to same file
        val uniqueItems = allItems
            .groupBy { getFilePath(it.uri) }
            .mapValues { (_, entries) ->
                // If multiple entries for same file, prefer content:// URI over file:// URI
                entries.firstOrNull { it.uri.scheme == "content" } ?: entries.first()
            }
            .values
            .sortedWith(
                compareByDescending<MediaEntry> { it.dateTaken ?: it.dateAdded }
                    .thenByDescending { it.dateAdded }
            )
            .take(pageSize)

        // Generate next cursor if we have full page
        val nextCursor = if (uniqueItems.size >= pageSize) {
            val lastItem = uniqueItems.last()
            ScanCursor(
                lastDate = lastItem.dateTaken ?: lastItem.dateAdded,
                lastId = lastItem.uri.lastPathSegment?.toLongOrNull() ?: 0L
            )
        } else null

        android.util.Log.d("MediaRepository", "Deep scan complete. Total unique items: ${uniqueItems.size}")
        ScanResult(uniqueItems, nextCursor)
    }

    private fun scanHiddenFiles(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val hiddenFiles = mutableListOf<MediaEntry>()

        try {
            // Expanded hidden file directories on Android
            val sdcardPath = Environment.getExternalStorageDirectory().path
            val hiddenDirs = listOf(
                "/storage/emulated/0/.hidden",
                "/storage/emulated/0/.trash",
                "/storage/emulated/0/.recycle",
                "/storage/emulated/0/.thumbnails",
                "/storage/emulated/0/.nomedia",
                "/storage/emulated/0/Android/data/.hidden",
                "/storage/emulated/0/DCIM/.thumbnails",
                "/storage/emulated/0/Pictures/.thumbnails",
                "$sdcardPath/.hidden",
                "$sdcardPath/.cache",
                "$sdcardPath/.temp",
                // App-specific hidden directories
                "/storage/emulated/0/.WhatsApp",
                "/storage/emulated/0/.Telegram",
                "/storage/emulated/0/.Instagram",
                "/storage/emulated/0/.Facebook",
                "/storage/emulated/0/.Snapchat",
                "/storage/emulated/0/.TikTok",
                // System hidden directories
                "/storage/emulated/0/Android/data/.trash",
                "/storage/emulated/0/Android/media/.hidden"
            )

            hiddenDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("MediaRepository", "Scanning hidden directory: $dirPath")
                    scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, hiddenFiles)
                }
            }

            // Scan all directories for files starting with "." (hidden files)
            scanForDotFiles(types, minSize, fromSec, toSec, hiddenFiles)

            // Scan for .nomedia directories (often contain hidden media)
            scanNoMediaDirectories(types, minSize, fromSec, toSec, hiddenFiles)

        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning hidden files: ${e.message}")
        }

        return hiddenFiles
    }

    private fun scanTrashDirectories(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val trashFiles = mutableListOf<MediaEntry>()

        try {
            // Comprehensive trash/recycle bin directories
            val sdcardPath = Environment.getExternalStorageDirectory().path
            val trashDirs = listOf(
                // System trash directories
                "/storage/emulated/0/.Trash",
                "/storage/emulated/0/Trash",
                "/storage/emulated/0/.trash",
                "/storage/emulated/0/RECYCLE.BIN",
                "/storage/emulated/0/.recycle.bin",
                "/storage/emulated/0/.Trash-1000",
                "$sdcardPath/.Trash-1000",
                "$sdcardPath/Trash",
                "$sdcardPath/.trash",
                // MediaStore trash
                "/storage/emulated/0/Android/data/com.android.providers.media/cache/.trash",
                "/storage/emulated/0/Android/data/com.android.providers.media/.trash",
                // File manager app trash directories
                "/storage/emulated/0/.RecycleBin",
                "/storage/emulated/0/Files/Trash",
                "/storage/emulated/0/.archive",
                "/storage/emulated/0/archive",
                // Gallery app trash
                "/storage/emulated/0/DCIM/.trashed",
                "/storage/emulated/0/Pictures/.trashed",
                "/storage/emulated/0/Pictures/Trash",
                "/storage/emulated/0/DCIM/Trash",
                // Third-party file manager trash
                "/storage/emulated/0/ES File Explorer/.trash",
                "/storage/emulated/0/File Manager/Trash",
                "/storage/emulated/0/.Asus/FileManager/.TrashCan",
                "/storage/emulated/0/MiDrive/.trash",
                // Cloud storage local trash
                "/storage/emulated/0/Android/data/com.google.android.apps.docs/files/.trash",
                "/storage/emulated/0/Android/data/com.dropbox.android/files/.trash",
                // Social media app trash/cache
                "/storage/emulated/0/WhatsApp/.Trash",
                "/storage/emulated/0/Telegram/.Trash",
                "/storage/emulated/0/Instagram/.Trash",
                // Camera app trash
                "/storage/emulated/0/DCIM/.thumbnails_trash",
                "/storage/emulated/0/DCIM/Camera/.trash"
            )

            trashDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("MediaRepository", "Scanning trash directory: $dirPath")
                    scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, trashFiles)
                } else {
                    android.util.Log.d("MediaRepository", "Trash directory not accessible: $dirPath")
                }
            }

            // Scan for any directory with "trash" or "archive" in name
            scanForTrashNamedDirectories(types, minSize, fromSec, toSec, trashFiles)

        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning trash directories: ${e.message}")
        }

        return trashFiles
    }

    private fun scanDeletedFiles(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val deletedFiles = mutableListOf<MediaEntry>()

        try {
            // On Android 11+, try to access recently archive files through MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Scan MediaStore for files marked as archive/trashed
                scanMediaStoreDeleted(types, minSize, fromSec, toSec, deletedFiles)
            }

            // Check temporary directories where archive files might still exist
            val sdcardPath = Environment.getExternalStorageDirectory().path
            val tempDirs = listOf(
                "/storage/emulated/0/Android/data/com.android.providers.media/files/.pending",
                "/storage/emulated/0/Android/data/com.android.providers.media/files/.archive",
                "/storage/emulated/0/.temp",
                "/storage/emulated/0/tmp",
                "/storage/emulated/0/.tmp",
                "/storage/emulated/0/temp",
                // App cache directories that might contain archive files
                "/storage/emulated/0/Android/data/com.android.gallery3d/cache",
                "/storage/emulated/0/Android/data/com.google.android.apps.photos/cache",
                // System temporary directories
                "/data/local/tmp",
                "/cache",
                // Backup directories
                "/storage/emulated/0/.backup",
                "/storage/emulated/0/backup",
                "/storage/emulated/0/Backups",
                "/storage/emulated/0/Android/data/backup",
                "$sdcardPath/.temp",
                "$sdcardPath/tmp",
                "$sdcardPath/.tmp",
                "$sdcardPath/temp",
                "$sdcardPath/.backup",
                "$sdcardPath/backup",
                "$sdcardPath/Backups"
            )

            tempDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("MediaRepository", "Scanning temp/archive directory: $dirPath")
                    scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, deletedFiles)
                }
            }

            // Scan for orphaned files (files with no parent directory reference)
            scanOrphanedFiles(types, minSize, fromSec, toSec, deletedFiles)

        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning archive files: ${e.message}")
        }

        return deletedFiles
    }

    private fun scanUnindexedFiles(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val unindexedFiles = mutableListOf<MediaEntry>()

        try {
            // Expanded directories that might contain unindexed files
            val sdcardPath = Environment.getExternalStorageDirectory().path
            val unindexedDirs = listOf(
                // Standard directories
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Movies",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/DCIM/Camera",
                "/storage/emulated/0/DCIM/Screenshots",
                // Messaging app media
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Images",
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Video",
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Documents",
                "/storage/emulated/0/Telegram/Telegram Images",
                "/storage/emulated/0/Telegram/Telegram Video",
                "/storage/emulated/0/Telegram/Telegram Documents",
                // Social media
                "/storage/emulated/0/Instagram",
                "/storage/emulated/0/Snapchat",
                "/storage/emulated/0/TikTok",
                "/storage/emulated/0/Facebook",
                "/storage/emulated/0/Twitter",
                // Bluetooth transfers
                "/storage/emulated/0/bluetooth",
                "/storage/emulated/0/Bluetooth",
                // Recording apps
                "/storage/emulated/0/Recordings",
                "/storage/emulated/0/Voice Recorder",
                "/storage/emulated/0/Call Recordings",
                // Camera apps
                "/storage/emulated/0/DCIM/.thumbnails",
                "/storage/emulated/0/Pictures/Screenshots",
                // App data directories
                "/storage/emulated/0/Android/data",
                "/storage/emulated/0/Android/media",
                "/storage/emulated/0/Android/obb",
                // Custom folders
                "/storage/emulated/0/Media",
                "/storage/emulated/0/Files",
                "/storage/emulated/0/MyFiles",
                // SD card paths
                "/storage/sdcard1",
                "/mnt/extSdCard",
                "$sdcardPath/Download",
                "$sdcardPath/Downloads",
                "$sdcardPath/Documents",
                "$sdcardPath/Music",
                "$sdcardPath/Movies",
                "$sdcardPath/Pictures",
                "$sdcardPath/DCIM",
                "$sdcardPath/DCIM/Camera",
                "$sdcardPath/DCIM/Screenshots",
                "$sdcardPath/bluetooth",
                "$sdcardPath/Bluetooth",
                "$sdcardPath/Recordings",
                "$sdcardPath/Voice Recorder",
                "$sdcardPath/Call Recordings",
                "$sdcardPath/Media",
                "$sdcardPath/Files",
                "$sdcardPath/MyFiles"
            )

            unindexedDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("MediaRepository", "Scanning for unindexed files in: $dirPath")
                    scanDirectoryForUnindexed(dir, types, minSize, fromSec, toSec, unindexedFiles)
                }
            }

            // Scan root storage for any unindexed files
            scanRootStorageForUnindexed(types, minSize, fromSec, toSec, unindexedFiles)

        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning unindexed files: ${e.message}")
        }

        return unindexedFiles
    }

    private fun buildSelection(
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        cursor: ScanCursor?
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

    // Helper methods for deep scanning
    private fun scanDirectoryRecursively(
        directory: java.io.File,
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // Recursively scan subdirectories
                    scanDirectoryRecursively(file, types, minSize, fromSec, toSec, resultList)
                } else if (file.isFile) {
                    // Check if file matches our criteria
                    val mediaEntry = createMediaEntryFromFile(file, types, minSize, fromSec, toSec)
                    mediaEntry?.let { resultList.add(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning directory ${directory.path}: ${e.message}")
        }
    }

    private fun scanDirectoryForUnindexed(
        directory: java.io.File,
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    // Check if file is not in MediaStore (unindexed)
                    if (!isFileInMediaStore(file)) {
                        val mediaEntry = createMediaEntryFromFile(file, types, minSize, fromSec, toSec)
                        mediaEntry?.let { resultList.add(it) }
                    }
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    // Recursively scan non-hidden subdirectories
                    scanDirectoryForUnindexed(file, types, minSize, fromSec, toSec, resultList)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning unindexed directory ${directory.path}: ${e.message}")
        }
    }

    private fun scanForDotFiles(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            // Scan common directories for hidden files (starting with ".")
            val commonDirs = listOf(
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Documents"
            )

            commonDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    dir.listFiles()?.filter { it.name.startsWith(".") }?.forEach { file ->
                        if (file.isFile) {
                            val mediaEntry = createMediaEntryFromFile(file, types, minSize, fromSec, toSec)
                            mediaEntry?.let { resultList.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning for dot files: ${e.message}")
        }
    }

    private fun scanNoMediaDirectories(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            // Scan for .nomedia files which indicate the folder should be hidden
            val noMediaFiles = mutableListOf<MediaEntry>()

            // Common directories to scan for .nomedia files
            val commonDirs = listOf(
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/WhatsApp/Media",
                "/storage/emulated/0/Telegram/Telegram Images",
                "/storage/emulated/0/Instagram",
                "/storage/emulated/0/Snapchat",
                "/storage/emulated/0/TikTok",
                "/storage/emulated/0/Facebook",
                "/storage/emulated/0/Twitter"
            )

            commonDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    // Check for .nomedia file in the directory
                    val nomediaFile = java.io.File(dir, ".nomedia")
                    if (nomediaFile.exists() && nomediaFile.isFile) {
                        // If .nomedia file exists, add all media in this directory to the result
                        dir.listFiles()?.forEach { file ->
                            val mediaEntry = createMediaEntryFromFile(file, types, minSize, fromSec, toSec)
                            mediaEntry?.let { noMediaFiles.add(it) }
                        }
                    }
                }
            }

            // Add noMediaFiles to resultList
            resultList.addAll(noMediaFiles)

        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning .nomedia directories: ${e.message}")
        }
    }

    private fun scanMediaStoreDeleted(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Query MediaStore for recently archive files
                // Note: This requires special permissions and may not be available on all devices
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )

                // Try to query files that might be marked as archive
                contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} > ?",
                    arrayOf((System.currentTimeMillis() / 1000 - 30 * 24 * 60 * 60).toString()), // Last 30 days
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(0)
                            val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())

                            // Check if file actually exists (if not, it might be archive)
                            val fileExists = try {
                                contentResolver.openInputStream(uri)?.close()
                                true
                            } catch (_: Exception) {
                                false
                            }

                            if (!fileExists) {
                                // File doesn't exist but is still in MediaStore - might be recently archive
                                val mediaEntry = createMediaEntryFromCursor(cursor, uri, types, minSize, fromSec, toSec)
                                mediaEntry?.let { resultList.add(it) }
                            }
                        } catch (_: Exception) {
                            // Continue with next file
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning MediaStore for archive files: ${e.message}")
        }
    }

    private fun createMediaEntryFromFile(
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

            // Determine MIME type from file extension
            val mimeType = getMimeTypeFromExtension(file.extension)
            val mediaKind = determineMediaKind(mimeType)

            // Apply type filter
            when {
                types.contains(MediaType.IMAGES) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.IMAGE -> return null
                types.contains(MediaType.VIDEOS) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.VIDEO -> return null
                types.contains(MediaType.AUDIO) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.AUDIO -> return null
                types.contains(MediaType.DOCUMENTS) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.DOCUMENT -> return null
                !types.contains(MediaType.ALL) && !types.contains(MediaType.IMAGES) && !types.contains(MediaType.VIDEOS) && !types.contains(MediaType.AUDIO) && !types.contains(MediaType.DOCUMENTS) -> return null
            }

            // Create file URI
            val uri = Uri.fromFile(file)

            // Auto-detect if file is in trash directory (in case isTrashed not explicitly set)
            val isInTrash = isTrashed || isFileInTrashDirectory(file)

            return MediaEntry(
                uri = uri,
                displayName = file.name,
                mimeType = mimeType,
                size = file.length(),
                dateAdded = fileTime,
                dateTaken = fileTime,
                durationMs = null,
                isVideo = mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.VIDEO,
                isTrashed = isInTrash,
                mediaKind = mediaKind
            )
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error creating MediaEntry from file ${file.path}: ${e.message}")
            return null
        }
    }

    /**
     * Check if a file is in a trash/recycle bin directory
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

    private fun createMediaEntryFromCursor(
        cursor: android.database.Cursor,
        uri: Uri,
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): MediaEntry? {
        try {
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))

            // Apply filters
            if (minSize != null && size < minSize) return null
            if (fromSec != null && toSec != null && (dateAdded < fromSec || dateAdded > toSec)) return null

            val mediaKind = determineMediaKind(mimeType)

            // Apply type filter
            when {
                types.contains(MediaType.IMAGES) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.IMAGE -> return null
                types.contains(MediaType.VIDEOS) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.VIDEO -> return null
                types.contains(MediaType.AUDIO) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.AUDIO -> return null
                types.contains(MediaType.DOCUMENTS) && mediaKind != com.meta.brain.file.recovery.data.model.MediaKind.DOCUMENT -> return null
                !types.contains(MediaType.ALL) && !types.contains(MediaType.IMAGES) && !types.contains(MediaType.VIDEOS) && !types.contains(MediaType.AUDIO) && !types.contains(MediaType.DOCUMENTS) -> return null
            }

            return MediaEntry(
                uri = uri,
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)),
                mimeType = mimeType,
                size = size,
                dateAdded = dateAdded,
                dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)),
                durationMs = null,
                isVideo = mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.VIDEO,
                mediaKind = mediaKind
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun isFileInMediaStore(file: java.io.File): Boolean {
        try {
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
                return cursor.count > 0
            }
        } catch (_: Exception) {
            // If we can't check, assume it's indexed
            return true
        }
        return false
    }

    private fun getMimeTypeFromExtension(extension: String): String? {
        return when (extension.lowercase()) {
            // Image formats
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            // Video formats
            "mp4" -> "video/mp4"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            // Audio formats
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"
            "amr" -> "audio/amr"
            "3ga" -> "audio/3gpp"
            // Document formats
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> null
        }
    }

    private fun determineMediaKind(mimeType: String?): com.meta.brain.file.recovery.data.model.MediaKind {
        return when {
            mimeType == null -> com.meta.brain.file.recovery.data.model.MediaKind.OTHER
            mimeType.startsWith("image/") -> com.meta.brain.file.recovery.data.model.MediaKind.IMAGE
            mimeType.startsWith("video/") -> com.meta.brain.file.recovery.data.model.MediaKind.VIDEO
            mimeType.startsWith("audio/") -> com.meta.brain.file.recovery.data.model.MediaKind.AUDIO
            mimeType.startsWith("application/") -> com.meta.brain.file.recovery.data.model.MediaKind.DOCUMENT
            else -> com.meta.brain.file.recovery.data.model.MediaKind.OTHER
        }
    }

    private fun scanForTrashNamedDirectories(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            // Scan root storage for directories with "trash", "archive", "recycle" in their names
            val sdcardPath = Environment.getExternalStorageDirectory().path
            val rootDirs = listOf(
                "/storage/emulated/0",
                sdcardPath
            )

            rootDirs.forEach { rootPath ->
                val rootDir = java.io.File(rootPath)
                if (rootDir.exists() && rootDir.canRead()) {
                    rootDir.listFiles()?.forEach { dir ->
                        if (dir.isDirectory) {
                            val dirName = dir.name.lowercase()
                            if (dirName.contains("trash") ||
                                dirName.contains("archive") ||
                                dirName.contains("recycle") ||
                                dirName.contains("bin")) {
                                android.util.Log.d("MediaRepository", "Found trash-named directory: ${dir.path}")
                                scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, resultList)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning for trash-named directories: ${e.message}")
        }
    }

    private fun scanOrphanedFiles(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                var count = 0
                val maxCount = 1000

                while (cursor.moveToNext()) {
                    if (count >= maxCount) break
                    try {
                        if (dataColumn >= 0) {
                            val filePath = cursor.getString(dataColumn)
                            val file = java.io.File(filePath)

                            // If file doesn't physically exist, it's orphaned
                            if (!file.exists()) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                                val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())

                                val mediaEntry = createMediaEntryFromCursor(cursor, uri, types, minSize, fromSec, toSec)
                                mediaEntry?.let {
                                    android.util.Log.d("MediaRepository", "Found orphaned file: $filePath")
                                    resultList.add(it)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Continue with next file
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning for orphaned files: ${e.message}")
        }
    }

    private fun scanRootStorageForUnindexed(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            // Scan the root of storage for any unindexed media files
            val sdcardPath = Environment.getExternalStorageDirectory().path
            val rootPaths = listOf(
                "/storage/emulated/0",
                sdcardPath
            )

            rootPaths.forEach { rootPath ->
                val rootDir = java.io.File(rootPath)
                if (rootDir.exists() && rootDir.canRead()) {
                    // Only scan first level files and selected directories
                    rootDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            // Check if file is unindexed
                            if (!isFileInMediaStore(file)) {
                                val mediaEntry = createMediaEntryFromFile(file, types, minSize, fromSec, toSec)
                                mediaEntry?.let {
                                    android.util.Log.d("MediaRepository", "Found unindexed file at root: ${file.name}")
                                    resultList.add(it)
                                }
                            }
                        } else if (file.isDirectory && !file.name.startsWith(".")) {
                            // Scan custom directories that might not be in standard list
                            val dirName = file.name.lowercase()
                            if (!dirName.startsWith("android") &&
                                dirName.contains("media") ||
                                dirName.contains("file") ||
                                dirName.contains("photo") ||
                                dirName.contains("video") ||
                                dirName.contains("music") ||
                                dirName.contains("document")) {
                                android.util.Log.d("MediaRepository", "Scanning custom media directory: ${file.path}")
                                scanDirectoryForUnindexed(file, types, minSize, fromSec, toSec, resultList)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error scanning root storage for unindexed files: ${e.message}")
        }
    }

    /**
     * Extract file path from URI for duplicate detection
     * Handles both content:// and file:// URIs
     */
    private fun getFilePath(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> uri.path ?: uri.toString()
            "content" -> {
                // Try to get actual file path from MediaStore content URI
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
}
