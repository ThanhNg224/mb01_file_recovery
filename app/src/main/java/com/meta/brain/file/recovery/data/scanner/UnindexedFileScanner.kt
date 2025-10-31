package com.meta.brain.file.recovery.data.scanner

import com.meta.brain.file.recovery.data.datasource.FileSystemDataSource
import com.meta.brain.file.recovery.data.datasource.MediaStoreDataSource
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner for unindexed files (files not in MediaStore)
 * Responsibility: Find media files that exist in file system but are not indexed by MediaStore
 */
@Singleton
class UnindexedFileScanner @Inject constructor(
    private val fileSystemDataSource: FileSystemDataSource,
    private val mediaStoreDataSource: MediaStoreDataSource
) {

    /**
     * Scan for unindexed files in common directories
     */
    fun scan(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val unindexedFiles = mutableListOf<MediaEntry>()

        try {
            // Scan standard directories
            val standardDirs = fileSystemDataSource.getStandardDirectories()
            standardDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("UnindexedFileScanner", "Scanning for unindexed files in: $dirPath")
                    fileSystemDataSource.scanDirectoryForUnindexed(
                        dir, types, minSize, fromSec, toSec,
                        { file -> mediaStoreDataSource.isFileInMediaStore(file) },
                        unindexedFiles
                    )
                }
            }

            // Scan messaging app directories
            val messagingDirs = fileSystemDataSource.getMessagingAppDirectories()
            messagingDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("UnindexedFileScanner", "Scanning messaging app directory: $dirPath")
                    fileSystemDataSource.scanDirectoryForUnindexed(
                        dir, types, minSize, fromSec, toSec,
                        { file -> mediaStoreDataSource.isFileInMediaStore(file) },
                        unindexedFiles
                    )
                }
            }

            // Scan root storage for unindexed files
            scanRootStorageForUnindexed(types, minSize, fromSec, toSec, unindexedFiles)

        } catch (e: Exception) {
            android.util.Log.e("UnindexedFileScanner", "Error scanning unindexed files: ${e.message}")
        }

        return unindexedFiles
    }

    /**
     * Scan root storage for any unindexed media files
     */
    private fun scanRootStorageForUnindexed(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            val rootPaths = fileSystemDataSource.getRootStoragePaths()

            rootPaths.forEach { rootPath ->
                val rootDir = java.io.File(rootPath)
                if (rootDir.exists() && rootDir.canRead()) {
                    // Only scan first level and selected custom directories
                    rootDir.listFiles()?.forEach { file ->
                        if (file.isDirectory && !file.name.startsWith(".")) {
                            // Scan custom media directories
                            val dirName = file.name.lowercase()
                            if (!dirName.startsWith("android") &&
                                (dirName.contains("media") ||
                                 dirName.contains("file") ||
                                 dirName.contains("photo") ||
                                 dirName.contains("video") ||
                                 dirName.contains("music") ||
                                 dirName.contains("document"))) {
                                android.util.Log.d("UnindexedFileScanner", "Scanning custom media directory: ${file.path}")
                                fileSystemDataSource.scanDirectoryForUnindexed(
                                    file, types, minSize, fromSec, toSec,
                                    { f -> mediaStoreDataSource.isFileInMediaStore(f) },
                                    resultList
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UnindexedFileScanner", "Error scanning root storage for unindexed files: ${e.message}")
        }
    }
}

