package com.meta.brain.file.recovery.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Media kind classification
 */
enum class MediaKind {
    IMAGE, VIDEO, DOCUMENT, AUDIO, OTHER
}

/**
 * Represents a media entry from MediaStore (Image, Video, or Document)
 */
@Parcelize
data class MediaEntry(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
    val size: Long,
    val dateAdded: Long,
    val dateTaken: Long?,
    val durationMs: Long? = null,
    val isVideo: Boolean,
    val isTrashed: Boolean = false,
    val mediaKind: MediaKind = determineMediaKind(mimeType, isVideo),
    val filePath: String? = null // Physical file path on device storage
) : Parcelable {
    /**
     * Returns formatted file size (e.g., "2.5 MB")
     */
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> "${"%.1f".format(size / (1024.0 * 1024.0 * 1024.0))}GB"
        }
    }

    /**
     * Returns formatted date for display
     */
    fun getFormattedDate(): String {
        val date = dateTaken ?: dateAdded
        return android.text.format.DateFormat.format("MMM dd, yyyy", date * 1000).toString()
    }

    companion object {
        /**
         * Determines MediaKind based on MIME type and isVideo flag
         */
        private fun determineMediaKind(mimeType: String?, isVideo: Boolean): MediaKind {
            return when {
                isVideo -> MediaKind.VIDEO
                mimeType?.startsWith("image/") == true -> MediaKind.IMAGE
                mimeType?.startsWith("application/") == true -> MediaKind.DOCUMENT
                mimeType?.contains("pdf") == true -> MediaKind.DOCUMENT
                mimeType?.contains("word") == true -> MediaKind.DOCUMENT
                mimeType?.contains("excel") == true -> MediaKind.DOCUMENT
                mimeType?.contains("powerpoint") == true -> MediaKind.DOCUMENT
                mimeType?.contains("zip") == true -> MediaKind.DOCUMENT
                mimeType?.contains("apk") == true -> MediaKind.DOCUMENT
                else -> MediaKind.OTHER
            }
        }
    }
}

/**
 * Media type filter
 */
enum class MediaType {
    IMAGES, VIDEOS, DOCUMENTS, AUDIO, ALL
}

/**
 * Pagination cursor for efficient loading
 */
data class ScanCursor(
    val lastDate: Long,
    val lastId: Long
)

/**
 * Result container for paginated scan
 */
data class ScanResult(
    val items: List<MediaEntry>,
    val nextCursor: ScanCursor?
)
