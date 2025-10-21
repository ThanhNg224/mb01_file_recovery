package com.meta.brain.file.recovery.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a group of media files by folder
 */
@Parcelize
data class MediaGroup(
    val folderName: String,
    val fileCount: Int,
    val previewItems: List<MediaEntry>, // Up to 3 items for preview
    val allItems: List<MediaEntry> // All items in this group
) : Parcelable {
    /**
     * Get total size of all items in the group
     */
    fun getTotalSize(): Long {
        return allItems.sumOf { it.size }
    }

    /**
     * Get formatted total size
     */
    fun getFormattedSize(): String {
        val totalSize = getTotalSize()
        return when {
            totalSize < 1024 -> "${totalSize}B"
            totalSize < 1024 * 1024 -> "${totalSize / 1024}KB"
            totalSize < 1024 * 1024 * 1024 -> "${totalSize / (1024 * 1024)}MB"
            else -> "${"%.1f".format(totalSize / (1024.0 * 1024.0 * 1024.0))}GB"
        }
    }
}

/**
 * Represents a date-based section within a folder group
 */
data class DateSection(
    val date: String, // yyyy-MM-dd format
    val displayDate: String, // formatted for display
    val items: List<MediaEntry>,
    val totalSize: Long
) {
    fun getFormattedSize(): String {
        return when {
            totalSize < 1024 -> "${totalSize}B"
            totalSize < 1024 * 1024 -> "${totalSize / 1024}KB"
            totalSize < 1024 * 1024 * 1024 -> "${totalSize / (1024 * 1024)}MB"
            else -> "${"%.1f".format(totalSize / (1024.0 * 1024.0 * 1024.0))}GB"
        }
    }
}

