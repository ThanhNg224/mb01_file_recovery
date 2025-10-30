package com.meta.brain.file.recovery.ui.settings

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.DialogFeedbackThanksBinding
import com.meta.brain.file.recovery.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

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
        observeViewModel()
        setupClickListeners()
    }

    private fun observeViewModel() {
        // Observe UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                handleUiState(state)
            }
        }

        // Observe one-time events
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collect { event ->
                handleUiEvent(event)
            }
        }
    }

    private fun handleUiState(state: SettingsUiState) {
        // Setup version text
        binding.tvVersion.text = getString(R.string.setting_version, state.appVersion)

        // Setup dropdowns with current state
        setupLanguageDropdown(state)
        setupThemeDropdown(state)
        setupDateFormatDropdown(state)

        // Update reminder switch
        binding.switchReminder.isChecked = state.isReminderEnabled
    }

    private fun handleUiEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ApplyTheme -> {
                applyTheme(event.themeId)
            }
            is SettingsUiEvent.RecreateActivity -> {
                requireActivity().recreate()
            }
            is SettingsUiEvent.NavigateToFeedback -> {
                findNavController().navigate(R.id.action_setting_to_feedback)
            }
            is SettingsUiEvent.ShowRatingDialog -> {
                showThankYouDialog()
            }
            is SettingsUiEvent.OpenPlayStore -> {
                openGooglePlayStore(event.packageName)
            }
            is SettingsUiEvent.OpenPrivacyPolicy -> {
                // TODO: Implement privacy policy
            }
            is SettingsUiEvent.NavigateBack -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            is SettingsUiEvent.ShowToast -> {
                android.widget.Toast.makeText(requireContext(), event.message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyTheme(themeId: String) {
        when (themeId) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "auto" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            viewModel.onBackPressed()
        }
    }

    private fun setupLanguageDropdown(state: SettingsUiState) {
        // Create adapter with language display names
        val languageNames = state.availableLanguages.map { it.displayName }
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
        binding.languageDropdown.setText(state.currentLanguage.displayName, false)

        // Handle language selection
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            if (isLanguageChanging) return@setOnItemClickListener

            val selectedLanguage = state.availableLanguages[position]
            if (selectedLanguage.code != state.currentLanguage.code) {
                isLanguageChanging = true
                viewModel.onLanguageSelected(selectedLanguage)
            }
        }
    }

    private fun setupThemeDropdown(state: SettingsUiState) {
        // Create adapter with theme display names
        val themeNames = state.availableThemes.map { it.displayName }
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
        binding.themeDropdown.setText(state.currentTheme.displayName, false)

        // Handle theme selection
        binding.themeDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedTheme = state.availableThemes[position]
            viewModel.onThemeSelected(selectedTheme)
            // Update display
            binding.themeDropdown.setText(selectedTheme.displayName, false)
        }
    }

    private fun setupDateFormatDropdown(state: SettingsUiState) {
        // Create adapter with date format display names
        val formatNames = state.availableDateFormats.map { it.displayName }
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
        binding.dateFormatDropdown.setText(state.currentDateFormat.displayName, false)

        // Handle date format selection
        binding.dateFormatDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedFormat = state.availableDateFormats[position]
            viewModel.onDateFormatSelected(selectedFormat)
            // Update display
            binding.dateFormatDropdown.setText(selectedFormat.displayName, false)
        }
    }

    private fun setupClickListeners() {
        // Reminder
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onReminderToggled(isChecked)
        }

        // Feedback
        binding.cardFeedback.setOnClickListener {
            viewModel.onFeedbackClicked()
        }

        // Rate Us
        binding.cardRateUs.setOnClickListener {
            viewModel.onRateUsClicked()
        }

        // Privacy Policy
        binding.cardPrivacyPolicy.setOnClickListener {
            viewModel.onPrivacyPolicyClicked()
        }
    }

    private fun showThankYouDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val dialogBinding = DialogFeedbackThanksBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set up star rating
        val stars = listOf(
            dialogBinding.star1,
            dialogBinding.star2,
            dialogBinding.star3,
            dialogBinding.star4,
            dialogBinding.star5
        )

        var selectedRating: Int

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedRating = index + 1
                updateStarRating(stars, selectedRating)
                viewModel.onRatingSubmitted(selectedRating)
            }
        }

        // Rate on Google button
        dialogBinding.btnRateOnGoogle.setOnClickListener {
            viewModel.onRateOnGooglePlayClicked()
            dialog.dismiss()
        }

        // No thanks button
        dialogBinding.btnNoThanks.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setCancelable(true)
        dialog.show()

        // Set dialog width to match parent with margin
        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateStarRating(stars: List<ImageView>, rating: Int) {
        stars.forEachIndexed { index, star ->
            if (index < rating) {
                star.setImageResource(R.drawable.ic_star_filled)
            } else {
                star.setImageResource(R.drawable.ic_star_outline)
            }
        }
    }

    private fun openGooglePlayStore(packageName: String) {
        try {
            // Try to open Play Store app
            val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // If Play Store app is not available, open in browser
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri()
            )
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
