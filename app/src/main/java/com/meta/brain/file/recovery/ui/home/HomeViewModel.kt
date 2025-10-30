package com.meta.brain.file.recovery.ui.home

import android.Manifest
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaScanKind
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.model.ScanConfig
import com.meta.brain.file.recovery.data.model.ScanCursor
import com.meta.brain.file.recovery.data.model.ScanDepth
import com.meta.brain.file.recovery.data.model.ScanTarget
import com.meta.brain.file.recovery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Home screen
 */
data class HomeUiState(
    val hasPermissions: Boolean = false,
    val isLoading: Boolean = false,
    val lastScanKind: MediaScanKind? = null
)

/**
 * One-time UI events for Home screen
 */
sealed class HomeUiEvent {
    data class NavigateToScan(val scanConfig: ScanConfig) : HomeUiEvent()
    object ShowPermissionDialog : HomeUiEvent()
    data class ShowManageStorageDialog(val showDialog: Boolean) : HomeUiEvent()
    data class ShowToast(val message: String) : HomeUiEvent()
    data class ShowError(val message: String) : HomeUiEvent()
    data class NavigateToHelp(val unit: Unit = Unit) : HomeUiEvent()
    data class NavigateToSettings(val unit: Unit = Unit) : HomeUiEvent()
    data class NavigateToArchive(val unit: Unit = Unit) : HomeUiEvent()
}

/**
 * UI State for media scanning (for ResultsFragment)
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

    // Home UI State
    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    // One-time UI events
    private val _uiEvent = Channel<HomeUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    // Media scanning state (for ResultsFragment)
    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    // Internal state for pagination
    private var currentCursor: ScanCursor? = null
    private val mediaBuffer = mutableListOf<MediaEntry>()
    private var isLoading = false

    // Persist scan parameters for pagination
    private var lastTypes: Set<MediaType> = setOf(MediaType.ALL)
    private var lastMinSize: Long? = null
    private var lastFromSec: Long? = null
    private var lastToSec: Long? = null

    /**
     * Update permission state
     */
    fun updatePermissions(hasPermissions: Boolean) {
        _homeUiState.value = _homeUiState.value.copy(hasPermissions = hasPermissions)
    }

    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is required and granted
     */
    fun checkManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true // Not required below Android 11
        }
    }

    /**
     * Handle tile click with permission check
     */
    fun onTileClicked(depth: ScanDepth, mediaKind: MediaScanKind) {
        if (_homeUiState.value.hasPermissions) {
            navigateToScan(depth, mediaKind)
        } else {
            viewModelScope.launch {
                _uiEvent.send(HomeUiEvent.ShowPermissionDialog)
            }
        }
    }

    /**
     * Navigate to scan with configuration
     */
    private fun navigateToScan(depth: ScanDepth, mediaKind: MediaScanKind) {
        viewModelScope.launch {
            // Check for MANAGE_EXTERNAL_STORAGE permission if scanning documents on Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkManageStoragePermission()) {
                _uiEvent.send(HomeUiEvent.ShowManageStorageDialog(true))
                return@launch
            }

            _homeUiState.value = _homeUiState.value.copy(
                isLoading = true,
                lastScanKind = mediaKind
            )

            val scanConfig = ScanConfig(
                target = ScanTarget.ALL,
                depth = depth,
                mediaKind = mediaKind,
                minDurationMs = 4000L,
                fromSec = null,
                toSec = null
            )

            try {
                _uiEvent.send(HomeUiEvent.NavigateToScan(scanConfig))
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Navigation error: ${e.message}")
                _uiEvent.send(HomeUiEvent.ShowError("Navigation error occurred"))
            } finally {
                _homeUiState.value = _homeUiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Handle help button click
     */
    fun onHelpClicked() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.NavigateToHelp())
        }
    }

    /**
     * Handle settings button click
     */
    fun onSettingsClicked() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.NavigateToSettings())
        }
    }

    /**
     * Handle archive button click
     */
    fun onArchiveClicked() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.NavigateToArchive())
        }
    }

    /**
     * Handle ads button/banner click
     */
    fun onAdsClicked(message: String) {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.ShowToast(message))
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission dialog
     */
    fun showManageStoragePermissionDialog() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.ShowManageStorageDialog(true))
        }
    }

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

        // Store parameters for pagination
        lastTypes = types
        lastMinSize = minSize
        lastFromSec = fromSec
        lastToSec = toSec

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

    companion object {
        private const val PAGE_SIZE = 300
    }
}
