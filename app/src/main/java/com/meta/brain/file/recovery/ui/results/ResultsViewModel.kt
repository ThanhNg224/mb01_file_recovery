package com.meta.brain.file.recovery.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.repository.FileOperationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sort type enumeration
 */
enum class SortType {
    SIZE_SMALL_TO_LARGE,
    SIZE_LARGE_TO_SMALL,
    DATE_OLD_TO_NEW,
    DATE_NEW_TO_OLD
}

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
 * State for delete operation
 */
sealed class DeleteState {
    object Idle : DeleteState()
    data class Done(val successCount: Int, val failCount: Int) : DeleteState()
    data class Error(val message: String) : DeleteState()
}

/**
 * ViewModel for managing selection, sorting, restore and delete operations in ResultsFragment
 * Follows Clean Architecture - keeps business logic separate from UI
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val fileOperationRepository: FileOperationRepository
) : ViewModel() {

    // Media items state
    private val _allItems = MutableStateFlow<List<MediaEntry>>(emptyList())
    val allItems: StateFlow<List<MediaEntry>> = _allItems.asStateFlow()

    // Sorted items state
    private val _sortedItems = MutableStateFlow<List<MediaEntry>>(emptyList())
    val sortedItems: StateFlow<List<MediaEntry>> = _sortedItems.asStateFlow()

    // Current sort type
    private val _currentSortType = MutableStateFlow(SortType.DATE_OLD_TO_NEW)
    val currentSortType: StateFlow<SortType> = _currentSortType.asStateFlow()

    // Selection state
    private val _selectedItems = MutableStateFlow<Set<MediaEntry>>(emptySet())
    val selectedItems: StateFlow<Set<MediaEntry>> = _selectedItems.asStateFlow()

    // Restore state
    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    // Delete state
    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    // Selection mode
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // Folder count (derived data)
    private val _folderCount = MutableStateFlow(0)
    val folderCount: StateFlow<Int> = _folderCount.asStateFlow()

    private var restoreJob: Job? = null

    /**
     * Initialize items from scan results
     */
    fun setItems(items: List<MediaEntry>) {
        _allItems.value = items
        applySorting(_currentSortType.value)
        updateFolderCount()
    }

    /**
     * Apply sorting to items
     */
    fun applySorting(sortType: SortType) {
        _currentSortType.value = sortType
        val sorted = sortItems(_allItems.value, sortType)
        _sortedItems.value = sorted
    }

    /**
     * Internal sorting logic - Pure function for testability
     */
    private fun sortItems(items: List<MediaEntry>, sortType: SortType): List<MediaEntry> {
        return when (sortType) {
            SortType.SIZE_SMALL_TO_LARGE -> items.sortedBy { it.size }
            SortType.SIZE_LARGE_TO_SMALL -> items.sortedByDescending { it.size }
            SortType.DATE_OLD_TO_NEW -> items.sortedBy { it.dateTaken ?: it.dateAdded }
            SortType.DATE_NEW_TO_OLD -> items.sortedByDescending { it.dateTaken ?: it.dateAdded }
        }
    }

    /**
     * Calculate folder count from items
     */
    private fun updateFolderCount() {
        _folderCount.value = _allItems.value
            .mapNotNull { it.filePath?.substringBeforeLast("/") }
            .distinct()
            .size
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

                fileOperationRepository.restoreMediaFiles(itemsToRestore, destFolderName)
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
     * Start delete operation
     */
    fun startDelete() {
        val itemsToDelete = _selectedItems.value.toList()
        if (itemsToDelete.isEmpty()) {
            _deleteState.value = DeleteState.Error("No items selected")
            return
        }

        viewModelScope.launch {
            try {
                val result = fileOperationRepository.deleteMediaFiles(itemsToDelete)

                // Remove successfully deleted items from the list
                removeDeletedItems(result.successfulItems)

                // Notify completion
                _deleteState.value = DeleteState.Done(
                    successCount = result.successCount,
                    failCount = result.failCount
                )

                // Exit selection mode
                exitSelectionMode()
            } catch (e: Exception) {
                android.util.Log.e("ResultsViewModel", "Delete failed: ${e.message}", e)
                _deleteState.value = DeleteState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Remove deleted items from the list (called after deletion)
     */
    fun removeDeletedItems(deletedItems: List<MediaEntry>) {
        val updatedItems = _allItems.value.filterNot { deletedItems.contains(it) }
        _allItems.value = updatedItems
        applySorting(_currentSortType.value)
        updateFolderCount()
    }

    /**
     * Notify delete completion with results (deprecated - use startDelete instead)
     */
    @Deprecated("Use startDelete() instead which handles deletion internally")
    fun notifyDeleteComplete(successCount: Int, failCount: Int) {
        _deleteState.value = DeleteState.Done(successCount, failCount)
    }

    /**
     * Reset delete state
     */
    fun resetDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

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
        return fileOperationRepository.calculateTotalSize(_selectedItems.value.toList())
    }

    /**
     * Get formatted total size
     */
    fun getFormattedSelectedSize(): String {
        return fileOperationRepository.formatSize(getSelectedTotalSize())
    }

    override fun onCleared() {
        super.onCleared()
        cancelRestore()
    }
}

