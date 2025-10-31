package com.meta.brain.file.recovery.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.meta.brain.file.recovery.data.datasource.FileSystemDataSource
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.util.MediaEntryFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner for deleted/archived files
 * Responsibility: Find recently deleted files that may still exist in temporary locations
 */
@Singleton
class DeletedFileScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileSystemDataSource: FileSystemDataSource,
    private val mediaEntryFactory: MediaEntryFactory
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Scan for deleted/archived files
     */
    fun scan(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val deletedFiles = mutableListOf<MediaEntry>()

        try {
            // On Android 11+, try to access recently deleted files through MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scanMediaStoreDeleted(types, minSize, fromSec, toSec, deletedFiles)
            }

            // Check temporary directories where deleted files might still exist
            val tempDirs = fileSystemDataSource.getTempDirectories()

            tempDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("DeletedFileScanner", "Scanning temp/archive directory: $dirPath")
                    fileSystemDataSource.scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, deletedFiles)
                }
            }

            // Scan for orphaned files (files with no parent directory reference)
            scanOrphanedFiles(types, minSize, fromSec, toSec, deletedFiles)

        } catch (e: Exception) {
            android.util.Log.e("DeletedFileScanner", "Error scanning deleted files: ${e.message}")
        }

        return deletedFiles
    }

    /**
     * Scan MediaStore for recently deleted files (Android 11+)
     */
    private fun scanMediaStoreDeleted(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )

                // Query files from the last 30 days
                contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} > ?",
                    arrayOf((System.currentTimeMillis() / 1000 - 30 * 24 * 60 * 60).toString()),
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(0)
                            val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())

                            // Check if file actually exists (if not, it might be deleted)
                            val fileExists = try {
                                contentResolver.openInputStream(uri)?.close()
                                true
                            } catch (_: Exception) {
                                false
                            }

                            if (!fileExists) {
                                // File doesn't exist but is still in MediaStore - might be recently deleted
                                val mediaEntry = mediaEntryFactory.createFromCursor(cursor, uri, types, minSize, fromSec, toSec)
                                mediaEntry?.let { resultList.add(it) }
                            }
                        } catch (_: Exception) {
                            // Continue with next file
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DeletedFileScanner", "Error scanning MediaStore for deleted files: ${e.message}")
        }
    }

    /**
     * Scan for orphaned files (files in MediaStore but don't physically exist)
     */
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

                while (cursor.moveToNext() && count < maxCount) {
                    try {
                        if (dataColumn >= 0) {
                            val filePath = cursor.getString(dataColumn)
                            val file = java.io.File(filePath)

                            // If file doesn't physically exist, it's orphaned
                            if (!file.exists()) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                                val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())

                                val mediaEntry = mediaEntryFactory.createFromCursor(cursor, uri, types, minSize, fromSec, toSec)
                                mediaEntry?.let {
                                    android.util.Log.d("DeletedFileScanner", "Found orphaned file: $filePath")
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
            android.util.Log.e("DeletedFileScanner", "Error scanning for orphaned files: ${e.message}")
        }
    }
}

