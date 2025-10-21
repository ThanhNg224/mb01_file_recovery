package com.meta.brain.file.recovery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.model.ScanCursor
import com.meta.brain.file.recovery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for media scanning
 */
sealed class MediaUiState {
    object Idle : MediaUiState()
    object Loading : MediaUiState()
    data class Items(
        val list: List<MediaEntry>,
        val canLoadMore: Boolean,
        val appending: Boolean = false
    ) : MediaUiState()
    object Empty : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    // Internal state for pagination
    private var currentCursor: ScanCursor? = null
    private val mediaBuffer = mutableListOf<MediaEntry>()
    private var isLoading = false

    /**
     * Start deep scan with comprehensive file system scanning
     * Includes hidden files, archive files, trash, and unindexed files
     */
    fun deepScan(
        types: Set<MediaType>,
        minSize: Long? = null,
        fromSec: Long? = null,
        toSec: Long? = null
    ) {
        if (isLoading) return

        viewModelScope.launch {
            try {
                isLoading = true
                _uiState.value = MediaUiState.Loading

                // Reset state for new scan
                mediaBuffer.clear()
                currentCursor = null

                val result = mediaRepository.deepScan(
                    types = types,
                    minSize = minSize,
                    fromSec = fromSec,
                    toSec = toSec,
                    pageSize = PAGE_SIZE,
                    cursor = null
                )

                mediaBuffer.addAll(result.items)
                currentCursor = result.nextCursor

                _uiState.value = if (result.items.isEmpty()) {
                    MediaUiState.Empty
                } else {
                    MediaUiState.Items(
                        list = mediaBuffer.toList(),
                        canLoadMore = result.nextCursor != null,
                        appending = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error(
                    e.message ?: "Unknown error occurred during deep scan"
                )
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Load next page of media items
     */
    fun loadMore() {
        val currentState = _uiState.value
        if (isLoading || currentCursor == null || currentState !is MediaUiState.Items) return

        viewModelScope.launch {
            try {
                isLoading = true

                // Show appending indicator
                _uiState.value = currentState.copy(appending = true)

                val result = mediaRepository.deepScan(
                    types = setOf(MediaType.ALL),
                    pageSize = PAGE_SIZE,
                    cursor = currentCursor
                )

                mediaBuffer.addAll(result.items)
                currentCursor = result.nextCursor

                _uiState.value = MediaUiState.Items(
                    list = mediaBuffer.toList(),
                    canLoadMore = result.nextCursor != null,
                    appending = false
                )
            } catch (_: Exception) {
                // Revert to previous state on error
                _uiState.value = currentState.copy(appending = false)
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Clear scan results and reset to idle
     */
    fun clearResults() {
        mediaBuffer.clear()
        currentCursor = null
        _uiState.value = MediaUiState.Idle
    }

    companion object {
        private const val PAGE_SIZE = 300
    }
}
