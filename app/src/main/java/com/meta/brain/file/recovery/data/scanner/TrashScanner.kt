package com.meta.brain.file.recovery.data.scanner

import com.meta.brain.file.recovery.data.datasource.FileSystemDataSource
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner for trash and recycle bin directories
 * Responsibility: Find media files in trash/recycle bin locations
 */
@Singleton
class TrashScanner @Inject constructor(
    private val fileSystemDataSource: FileSystemDataSource
) {

    /**
     * Scan trash and recycle bin directories for media files
     */
    fun scan(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val trashFiles = mutableListOf<MediaEntry>()

        try {
            val trashDirs = fileSystemDataSource.getTrashDirectories()

            trashDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("TrashScanner", "Scanning trash directory: $dirPath")
                    fileSystemDataSource.scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, trashFiles)
                } else {
                    android.util.Log.d("TrashScanner", "Trash directory not accessible: $dirPath")
                }
            }

            // Scan for any directory with "trash" or "archive" in name
            scanForTrashNamedDirectories(types, minSize, fromSec, toSec, trashFiles)

        } catch (e: Exception) {
            android.util.Log.e("TrashScanner", "Error scanning trash directories: ${e.message}")
        }

        return trashFiles
    }

    /**
     * Scan root storage for directories with trash/archive/recycle in their names
     */
    private fun scanForTrashNamedDirectories(
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
                    rootDir.listFiles()?.forEach { dir ->
                        if (dir.isDirectory) {
                            val dirName = dir.name.lowercase()
                            if (dirName.contains("trash") ||
                                dirName.contains("archive") ||
                                dirName.contains("recycle") ||
                                dirName.contains("bin")) {
                                android.util.Log.d("TrashScanner", "Found trash-named directory: ${dir.path}")
                                fileSystemDataSource.scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, resultList)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TrashScanner", "Error scanning for trash-named directories: ${e.message}")
        }
    }
}

