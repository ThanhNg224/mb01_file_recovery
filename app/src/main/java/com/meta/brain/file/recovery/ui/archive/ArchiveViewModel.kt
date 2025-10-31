package com.meta.brain.file.recovery.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import com.meta.brain.file.recovery.data.repository.ArchiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Archive screen
 */
sealed class ArchiveUiState {
    object Loading : ArchiveUiState()
    data class Success(
        val files: List<MediaEntry>,
        val filteredFiles: List<MediaEntry>,
        val selectedFilter: MediaKind? = null
    ) : ArchiveUiState()
    data class Error(val message: String) : ArchiveUiState()
    object Empty : ArchiveUiState()
}

/**
 * ViewModel for Archive/Restored Files screen
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Loading)
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<MediaEntry>>(emptySet())
    val selectedItems: StateFlow<Set<MediaEntry>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private var allFiles: List<MediaEntry> = emptyList()

    init {
        loadRestoredFiles()
    }

    /**
     * Load all restored files from storage
     */
    fun loadRestoredFiles() {
        viewModelScope.launch {
            _uiState.value = ArchiveUiState.Loading
            try {
                val files = archiveRepository.loadRestoredFiles()
                allFiles = files
                if (files.isEmpty()) {
                    _uiState.value = ArchiveUiState.Empty
                } else {
                    _uiState.value = ArchiveUiState.Success(
                        files = files,
                        filteredFiles = files,
                        selectedFilter = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ArchiveUiState.Error(e.message ?: "Failed to load restored files")
            }
        }
    }

    /**
     * Filter files by MediaKind
     */
    fun filterByKind(kind: MediaKind?) {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            val filtered = if (kind == null) {
                allFiles
            } else {
                allFiles.filter { it.mediaKind == kind }
            }
            _uiState.value = currentState.copy(
                filteredFiles = filtered,
                selectedFilter = kind
            )
        }
    }

    /**
     * Toggle item selection
     */
    fun toggleItemSelection(item: MediaEntry) {
        val currentSelection = _selectedItems.value.toMutableSet()
        if (currentSelection.contains(item)) {
            currentSelection.remove(item)
        } else {
            currentSelection.add(item)
        }
        _selectedItems.value = currentSelection

        // Exit selection mode if no items selected
        if (currentSelection.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    /**
     * Enable selection mode
     */
    fun enterSelectionMode(initialItem: MediaEntry? = null) {
        _isSelectionMode.value = true
        if (initialItem != null) {
            _selectedItems.value = setOf(initialItem)
        }
    }

    /**
     * Exit selection mode
     */
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
    }

    /**
     * Select all visible items
     */
    fun selectAll() {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            _selectedItems.value = currentState.filteredFiles.toSet()
            _isSelectionMode.value = true
        }
    }

    /**
     * Get statistics for restored files
     */
    fun getStatistics(): Map<MediaKind, Int> {
        return allFiles.groupBy { it.mediaKind }
            .mapValues { it.value.size }
    }

    /**
     * Calculate total size of selected items
     */
    fun getSelectedSize(): Long {
        return _selectedItems.value.sumOf { it.size }
    }

    /**
     * Refresh the list
     */
    fun refresh() {
        loadRestoredFiles()
    }
}

