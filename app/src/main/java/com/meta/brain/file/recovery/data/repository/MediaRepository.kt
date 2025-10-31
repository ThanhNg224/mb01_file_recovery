package com.meta.brain.file.recovery.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.meta.brain.file.recovery.data.datasource.MediaStoreDataSource
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.model.ScanCursor
import com.meta.brain.file.recovery.data.model.ScanResult
import com.meta.brain.file.recovery.data.scanner.DeletedFileScanner
import com.meta.brain.file.recovery.data.scanner.HiddenFileScanner
import com.meta.brain.file.recovery.data.scanner.TrashScanner
import com.meta.brain.file.recovery.data.scanner.UnindexedFileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MediaRepository @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val hiddenFileScanner: HiddenFileScanner,
    private val trashScanner: TrashScanner,
    private val deletedFileScanner: DeletedFileScanner,
    private val unindexedFileScanner: UnindexedFileScanner
) {

    /**
     * Load thumbnail for media item (API 29+)
     */
    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        mediaStoreDataSource.loadThumbnail(uri, width, height)
    }

    /**
     * Performs comprehensive deep scan including MediaStore, hidden files, trash, and unindexed files
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

        val allItems = mutableListOf<com.meta.brain.file.recovery.data.model.MediaEntry>()

        // 1. Regular MediaStore scan
        if (types.contains(MediaType.IMAGES) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning images...")
            val images = mediaStoreDataSource.queryImages(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${images.size} images")
            allItems.addAll(images)
        }

        if (types.contains(MediaType.VIDEOS) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning videos...")
            val videos = mediaStoreDataSource.queryVideos(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${videos.size} videos")
            allItems.addAll(videos)
        }

        if (types.contains(MediaType.AUDIO) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning audio...")
            val audio = mediaStoreDataSource.queryAudio(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${audio.size} audio files")
            allItems.addAll(audio)
        }

        if (types.contains(MediaType.DOCUMENTS) || types.contains(MediaType.ALL)) {
            android.util.Log.d("MediaRepository", "Deep scanning documents...")
            val documents = mediaStoreDataSource.queryDocuments(minSize, fromSec, toSec, pageSize, cursor)
            android.util.Log.d("MediaRepository", "Found ${documents.size} documents")
            allItems.addAll(documents)
        }

        // 2. Scan hidden files and directories
        android.util.Log.d("MediaRepository", "Deep scanning hidden files...")
        val hiddenFiles = hiddenFileScanner.scan(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${hiddenFiles.size} hidden files")
        allItems.addAll(hiddenFiles)

        // 3. Scan trash/recycle bin directories
        android.util.Log.d("MediaRepository", "Scanning trash directories...")
        val trashFiles = trashScanner.scan(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${trashFiles.size} trash files")
        allItems.addAll(trashFiles)

        // 4. Scan for recently deleted files (Android 11+)
        android.util.Log.d("MediaRepository", "Scanning deleted files...")
        val deletedFiles = deletedFileScanner.scan(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${deletedFiles.size} deleted files")
        allItems.addAll(deletedFiles)

        // 5. File system deep scan for unindexed files
        android.util.Log.d("MediaRepository", "Scanning unindexed files...")
        val unindexedFiles = unindexedFileScanner.scan(types, minSize, fromSec, toSec)
        android.util.Log.d("MediaRepository", "Found ${unindexedFiles.size} unindexed files")
        allItems.addAll(unindexedFiles)

        // Remove duplicates based on actual file path
        val uniqueItems = allItems
            .groupBy { mediaStoreDataSource.getFilePath(it.uri) }
            .mapValues { (_, entries) ->
                // Prefer content:// URI over file:// URI
                entries.firstOrNull { it.uri.scheme == "content" } ?: entries.first()
            }
            .values
            .sortedWith(
                compareByDescending<com.meta.brain.file.recovery.data.model.MediaEntry> { it.dateTaken ?: it.dateAdded }
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
}

