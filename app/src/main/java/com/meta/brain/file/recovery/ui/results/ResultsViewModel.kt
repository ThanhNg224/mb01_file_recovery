package com.meta.brain.file.recovery.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.repository.RestoreRepository
import com.meta.brain.file.recovery.data.repository.RestoreProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for restore operation
 */
sealed class RestoreState {
    object Idle : RestoreState()
    data class Running(
        val progress: Int,
        val total: Int,
        val currentFileName: String,
        val successCount: Int = 0,
        val failCount: Int = 0
    ) : RestoreState()
    data class Done(val successCount: Int, val failCount: Int, val destinationPath: String) : RestoreState()
    data class Error(val message: String) : RestoreState()
}

/**
 * ViewModel for managing selection and restore operations in ResultsFragment
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val restoreRepository: RestoreRepository
) : ViewModel() {

    // Selection state
    private val _selectedItems = MutableStateFlow<Set<MediaEntry>>(emptySet())
    val selectedItems: StateFlow<Set<MediaEntry>> = _selectedItems.asStateFlow()

    // Restore state
    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    // Selection mode
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private var restoreJob: Job? = null

    /**
     * Toggle selection for an item
     */
    fun toggleSelect(item: MediaEntry) {
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
     * Enter selection mode (typically triggered by long press)
     */
    fun enterSelectionMode(initialItem: MediaEntry? = null) {
        _isSelectionMode.value = true
        if (initialItem != null) {
            val currentSelection = _selectedItems.value.toMutableSet()
            currentSelection.add(initialItem)
            _selectedItems.value = currentSelection
        }
    }

    /**
     * Exit selection mode and clear selection
     */
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
    }

    /**
     * Select all items from the current list
     */
    fun selectAll(items: List<MediaEntry>) {
        _selectedItems.value = items.toSet()
        if (items.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    /**
     * Clear selection but keep selection mode active
     */
    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    /**
     * Check if an item is selected
     */
    fun isSelected(item: MediaEntry): Boolean {
        return _selectedItems.value.contains(item)
    }

    /**
     * Start restore operation
     */
    fun startRestore(destFolderName: String = "RELive/Restored") {
        val itemsToRestore = _selectedItems.value.toList()
        if (itemsToRestore.isEmpty()) {
            _restoreState.value = RestoreState.Error("No items selected")
            return
        }

        // Cancel any existing restore job
        restoreJob?.cancel()

        restoreJob = viewModelScope.launch {
            try {
                _restoreState.value = RestoreState.Running(0, itemsToRestore.size, "", 0, 0)

                restoreRepository.restoreMediaFiles(itemsToRestore, destFolderName)
                    .collect { progress ->
                        _restoreState.value = RestoreState.Running(
                            progress = progress.current,
                            total = progress.total,
                            currentFileName = progress.currentFileName,
                            successCount = progress.successCount,
                            failCount = progress.failCount
                        )

                        // Check if completed
                        if (progress.current >= progress.total) {
                            val destinationPath = "Downloads/$destFolderName"
                            _restoreState.value = RestoreState.Done(
                                successCount = progress.successCount,
                                failCount = progress.failCount,
                                destinationPath = destinationPath
                            )

                            // Clear selection after successful restore
                            exitSelectionMode()
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("ResultsViewModel", "Restore failed: ${e.message}", e)
                _restoreState.value = RestoreState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Cancel ongoing restore operation
     */
    fun cancelRestore() {
        restoreJob?.cancel()
        restoreJob = null
        _restoreState.value = RestoreState.Idle
    }

    /**
     * Reset restore state to idle
     */
    fun resetRestoreState() {
        _restoreState.value = RestoreState.Idle
    }

    /**
     * Get total size of selected items
     */
    fun getSelectedTotalSize(): Long {
        return restoreRepository.calculateTotalSize(_selectedItems.value.toList())
    }

    /**
     * Get formatted total size
     */
    fun getFormattedSelectedSize(): String {
        return restoreRepository.formatSize(getSelectedTotalSize())
    }

    override fun onCleared() {
        super.onCleared()
        cancelRestore()
    }
}

