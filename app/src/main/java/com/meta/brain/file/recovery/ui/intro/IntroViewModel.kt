package com.meta.brain.file.recovery.ui.intro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.meta.brain.file.recovery.data.repository.MetricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val metricsRepository: MetricsRepository
) : ViewModel() {

    private val _showAdEvent = MutableLiveData<Event<Unit>>()
    val showAdEvent: LiveData<Event<Unit>> = _showAdEvent

    private val _navigateToHome = MutableLiveData<Event<Unit>>()
    val navigateToHome: LiveData<Event<Unit>> = _navigateToHome

    fun onStartButtonClicked() {
        _showAdEvent.value = Event(Unit)
    }

    fun onAdCompleted() {
        _navigateToHome.value = Event(Unit)
    }
}


class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set


    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }


    fun peekContent(): T = content
}
