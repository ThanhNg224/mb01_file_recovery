package com.meta.brain.file.recovery.ui.results

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Date filter preset options
 */
enum class DatePreset {
    ANY,
    LAST_1_MONTH,
    LAST_6_MONTHS,
    CUSTOM
}

/**
 * Size filter preset options
 */
enum class SizePreset {
    ANY,
    LT_1MB,      // 0-1MB
    FROM_1_TO_5MB, // 1-5MB
    GT_5MB,      // >5MB
    CUSTOM
}

/**
 * Sort field options
 */
enum class SortBy {
    DATE,
    SIZE,
    NAME
}

/**
 * Sort direction options
 */
enum class SortDirection {
    ASC,
    DESC
}

/**
 * Immutable specification for filtering and sorting results
 */
@Parcelize
data class ResultsFilterSpec(
    val datePreset: DatePreset = DatePreset.ANY,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val sizePreset: SizePreset = SizePreset.ANY,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val sortBy: SortBy = SortBy.DATE,
    val sortDir: SortDirection = SortDirection.DESC,
    val spanCount: Int = 3
) : Parcelable {
    companion object {
        // Default filter spec
        val DEFAULT = ResultsFilterSpec()

        // Size constants
        const val ONE_MB = 1024L * 1024L
        const val FIVE_MB = 5L * 1024L * 1024L

        // Time constants
        const val ONE_MONTH_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val SIX_MONTHS_MILLIS = 180L * 24L * 60L * 60L * 1000L
    }

    /**
     * Get the effective date range in milliseconds based on preset or custom values
     */
    fun getEffectiveDateRange(): Pair<Long?, Long?> {
        return when (datePreset) {
            DatePreset.ANY -> null to null
            DatePreset.LAST_1_MONTH -> {
                val now = System.currentTimeMillis()
                (now - ONE_MONTH_MILLIS) to now
            }
            DatePreset.LAST_6_MONTHS -> {
                val now = System.currentTimeMillis()
                (now - SIX_MONTHS_MILLIS) to now
            }
            DatePreset.CUSTOM -> fromMillis to toMillis
        }
    }

    /**
     * Get the effective size range in bytes based on preset or custom values
     */
    fun getEffectiveSizeRange(): Pair<Long?, Long?> {
        return when (sizePreset) {
            SizePreset.ANY -> null to null
            SizePreset.LT_1MB -> 0L to ONE_MB
            SizePreset.FROM_1_TO_5MB -> ONE_MB to FIVE_MB
            SizePreset.GT_5MB -> FIVE_MB to Long.MAX_VALUE
            SizePreset.CUSTOM -> minSizeBytes to maxSizeBytes
        }
    }

    /**
     * Check if any filters are active (not default)
     */
    fun hasActiveFilters(): Boolean {
        return datePreset != DatePreset.ANY ||
                sizePreset != SizePreset.ANY ||
                sortBy != SortBy.DATE ||
                sortDir != SortDirection.DESC ||
                spanCount != 3
    }
}
