package com.meta.brain.file.recovery.data.scanner

import com.meta.brain.file.recovery.data.datasource.FileSystemDataSource
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner for hidden files and directories
 * Responsibility: Find media files in hidden locations (.hidden, .thumbnails, etc.)
 */
@Singleton
class HiddenFileScanner @Inject constructor(
    private val fileSystemDataSource: FileSystemDataSource
) {

    /**
     * Scan for hidden files in known hidden directories
     */
    fun scan(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?
    ): List<MediaEntry> {
        val hiddenFiles = mutableListOf<MediaEntry>()

        try {
            val hiddenDirs = fileSystemDataSource.getHiddenFileDirectories()

            hiddenDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    android.util.Log.d("HiddenFileScanner", "Scanning hidden directory: $dirPath")
                    fileSystemDataSource.scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, hiddenFiles)
                }
            }

            // Scan for dot files in common directories
            scanForDotFiles(types, minSize, fromSec, toSec, hiddenFiles)

            // Scan for .nomedia directories
            scanNoMediaDirectories(types, minSize, fromSec, toSec, hiddenFiles)

        } catch (e: Exception) {
            android.util.Log.e("HiddenFileScanner", "Error scanning hidden files: ${e.message}")
        }

        return hiddenFiles
    }

    /**
     * Scan for files starting with "." (hidden files)
     */
    private fun scanForDotFiles(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
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
                        if (file.isDirectory) {
                            fileSystemDataSource.scanDirectoryRecursively(file, types, minSize, fromSec, toSec, resultList)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HiddenFileScanner", "Error scanning for dot files: ${e.message}")
        }
    }

    /**
     * Scan directories containing .nomedia files
     */
    private fun scanNoMediaDirectories(
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            val commonDirs = listOf(
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Documents"
            ) + fileSystemDataSource.getMessagingAppDirectories()

            commonDirs.forEach { dirPath ->
                val dir = java.io.File(dirPath)
                if (dir.exists() && dir.canRead()) {
                    // Check for .nomedia file in the directory
                    val nomediaFile = java.io.File(dir, ".nomedia")
                    if (nomediaFile.exists() && nomediaFile.isFile) {
                        // If .nomedia file exists, scan this directory
                        fileSystemDataSource.scanDirectoryRecursively(dir, types, minSize, fromSec, toSec, resultList)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HiddenFileScanner", "Error scanning .nomedia directories: ${e.message}")
        }
    }
}

