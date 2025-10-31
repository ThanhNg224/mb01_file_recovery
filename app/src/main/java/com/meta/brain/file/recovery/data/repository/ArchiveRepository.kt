package com.meta.brain.file.recovery.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing restored/archived files from the RELive/Restored folder
 */
@Singleton
class ArchiveRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val RESTORED_FOLDER_PATH = "RELive/Restored"
    }

    /**
     * Load all restored files from the RELive/Restored folder in Downloads
     */
    suspend fun loadRestoredFiles(): List<MediaEntry> = withContext(Dispatchers.IO) {
        val restoredFiles = mutableListOf<MediaEntry>()

        // Query different MediaStore collections for files in our restored folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - use scoped storage with RELATIVE_PATH
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$RESTORED_FOLDER_PATH/"

            // Query Images
            restoredFiles.addAll(queryMediaCollection(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath,
                isVideo = false
            ))

            // Query Videos
            restoredFiles.addAll(queryMediaCollection(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath,
                isVideo = true
            ))

            // Query Audio
            restoredFiles.addAll(queryMediaCollection(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath,
                isVideo = false
            ))

            // Query Downloads (for documents and other files)
            restoredFiles.addAll(queryMediaCollection(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath,
                isVideo = false
            ))
        } else {
            // Android 9 and below - use legacy approach
            restoredFiles.addAll(queryMediaCollectionLegacy(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false
            ))
            restoredFiles.addAll(queryMediaCollectionLegacy(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true
            ))
            restoredFiles.addAll(queryMediaCollectionLegacy(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                isVideo = false
            ))
        }

        // Sort by date added (newest first)
        restoredFiles.sortedByDescending { it.dateAdded }
    }

    /**
     * Query a MediaStore collection for files in the restored folder (Android 10+)
     */
    private fun queryMediaCollection(
        collection: Uri,
        relativePath: String,
        isVideo: Boolean
    ): List<MediaEntry> {
        val mediaList = mutableListOf<MediaEntry>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        // Add duration for video collection
        val videoProjection = if (isVideo) {
            projection + MediaStore.Video.Media.DURATION
        } else {
            projection
        }

        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(relativePath)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                collection,
                videoProjection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val durationColumn = if (isVideo) {
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val duration = if (durationColumn >= 0) {
                        cursor.getLong(durationColumn)
                    } else {
                        null
                    }

                    val contentUri = Uri.withAppendedPath(collection, id.toString())

                    mediaList.add(
                        MediaEntry(
                            uri = contentUri,
                            displayName = displayName,
                            mimeType = mimeType,
                            size = size,
                            dateAdded = dateAdded,
                            dateTaken = dateModified,
                            durationMs = duration,
                            isVideo = isVideo
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArchiveRepository", "Error querying collection: ${e.message}", e)
        }

        return mediaList
    }

    /**
     * Query a MediaStore collection for files in the restored folder (Android 9 and below)
     */
    private fun queryMediaCollectionLegacy(
        collection: Uri,
        isVideo: Boolean
    ): List<MediaEntry> {
        val mediaList = mutableListOf<MediaEntry>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA
        )

        val videoProjection = if (isVideo) {
            projection + MediaStore.Video.Media.DURATION
        } else {
            projection
        }

        // For legacy, we filter by file path containing our folder
        val selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("%Download/$RESTORED_FOLDER_PATH/%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                collection,
                videoProjection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val durationColumn = if (isVideo) {
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val duration = if (durationColumn >= 0) {
                        cursor.getLong(durationColumn)
                    } else {
                        null
                    }

                    val contentUri = Uri.withAppendedPath(collection, id.toString())

                    mediaList.add(
                        MediaEntry(
                            uri = contentUri,
                            displayName = displayName,
                            mimeType = mimeType,
                            size = size,
                            dateAdded = dateAdded,
                            dateTaken = dateModified,
                            durationMs = duration,
                            isVideo = isVideo
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArchiveRepository", "Error querying collection (legacy): ${e.message}", e)
        }

        return mediaList
    }

    /**
     * Get count of restored files
     */
    suspend fun getRestoredFilesCount(): Int = withContext(Dispatchers.IO) {
        loadRestoredFiles().size
    }

    /**
     * Filter restored files by MediaKind
     */
    suspend fun filterByKind(kind: MediaKind): List<MediaEntry> = withContext(Dispatchers.IO) {
        loadRestoredFiles().filter { it.mediaKind == kind }
    }
}

