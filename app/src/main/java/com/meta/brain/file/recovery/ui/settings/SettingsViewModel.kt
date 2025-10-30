package com.meta.brain.file.recovery.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.brain.file.recovery.data.model.DateFormat
import com.meta.brain.file.recovery.data.model.Language
import com.meta.brain.file.recovery.data.model.Theme
import com.meta.brain.file.recovery.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Settings screen
 */
data class SettingsUiState(
    val currentTheme: Theme = Theme.getThemeById(Theme.AUTO),
    val currentLanguage: Language = Language.getLanguageByCode("en"),
    val currentDateFormat: DateFormat = DateFormat.getDateFormatById(DateFormat.DEFAULT_FORMAT_ID),
    val isReminderEnabled: Boolean = false,
    val appVersion: String = "1.0",
    val availableLanguages: List<Language> = Language.getAvailableLanguages(),
    val availableThemes: List<Theme> = Theme.getAvailableThemes(),
    val availableDateFormats: List<DateFormat> = DateFormat.getAvailableDateFormats()
)

/**
 * One-time UI events for Settings screen
 */
sealed class SettingsUiEvent {
    data class ApplyTheme(val themeId: String) : SettingsUiEvent()
    object RecreateActivity : SettingsUiEvent()
    object NavigateToFeedback : SettingsUiEvent()
    object ShowRatingDialog : SettingsUiEvent()
    data class OpenPlayStore(val packageName: String) : SettingsUiEvent()
    object OpenPrivacyPolicy : SettingsUiEvent()
    object NavigateBack : SettingsUiEvent()
    data class ShowToast(val message: String) : SettingsUiEvent()
}

/**
 * ViewModel for Settings screen
 * Manages app settings like theme, language, date format, reminders, etc.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<SettingsUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        loadSettings()
    }

    /**
     * Load current settings from repository
     */
    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            currentTheme = settingsRepository.getCurrentTheme(),
            currentLanguage = settingsRepository.getCurrentLanguage(),
            currentDateFormat = settingsRepository.getCurrentDateFormat(),
            isReminderEnabled = settingsRepository.isReminderEnabled(),
            appVersion = settingsRepository.getAppVersion()
        )
    }

    /**
     * Handle theme selection
     */
    fun onThemeSelected(theme: Theme) {
        viewModelScope.launch {
            // Save theme to repository
            settingsRepository.saveTheme(theme)

            // Update UI state
            _uiState.value = _uiState.value.copy(currentTheme = theme)

            // Trigger theme application
            _uiEvent.send(SettingsUiEvent.ApplyTheme(theme.id))
        }
    }

    /**
     * Handle language selection
     */
    fun onLanguageSelected(language: Language) {
        viewModelScope.launch {
            // Check if language actually changed
            if (language.code != _uiState.value.currentLanguage.code) {
                // Save language to repository
                settingsRepository.saveLanguage(language)

                // Update UI state
                _uiState.value = _uiState.value.copy(currentLanguage = language)

                // Trigger activity recreation to apply language change
                _uiEvent.send(SettingsUiEvent.RecreateActivity)
            }
        }
    }

    /**
     * Handle date format selection
     */
    fun onDateFormatSelected(dateFormat: DateFormat) {
        viewModelScope.launch {
            // Save date format to repository
            settingsRepository.saveDateFormat(dateFormat)

            // Update UI state
            _uiState.value = _uiState.value.copy(currentDateFormat = dateFormat)
        }
    }

    /**
     * Handle reminder toggle
     */
    fun onReminderToggled(enabled: Boolean) {
        viewModelScope.launch {
            // Save reminder state to repository
            settingsRepository.saveReminderEnabled(enabled)

            // Update UI state
            _uiState.value = _uiState.value.copy(isReminderEnabled = enabled)
        }
    }

    /**
     * Handle feedback button click
     */
    fun onFeedbackClicked() {
        viewModelScope.launch {
            _uiEvent.send(SettingsUiEvent.NavigateToFeedback)
        }
    }

    /**
     * Handle rate us button click
     */
    fun onRateUsClicked() {
        viewModelScope.launch {
            _uiEvent.send(SettingsUiEvent.ShowRatingDialog)
        }
    }

    /**
     * Handle rating submitted from dialog
     */
    fun onRatingSubmitted(rating: Int) {
        // In a real app, you would send the rating to your backend
        android.util.Log.d("SettingsViewModel", "User rating: $rating stars")
    }

    /**
     * Handle "Rate on Google Play" click
     */
    fun onRateOnGooglePlayClicked() {
        viewModelScope.launch {
            val packageName = settingsRepository.getPackageName()
            _uiEvent.send(SettingsUiEvent.OpenPlayStore(packageName))
        }
    }

    /**
     * Handle privacy policy button click
     */
    fun onPrivacyPolicyClicked() {
        viewModelScope.launch {
            // TODO: Open privacy policy when implemented
            _uiEvent.send(SettingsUiEvent.OpenPrivacyPolicy)
        }
    }

    /**
     * Handle back navigation
     */
    fun onBackPressed() {
        viewModelScope.launch {
            _uiEvent.send(SettingsUiEvent.NavigateBack)
        }
    }
}

