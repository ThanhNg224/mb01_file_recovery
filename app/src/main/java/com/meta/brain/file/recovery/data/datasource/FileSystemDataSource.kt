package com.meta.brain.file.recovery.data.datasource

import android.os.Environment
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.util.MediaEntryFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for file system operations
 * Handles direct file system access and recursive directory scanning
 */
@Singleton
class FileSystemDataSource @Inject constructor(
    private val mediaEntryFactory: MediaEntryFactory
) {

    /**
     * Scan a directory recursively for media files
     */
    fun scanDirectoryRecursively(
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
                    val mediaEntry = mediaEntryFactory.createFromFile(file, types, minSize, fromSec, toSec)
                    mediaEntry?.let { resultList.add(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSystemDataSource", "Error scanning directory ${directory.path}: ${e.message}")
        }
    }

    /**
     * Scan directory for unindexed files (files not in MediaStore)
     */
    fun scanDirectoryForUnindexed(
        directory: java.io.File,
        types: Set<MediaType>,
        minSize: Long?,
        fromSec: Long?,
        toSec: Long?,
        isFileInMediaStore: (java.io.File) -> Boolean,
        resultList: MutableList<MediaEntry>
    ) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    // Check if file is not in MediaStore (unindexed)
                    if (!isFileInMediaStore(file)) {
                        val mediaEntry = mediaEntryFactory.createFromFile(file, types, minSize, fromSec, toSec)
                        mediaEntry?.let { resultList.add(it) }
                    }
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    // Recursively scan non-hidden subdirectories
                    scanDirectoryForUnindexed(file, types, minSize, fromSec, toSec, isFileInMediaStore, resultList)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSystemDataSource", "Error scanning unindexed directory ${directory.path}: ${e.message}")
        }
    }

    /**
     * Get standard external storage directories
     */
    fun getStandardDirectories(): List<String> {
        val sdcardPath = Environment.getExternalStorageDirectory().path
        return listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/DCIM/Camera",
            "/storage/emulated/0/DCIM/Screenshots",
            "$sdcardPath/Download",
            "$sdcardPath/Downloads",
            "$sdcardPath/Documents",
            "$sdcardPath/Music",
            "$sdcardPath/Movies",
            "$sdcardPath/Pictures",
            "$sdcardPath/DCIM"
        )
    }

    /**
     * Get messaging app media directories
     */
    fun getMessagingAppDirectories(): List<String> {
        return listOf(
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Images",
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Video",
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Documents",
            "/storage/emulated/0/Telegram/Telegram Images",
            "/storage/emulated/0/Telegram/Telegram Video",
            "/storage/emulated/0/Telegram/Telegram Documents",
            "/storage/emulated/0/Instagram",
            "/storage/emulated/0/Snapchat",
            "/storage/emulated/0/TikTok",
            "/storage/emulated/0/Facebook",
            "/storage/emulated/0/Twitter"
        )
    }

    /**
     * Get hidden file directories
     */
    fun getHiddenFileDirectories(): List<String> {
        val sdcardPath = Environment.getExternalStorageDirectory().path
        return listOf(
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
            "/storage/emulated/0/.WhatsApp",
            "/storage/emulated/0/.Telegram",
            "/storage/emulated/0/.Instagram",
            "/storage/emulated/0/.Facebook",
            "/storage/emulated/0/.Snapchat",
            "/storage/emulated/0/.TikTok",
            "/storage/emulated/0/Android/data/.trash",
            "/storage/emulated/0/Android/media/.hidden"
        )
    }

    /**
     * Get trash/recycle bin directories
     */
    fun getTrashDirectories(): List<String> {
        val sdcardPath = Environment.getExternalStorageDirectory().path
        return listOf(
            "/storage/emulated/0/.Trash",
            "/storage/emulated/0/Trash",
            "/storage/emulated/0/.trash",
            "/storage/emulated/0/RECYCLE.BIN",
            "/storage/emulated/0/.recycle.bin",
            "/storage/emulated/0/.Trash-1000",
            "$sdcardPath/.Trash-1000",
            "$sdcardPath/Trash",
            "$sdcardPath/.trash",
            "/storage/emulated/0/Android/data/com.android.providers.media/cache/.trash",
            "/storage/emulated/0/Android/data/com.android.providers.media/.trash",
            "/storage/emulated/0/.RecycleBin",
            "/storage/emulated/0/Files/Trash",
            "/storage/emulated/0/.archive",
            "/storage/emulated/0/archive",
            "/storage/emulated/0/DCIM/.trashed",
            "/storage/emulated/0/Pictures/.trashed",
            "/storage/emulated/0/Pictures/Trash",
            "/storage/emulated/0/DCIM/Trash",
            "/storage/emulated/0/ES File Explorer/.trash",
            "/storage/emulated/0/File Manager/Trash",
            "/storage/emulated/0/.Asus/FileManager/.TrashCan",
            "/storage/emulated/0/MiDrive/.trash",
            "/storage/emulated/0/Android/data/com.google.android.apps.docs/files/.trash",
            "/storage/emulated/0/Android/data/com.dropbox.android/files/.trash",
            "/storage/emulated/0/WhatsApp/.Trash",
            "/storage/emulated/0/Telegram/.Trash",
            "/storage/emulated/0/Instagram/.Trash",
            "/storage/emulated/0/DCIM/.thumbnails_trash",
            "/storage/emulated/0/DCIM/Camera/.trash"
        )
    }

    /**
     * Get temporary/archive directories
     */
    fun getTempDirectories(): List<String> {
        val sdcardPath = Environment.getExternalStorageDirectory().path
        return listOf(
            "/storage/emulated/0/Android/data/com.android.providers.media/files/.pending",
            "/storage/emulated/0/Android/data/com.android.providers.media/files/.archive",
            "/storage/emulated/0/.temp",
            "/storage/emulated/0/tmp",
            "/storage/emulated/0/.tmp",
            "/storage/emulated/0/temp",
            "/storage/emulated/0/Android/data/com.android.gallery3d/cache",
            "/storage/emulated/0/Android/data/com.google.android.apps.photos/cache",
            "/data/local/tmp",
            "/cache",
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
    }

    /**
     * Get root storage paths
     */
    fun getRootStoragePaths(): List<String> {
        val sdcardPath = Environment.getExternalStorageDirectory().path
        return listOf(
            "/storage/emulated/0",
            sdcardPath
        )
    }
}

