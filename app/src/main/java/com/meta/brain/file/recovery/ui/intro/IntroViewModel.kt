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

    private val _navigateToOnboarding = MutableLiveData<Event<Unit>>()
    val navigateToOnboarding: LiveData<Event<Unit>> = _navigateToOnboarding

    fun onStartButtonClicked() {
        // TODO: Add analytics event: "Intro_Started"
        _navigateToOnboarding.value = Event(Unit)
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
