package com.meta.brain.file.recovery.ui.scanloading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.ScanConfig
import com.meta.brain.file.recovery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * UI State for scan loading
 */
sealed class ScanLoadingState {
    data class Progress(
        val percent: Int,
        val hint: String? = null
    ) : ScanLoadingState()

    data class Completed(val foundCount: Int) : ScanLoadingState()
    data class Error(val message: String) : ScanLoadingState()
}

@HiltViewModel
class ScanLoadingViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanLoadingState>(ScanLoadingState.Progress(0))
    val uiState: StateFlow<ScanLoadingState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var progressJob: Job? = null

    private var actualProgress = 0
    private var fakeProgress = 0
    private var scanComplete = false
    private var resultCount = 0
    private var minDurationElapsed = false

    /**
     * Start scan with the given configuration
     */
    fun startScan(config: ScanConfig) {
        if (scanJob?.isActive == true) return

        // Reset state
        actualProgress = 0
        fakeProgress = 0
        scanComplete = false
        resultCount = 0
        minDurationElapsed = false

        // Start fake progress animation
        startFakeProgress(config.minDurationMs)

        // Start actual scan
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val types = config.toMediaTypes()

                // Always use deep scan
                val result = mediaRepository.deepScan(
                    types = types,
                    minSize = config.minSize,
                    fromSec = config.fromSec,
                    toSec = config.toSec,
                    pageSize = 300,
                    cursor = null
                )

                resultCount = result.items.size
                scanComplete = true

                // Animate to 100%
                animateToCompletion()

            } catch (e: Exception) {
                progressJob?.cancel()
                _uiState.value = ScanLoadingState.Error(
                    e.message ?: "Unknown error occurred during scan"
                )
            }
        }
    }

    /**
     * Start fake progress that ramps 1% -> 100% over minDurationMs
     */
    private fun startFakeProgress(minDurationMs: Long) {
        progressJob = viewModelScope.launch {
            val updateIntervalMs = 100L
            val totalSteps = (minDurationMs / updateIntervalMs).toInt()
            val incrementPerStep = 99.0 / totalSteps // 1% to 100% = 99 steps

            // Emit initial progress immediately
            fakeProgress = 1
            _uiState.value = ScanLoadingState.Progress(
                percent = 1,
                hint = getProgressHint(1)
            )

            var currentStep = 0
            while (currentStep < totalSteps) {
                delay(updateIntervalMs)
                currentStep++
                // Progress from 1% to 100%
                fakeProgress = (1 + (currentStep * incrementPerStep)).toInt().coerceIn(1, 100)

                // Use max of fake and actual progress
                val displayProgress = max(fakeProgress, actualProgress)
                _uiState.value = ScanLoadingState.Progress(
                    percent = displayProgress,
                    hint = getProgressHint(displayProgress)
                )

                // If we've reached 100%, mark minimum duration as elapsed
                if (fakeProgress >= 100) {
                    minDurationElapsed = true
                    break
                }
            }

            // Mark duration as elapsed and check if scan is complete
            minDurationElapsed = true
            if (scanComplete) {
                finishScan()
            }
        }
    }

    /**
     * Animate from current progress to 100% quickly
     */
    private suspend fun animateToCompletion() {
        // Don't cancel the progress job, let it continue to 100%
        // The progress animation will call finishScan() when it reaches 100%

        // If minimum duration has already elapsed, finish immediately
        if (minDurationElapsed) {
            finishScan()
        }
        // Otherwise, just wait - the progressJob will finish when it reaches 100%
    }

    /**
     * Finish the scan and emit completed state
     */
    private suspend fun finishScan() {
        // Ensure we're at 100%
        _uiState.value = ScanLoadingState.Progress(
            percent = 100,
            hint = "Finalizing..."
        )

        // Brief pause at 100%
        delay(200)

        // Emit completed state
        _uiState.value = ScanLoadingState.Completed(resultCount)
    }

    /**
     * Get progress hint based on current percent
     */
    private fun getProgressHint(percent: Int): String? {
        return when {
            percent < 20 -> "Initializing scan..."
            percent < 40 -> "Scanning media files..."
            percent < 60 -> "Analyzing file system..."
            percent < 80 -> "Processing results..."
            percent < 95 -> "Almost done..."
            else -> "Finalizing..."
        }
    }

    /**
     * Retry scan with same config
     */
    fun retry(config: ScanConfig) {
        startScan(config)
    }

    /**
     * Cancel ongoing scan
     */
    fun cancelScan() {
        scanJob?.cancel()
        progressJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cancelScan()
    }
}
