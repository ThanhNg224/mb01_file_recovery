package com.meta.brain.file.recovery.ui.feedback

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.DialogFeedbackThanksBinding
import com.meta.brain.file.recovery.databinding.FragmentFeedbackBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri

@AndroidEntryPoint
class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedbackViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupChipGroup()
        setupFeedbackInput()
        setupSendButton()
        observeViewModel()
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

    private fun handleUiState(state: FeedbackUiState) {
        // Update error message if present
        binding.tilFeedback.error = state.errorMessage
    }

    private fun handleUiEvent(event: FeedbackUiEvent) {
        when (event) {
            is FeedbackUiEvent.ShowThankYouDialog -> {
                showThankYouDialog()
            }
            is FeedbackUiEvent.ShowError -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }
            is FeedbackUiEvent.NavigateBack -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            viewModel.onBackPressed()
        }
    }

    private fun setupChipGroup() {
        // Set up chip selection listeners
        val chipIds = listOf(
            R.id.chipGoodScan,
            R.id.chipGoodQuality,
            R.id.chipBugs,
            R.id.chipSuggestions,
            R.id.chipQuickRecovery,
            R.id.chipEasyInterface,
            R.id.chipOthers
        )

        chipIds.forEach { chipId ->
            val chip = binding.root.findViewById<Chip>(chipId)
            chip.setOnCheckedChangeListener { buttonView, isChecked ->
                val tag = buttonView.text.toString()
                viewModel.toggleTag(tag, isChecked)
            }
        }
    }

    private fun setupFeedbackInput() {
        binding.etFeedback.doAfterTextChanged { text ->
            viewModel.updateFeedbackText(text?.toString() ?: "")
        }
    }

    private fun setupSendButton() {
        binding.btnSendFeedback.setOnClickListener {
            viewModel.sendFeedback()
        }
    }

    private fun showThankYouDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val dialogBinding = DialogFeedbackThanksBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set up star rating
        val stars = listOf<ImageView>(
            dialogBinding.star1,
            dialogBinding.star2,
            dialogBinding.star3,
            dialogBinding.star4,
            dialogBinding.star5
        )

        var selectedRating = 0

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedRating = index + 1
                updateStarRating(stars, selectedRating)
                viewModel.onRatingSubmitted(selectedRating)
            }
        }

        // Rate on Google button
        dialogBinding.btnRateOnGoogle.setOnClickListener {
            openGooglePlayStore()
            dialog.dismiss()
            viewModel.onBackPressed()
        }

        // No thanks button
        dialogBinding.btnNoThanks.setOnClickListener {
            dialog.dismiss()
            viewModel.onBackPressed()
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

    private fun openGooglePlayStore() {
        val packageName = requireContext().packageName
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

