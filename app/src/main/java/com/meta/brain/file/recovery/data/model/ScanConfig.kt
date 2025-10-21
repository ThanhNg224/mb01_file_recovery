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
 * Configuration for scan operation
 */
@Parcelize
data class ScanConfig(
    val target: ScanTarget = ScanTarget.PHOTOS,
    val depth: ScanDepth = ScanDepth.DEEP,
    val minDurationMs: Long = 4000L,
    val minSize: Long? = null,
    val fromSec: Long? = null,
    val toSec: Long? = null
) : Parcelable {

    /**
     * Convert ScanTarget to MediaType set
     */
    fun toMediaTypes(): Set<MediaType> {
        return when (target) {
            ScanTarget.PHOTOS -> setOf(MediaType.IMAGES)
            ScanTarget.VIDEOS -> setOf(MediaType.VIDEOS)
            ScanTarget.AUDIO -> setOf(MediaType.AUDIO)
            ScanTarget.DOCUMENTS -> setOf(MediaType.DOCUMENTS)
            ScanTarget.ALL -> setOf(MediaType.ALL)
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
}
