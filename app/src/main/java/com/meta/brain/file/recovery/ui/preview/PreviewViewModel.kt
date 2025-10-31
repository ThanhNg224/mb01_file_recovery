package com.meta.brain.file.recovery.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.repository.FileOperationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for file operations in preview
 */
sealed class PreviewFileOpState {
    object Idle : PreviewFileOpState()
    data class Deleting(val fileName: String) : PreviewFileOpState()
    data class Deleted(val success: Boolean) : PreviewFileOpState()
    data class Restoring(val fileName: String) : PreviewFileOpState()
    data class Restored(val success: Boolean, val destinationPath: String) : PreviewFileOpState()
    data class Error(val message: String) : PreviewFileOpState()
}

/**
 * One-time UI events for Preview screen
 */
sealed class PreviewUiEvent {
    data class ShowMessage(val message: String) : PreviewUiEvent()
    object NavigateBack : PreviewUiEvent()
    data class NotifyFileChanged(val fromArchive: Boolean) : PreviewUiEvent()
}

/**
 * ViewModel for Preview screen file operations
 * Handles delete and restore operations via FileOperationRepository
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val fileOperationRepository: FileOperationRepository
) : ViewModel() {

    private val _fileOpState = MutableStateFlow<PreviewFileOpState>(PreviewFileOpState.Idle)
    val fileOpState: StateFlow<PreviewFileOpState> = _fileOpState.asStateFlow()

    private val _uiEvent = Channel<PreviewUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private var operationJob: Job? = null

    /**
     * Delete a single file
     */
    fun deleteSingleFile(entry: MediaEntry, fromArchive: Boolean) {
        operationJob?.cancel()

        operationJob = viewModelScope.launch {
            try {
                val fileName = entry.displayName ?: "Unknown"
                _fileOpState.value = PreviewFileOpState.Deleting(fileName)

                val success = fileOperationRepository.deleteSingleFile(entry)

                if (success) {
                    _fileOpState.value = PreviewFileOpState.Deleted(true)
                    _uiEvent.send(PreviewUiEvent.ShowMessage("File deleted"))
                    _uiEvent.send(PreviewUiEvent.NotifyFileChanged(fromArchive))

                    // Navigate back after showing message
                    kotlinx.coroutines.delay(500)
                    _uiEvent.send(PreviewUiEvent.NavigateBack)
                } else {
                    _fileOpState.value = PreviewFileOpState.Deleted(false)
                    _uiEvent.send(PreviewUiEvent.ShowMessage("Failed to delete file"))
                }
            } catch (e: Exception) {
                android.util.Log.e("PreviewViewModel", "Delete failed: ${e.message}", e)
                _fileOpState.value = PreviewFileOpState.Error(e.message ?: "Unknown error")
                _uiEvent.send(PreviewUiEvent.ShowMessage("Error: ${e.message}"))
            } finally {
                // Reset state after a delay
                kotlinx.coroutines.delay(1000)
                _fileOpState.value = PreviewFileOpState.Idle
            }
        }
    }

    /**
     * Restore a single file
     */
    fun restoreSingleFile(entry: MediaEntry, destFolderName: String = "RELive/Restored") {
        operationJob?.cancel()

        operationJob = viewModelScope.launch {
            try {
                val fileName = entry.displayName ?: "Unknown"
                _fileOpState.value = PreviewFileOpState.Restoring(fileName)

                // Use the repository's restore method with single item
                val destinationPath = "Downloads/$destFolderName"

                fileOperationRepository.restoreMediaFiles(listOf(entry), destFolderName)
                    .collect { progress ->
                        if (progress.current >= progress.total) {
                            val restored = progress.successCount > 0
                            if (restored) {
                                _fileOpState.value = PreviewFileOpState.Restored(true, destinationPath)
                                _uiEvent.send(PreviewUiEvent.ShowMessage("Restoring $fileName..."))
                            } else {
                                _fileOpState.value = PreviewFileOpState.Restored(false, "")
                                _uiEvent.send(PreviewUiEvent.ShowMessage("Failed to restore file"))
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("PreviewViewModel", "Restore failed: ${e.message}", e)
                _fileOpState.value = PreviewFileOpState.Error(e.message ?: "Unknown error")
                _uiEvent.send(PreviewUiEvent.ShowMessage("Error: ${e.message}"))
            } finally {
                // Reset state after a delay
                kotlinx.coroutines.delay(2000)
                _fileOpState.value = PreviewFileOpState.Idle
            }
        }
    }

    /**
     * Cancel ongoing operation
     */
    fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        _fileOpState.value = PreviewFileOpState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        cancelOperation()
    }
}

