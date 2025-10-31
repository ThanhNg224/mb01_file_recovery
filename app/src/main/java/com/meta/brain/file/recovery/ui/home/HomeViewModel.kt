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
    val hasManageStoragePermission: Boolean = false,
    val isLoading: Boolean = false,
    val lastScanKind: MediaScanKind? = null,
    val permissionsPermanentlyDenied: Boolean = false
)

/**
 * One-time UI events for Home screen
 */
sealed class HomeUiEvent {
    data class NavigateToScan(val scanConfig: ScanConfig) : HomeUiEvent()
    data class ShowBasicPermissionDialog(val permissions: List<String>) : HomeUiEvent()
    object ShowManageStoragePermissionDialog : HomeUiEvent()
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
        android.util.Log.d("HomeViewModel", "updatePermissions called with: $hasPermissions (previous: ${_homeUiState.value.hasPermissions})")
        _homeUiState.value = _homeUiState.value.copy(hasPermissions = hasPermissions)
    }

    /**
     * Update MANAGE_EXTERNAL_STORAGE permission state
     */
    fun updateManageStoragePermission(hasPermission: Boolean) {
        android.util.Log.d("HomeViewModel", "updateManageStoragePermission: $hasPermission")
        _homeUiState.value = _homeUiState.value.copy(hasManageStoragePermission = hasPermission)
    }

    /**
     * Update permanently denied state
     */
    fun updatePermissionsPermanentlyDenied(denied: Boolean) {
        android.util.Log.d("HomeViewModel", "updatePermissionsPermanentlyDenied: $denied")
        _homeUiState.value = _homeUiState.value.copy(permissionsPermanentlyDenied = denied)
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
        android.util.Log.d("HomeViewModel", "onTileClicked - hasPermissions: ${_homeUiState.value.hasPermissions}")
        if (_homeUiState.value.hasPermissions) {
            navigateToScan(depth, mediaKind)
        } else {
            android.util.Log.d("HomeViewModel", "Need basic permissions")
            requestBasicPermissions()
        }
    }

    /**
     * Request basic media permissions
     * ALWAYS shows custom dialog first, then user confirms to proceed
     */
    private fun requestBasicPermissions() {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "Showing custom permission dialog")
            // Always show custom dialog first with permission list
            _uiEvent.send(HomeUiEvent.ShowBasicPermissionDialog(getRequiredPermissions()))
        }
    }

    /**
     * Handle permission request result
     * Called by Fragment after user responds to permission request
     */
    fun onPermissionResult(allGranted: Boolean, anyPermanentlyDenied: Boolean) {
        android.util.Log.d("HomeViewModel", "onPermissionResult - granted: $allGranted, permanentlyDenied: $anyPermanentlyDenied")
        _homeUiState.value = _homeUiState.value.copy(
            hasPermissions = allGranted,
            permissionsPermanentlyDenied = anyPermanentlyDenied
        )

        if (!allGranted && anyPermanentlyDenied) {
            // User denied with "Don't ask again" - need to go to settings next time
            android.util.Log.d("HomeViewModel", "Permissions permanently denied by user")
        }
    }

    /**
     * Navigate to scan with configuration
     * Note: For file recovery, we need MANAGE_EXTERNAL_STORAGE to:
     * 1. Access deleted/hidden files in various locations
     * 2. Write recovered files to recovery folder
     * 3. Scan outside standard media directories
     */
    private fun navigateToScan(depth: ScanDepth, mediaKind: MediaScanKind) {
        viewModelScope.launch {
            // Re-check MANAGE_EXTERNAL_STORAGE permission in real-time
            val manageStorageGranted = checkManageStoragePermission()
            android.util.Log.d("HomeViewModel", "navigateToScan - MANAGE_EXTERNAL_STORAGE granted: $manageStorageGranted")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !manageStorageGranted) {
                android.util.Log.d("HomeViewModel", "Need MANAGE_EXTERNAL_STORAGE permission - showing custom dialog")
                // Show custom dialog first, user clicks Confirm to go to settings
                _uiEvent.send(HomeUiEvent.ShowManageStoragePermissionDialog)
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
