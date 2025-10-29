package com.meta.brain.file.recovery.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Scan target type
 */
enum class ScanTarget {
    PHOTOS,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    ALL
}

/**
 * Scan depth mode
 */
enum class ScanDepth {
    DEEP,
    NORMAL, // for photo scan
    VIDEO,
    AUDIO,
    OTHER
}

/**
 * Media scan kind - determines which media types to scan
 */
enum class MediaScanKind {
    IMAGE,   // Only scan image files
    VIDEO,   // Only scan video files
    AUDIO,   // Only scan audio files
    OTHER,   // Only scan document and other files
    ALL      // Scan all media types (Deep Scan)
}

/**
 * Configuration for scan operation
 */
@Parcelize
data class ScanConfig(
    val target: ScanTarget = ScanTarget.PHOTOS,
    val depth: ScanDepth = ScanDepth.DEEP,
    val mediaKind: MediaScanKind = MediaScanKind.ALL, // New field for specific media type filtering
    val minDurationMs: Long = 1500L,
    val minSize: Long? = null,
    val fromSec: Long? = null,
    val toSec: Long? = null
) : Parcelable {

    /**
     * Convert ScanTarget to MediaType set
     */
    fun toMediaTypes(): Set<MediaType> {
        // Use mediaKind if specified, otherwise fall back to target
        return when (mediaKind) {
            MediaScanKind.IMAGE -> setOf(MediaType.IMAGES)
            MediaScanKind.VIDEO -> setOf(MediaType.VIDEOS)
            MediaScanKind.AUDIO -> setOf(MediaType.AUDIO)
            MediaScanKind.OTHER -> setOf(MediaType.DOCUMENTS)
            MediaScanKind.ALL -> when (target) {
                ScanTarget.PHOTOS -> setOf(MediaType.IMAGES)
                ScanTarget.VIDEOS -> setOf(MediaType.VIDEOS)
                ScanTarget.AUDIO -> setOf(MediaType.AUDIO)
                ScanTarget.DOCUMENTS -> setOf(MediaType.DOCUMENTS)
                ScanTarget.ALL -> setOf(MediaType.ALL)
            }
        }
    }

    /**
     * Get display name for the target
     */
    fun getTargetDisplayName(): String {
        return when (target) {
            ScanTarget.PHOTOS -> "photos"
            ScanTarget.VIDEOS -> "videos"
            ScanTarget.AUDIO -> "audio"
            ScanTarget.DOCUMENTS -> "documents"
            ScanTarget.ALL -> "files"
        }
    }

    /**
     * Get scanning title based on media kind
     */
    fun getScanningTitle(): String {
        return when (mediaKind) {
            MediaScanKind.IMAGE -> "Scanning Photos..."
            MediaScanKind.VIDEO -> "Scanning Videos..."
            MediaScanKind.AUDIO -> "Scanning Audio..."
            MediaScanKind.OTHER -> "Scanning Documents..."
            MediaScanKind.ALL -> "Scanning..."
        }
    }

    /**
     * Get results screen title based on media kind
     */
    fun getResultsTitle(): String {
        return when (mediaKind) {
            MediaScanKind.IMAGE -> "Scan Photos"
            MediaScanKind.VIDEO -> "Scan Videos"
            MediaScanKind.AUDIO -> "Scan Audio"
            MediaScanKind.OTHER -> "Scan Documents"
            MediaScanKind.ALL -> "Scan Results"
        }
    }
}
