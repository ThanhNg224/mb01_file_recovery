package com.meta.brain.file.recovery.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Feedback screen
 */
data class FeedbackUiState(
    val selectedTags: Set<String> = emptySet(),
    val feedbackText: String = "",
    val isValid: Boolean = false,
    val errorMessage: String? = null
)

/**
 * One-time UI events for Feedback screen
 */
sealed class FeedbackUiEvent {
    object ShowThankYouDialog : FeedbackUiEvent()
    data class ShowError(val message: String) : FeedbackUiEvent()
    object NavigateBack : FeedbackUiEvent()
}

/**
 * ViewModel for Feedback screen
 * Handles feedback collection, validation, and submission
 */
@HiltViewModel
class FeedbackViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<FeedbackUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    /**
     * Toggle tag selection
     */
    fun toggleTag(tag: String, isSelected: Boolean) {
        val currentTags = _uiState.value.selectedTags.toMutableSet()
        if (isSelected) {
            currentTags.add(tag)
        } else {
            currentTags.remove(tag)
        }

        _uiState.value = _uiState.value.copy(
            selectedTags = currentTags,
            errorMessage = null
        )
        validateFeedback()
    }

    /**
     * Update feedback text
     */
    fun updateFeedbackText(text: String) {
        _uiState.value = _uiState.value.copy(
            feedbackText = text,
            errorMessage = null
        )
        validateFeedback()
    }

    /**
     * Validate feedback form
     */
    private fun validateFeedback() {
        val state = _uiState.value
        val isValid = state.selectedTags.isNotEmpty() && state.feedbackText.trim().length >= 8

        _uiState.value = state.copy(isValid = isValid)
    }

    /**
     * Send feedback
     */
    fun sendFeedback() {
        val state = _uiState.value

        // Validate tags
        if (state.selectedTags.isEmpty()) {
            viewModelScope.launch {
                _uiEvent.send(FeedbackUiEvent.ShowError("Please select at least one tag"))
            }
            return
        }

        // Validate text length
        if (state.feedbackText.trim().length < 8) {
            _uiState.value = state.copy(
                errorMessage = "Please enter at least 8 characters"
            )
            return
        }

        // In a real app, you would send this to your backend server
        // For now, we'll just show the thank you dialog
        viewModelScope.launch {
            try {
                // TODO: Send feedback to backend
                // val feedback = Feedback(
                //     tags = state.selectedTags.toList(),
                //     message = state.feedbackText,
                //     timestamp = System.currentTimeMillis()
                // )
                // feedbackRepository.submitFeedback(feedback)

                _uiEvent.send(FeedbackUiEvent.ShowThankYouDialog)
            } catch (e: Exception) {
                _uiEvent.send(FeedbackUiEvent.ShowError(e.message ?: "Failed to send feedback"))
            }
        }
    }

    /**
     * Handle back navigation
     */
    fun onBackPressed() {
        viewModelScope.launch {
            _uiEvent.send(FeedbackUiEvent.NavigateBack)
        }
    }

    /**
     * Handle rating submission (from thank you dialog)
     */
    fun onRatingSubmitted(rating: Int) {
        // In a real app, you would send the rating to your backend
        // For now, we'll just log it
        android.util.Log.d("FeedbackViewModel", "User rating: $rating stars")
    }
}

