package com.meta.brain.file.recovery.ui.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.repository.MetricsRepository
import com.meta.brain.file.recovery.ui.intro.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val metricsRepository: MetricsRepository
) : ViewModel() {

    private val _showAdEvent = MutableLiveData<Event<Unit>>()
    val showAdEvent: LiveData<Event<Unit>> = _showAdEvent

    private val _navigateToHome = MutableLiveData<Event<Unit>>()
    val navigateToHome: LiveData<Event<Unit>> = _navigateToHome

    fun onOnboardingFinished() {
        viewModelScope.launch {
            // Persist onboarding completion
            metricsRepository.setOnboardingDone(true)

            // TODO: Add analytics event: "Onboarding_Finish"

            // Show interstitial ad
            _showAdEvent.value = Event(Unit)
        }
    }

    fun onAdCompleted() {
        // Navigate to home after ad is shown/closed
        _navigateToHome.value = Event(Unit)
    }
}

