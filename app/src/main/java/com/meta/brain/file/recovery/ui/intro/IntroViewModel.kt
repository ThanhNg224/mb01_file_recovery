package com.meta.brain.file.recovery.ui.intro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.repository.MetricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val metricsRepository: MetricsRepository
) : ViewModel() {

    private val _showAdEvent = MutableLiveData<Event<Unit>>()
    val showAdEvent: LiveData<Event<Unit>> = _showAdEvent

    private val _startTouchCount = MutableLiveData<Int>()
    val startTouchCount: LiveData<Int> = _startTouchCount

    private val _navigateToHome = MutableLiveData<Event<Unit>>()
    val navigateToHome: LiveData<Event<Unit>> = _navigateToHome

    init {
        // Observe the start touches flow
        viewModelScope.launch {
            metricsRepository.startTouchesFlow.collect { count ->
                _startTouchCount.value = count
            }
        }
    }

    fun onStartButtonClicked() {
        _showAdEvent.value = Event(Unit)
    }

    fun onAdCompleted() {
        viewModelScope.launch {
            val newCount = metricsRepository.incrementStartTouches()
            _navigateToHome.value = Event(Unit)
        }
    }
}

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 */
class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}
