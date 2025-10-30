package com.meta.brain.file.recovery.ui.instruction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-time UI events for Recovery Instruction screen
 */
sealed class InstructionUiEvent {
    object NavigateBack : InstructionUiEvent()
}

/**
 * ViewModel for Recovery Instruction screen
 * Handles navigation and potential future instruction-related logic
 */
@HiltViewModel
class RecoveryInstructionViewModel @Inject constructor() : ViewModel() {

    private val _uiEvent = Channel<InstructionUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    /**
     * Handle back navigation
     */
    fun onBackPressed() {
        viewModelScope.launch {
            _uiEvent.send(InstructionUiEvent.NavigateBack)
        }
    }
}

