package com.meta.brain.file.recovery.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.DateFormat
import com.meta.brain.file.recovery.data.model.Language
import com.meta.brain.file.recovery.data.model.Theme
import com.meta.brain.file.recovery.databinding.FragmentSettingsBinding
import com.meta.brain.module.data.DataManager
import com.meta.brain.module.utils.Utility
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_DATE_FORMAT = "date_format"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val availableLanguages = Language.getAvailableLanguages()
    private val availableThemes = Theme.getAvailableThemes()
    private val availableDateFormats = DateFormat.getAvailableDateFormats()

    private var isLanguageChanging = false

    private lateinit var languageAdapter: ArrayAdapter<String>
    private lateinit var themeAdapter: ArrayAdapter<String>
    private lateinit var dateFormatAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupVersionText()
        setupLanguageDropdown()
        setupThemeDropdown()
        setupDateFormatDropdown()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupVersionText() {
        val versionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        binding.tvVersion.text = getString(R.string.setting_version, versionName)
    }

    private fun setupLanguageDropdown() {
        // Get current language from DataManager
        val currentLanguageCode = DataManager.user.language ?: "en"
        val currentLanguage = Language.getLanguageByCode(currentLanguageCode)

        // Create adapter with language display names
        val languageNames = availableLanguages.map { it.displayName }
        languageAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            languageNames
        )

        // Configure dropdown behavior - set high threshold to prevent filtering
        binding.languageDropdown.threshold = Int.MAX_VALUE

        // Set the adapter
        binding.languageDropdown.setAdapter(languageAdapter)

        // Set current language as default
        binding.languageDropdown.setText(currentLanguage.displayName, false)

        // Handle language selection
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            if (isLanguageChanging) return@setOnItemClickListener

            val selectedLanguage = availableLanguages[position]
            if (selectedLanguage.code != currentLanguageCode) {
                isLanguageChanging = true
                onLanguageSelected(selectedLanguage)
            }
        }
    }

    private fun setupThemeDropdown() {
        // Get current theme from SharedPreferences
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentThemeId = prefs.getString(KEY_THEME, "auto") ?: "auto"
        val currentTheme = Theme.getThemeById(currentThemeId)

        // Create adapter with theme display names
        val themeNames = availableThemes.map { it.displayName }
        themeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            themeNames
        )

        // Configure dropdown behavior - set high threshold to prevent filtering
        binding.themeDropdown.threshold = Int.MAX_VALUE

        // Set the adapter
        binding.themeDropdown.setAdapter(themeAdapter)

        // Set current theme as default
        binding.themeDropdown.setText(currentTheme.displayName, false)

        // Handle theme selection
        binding.themeDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedTheme = availableThemes[position]
            onThemeSelected(selectedTheme)
            // Update display
            binding.themeDropdown.setText(selectedTheme.displayName, false)
        }
    }

    private fun setupDateFormatDropdown() {
        // Get current date format from SharedPreferences
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentFormatId = prefs.getString(KEY_DATE_FORMAT, DateFormat.DEFAULT_FORMAT_ID) ?: DateFormat.DEFAULT_FORMAT_ID
        val currentFormat = DateFormat.getDateFormatById(currentFormatId)

        // Create adapter with date format display names
        val formatNames = availableDateFormats.map { it.displayName }
        dateFormatAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            formatNames
        )

        // Configure dropdown behavior - set high threshold to prevent filtering
        binding.dateFormatDropdown.threshold = Int.MAX_VALUE

        // Set the adapter
        binding.dateFormatDropdown.setAdapter(dateFormatAdapter)

        // Set current format as default
        binding.dateFormatDropdown.setText(currentFormat.displayName, false)

        // Handle date format selection
        binding.dateFormatDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedFormat = availableDateFormats[position]
            onDateFormatSelected(selectedFormat)
            // Update display
            binding.dateFormatDropdown.setText(selectedFormat.displayName, false)
        }
    }

    private fun setupClickListeners() {
        // Reminder
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            onReminderToggled(isChecked)
        }


        // Feedback
        binding.cardFeedback.setOnClickListener {
            onFeedbackClicked()
        }

        // Rate Us
        binding.cardRateUs.setOnClickListener {
            onRateUsClicked()
        }

        // Privacy Policy
        binding.cardPrivacyPolicy.setOnClickListener {
            onPrivacyPolicyClicked()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onReminderToggled(isChecked: Boolean) {
        // TODO: Implement reminder toggle logic
    }

    private fun onLanguageSelected(language: Language) {
        // Update DataManager with new language code
        DataManager.user.language = language.code

        // Save to persistent storage
        DataManager.saveData(requireContext())

        // Apply locale change using Utility from MetaBrain module
        Utility.setLocale(requireContext())

        // Recreate activity to apply language change
        requireActivity().recreate()
    }

    private fun onThemeSelected(theme: Theme) {
        // Save theme to SharedPreferences
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.id).apply()

        // Apply theme change
        when (theme.id) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "auto" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun onDateFormatSelected(dateFormat: DateFormat) {
        // Save date format to SharedPreferences
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DATE_FORMAT, dateFormat.id).apply()

        // Date format change doesn't require activity recreation
        // The change will be applied when dates are formatted in the app
    }

    private fun onFeedbackClicked() {
        // TODO: Implement feedback functionality
    }

    private fun onRateUsClicked() {
        // TODO: Implement rate us functionality
    }

    private fun onPrivacyPolicyClicked() {
        // TODO: Implement privacy policy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

