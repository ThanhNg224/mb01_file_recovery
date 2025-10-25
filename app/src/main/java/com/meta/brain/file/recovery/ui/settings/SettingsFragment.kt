package com.meta.brain.file.recovery.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.Language
import com.meta.brain.file.recovery.databinding.FragmentSettingsBinding
import com.meta.brain.module.data.DataManager
import com.meta.brain.module.utils.Utility
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val availableLanguages = Language.getAvailableLanguages()
    private var isLanguageChanging = false

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
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            languageNames
        )

        binding.languageDropdown.setAdapter(adapter)

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

    private fun setupClickListeners() {
        // Theme
        binding.cardTheme.setOnClickListener {
            onThemeClicked()
        }

        // Reminder
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            onReminderToggled(isChecked)
        }

        // Date Format
        binding.cardDateFormat.setOnClickListener {
            onDateFormatClicked()
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

    private fun onThemeClicked() {
        // TODO: Implement theme selection dialog
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

    private fun onDateFormatClicked() {
        // TODO: Implement date format selection dialog
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

