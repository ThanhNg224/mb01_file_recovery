package com.meta.brain.file.recovery.ui.feedback

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.DialogFeedbackThanksBinding
import com.meta.brain.file.recovery.databinding.FragmentFeedbackBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val selectedTags = mutableSetOf<String>()
    private var feedbackText = ""

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
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
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
                if (isChecked) {
                    selectedTags.add(tag)
                } else {
                    selectedTags.remove(tag)
                }
            }
        }
    }

    private fun setupFeedbackInput() {
        binding.etFeedback.doAfterTextChanged { text ->
            feedbackText = text?.toString() ?: ""
        }
    }

    private fun setupSendButton() {
        binding.btnSendFeedback.setOnClickListener {
            if (validateFeedback()) {
                sendFeedback()
            }
        }
    }

    private fun validateFeedback(): Boolean {
        // Check if at least one tag is selected
        if (selectedTags.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.feedback_error_select_tag),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        // Check if feedback text has at least 8 characters
        if (feedbackText.trim().length < 8) {
            binding.tilFeedback.error = getString(R.string.feedback_error_min_length)
            return false
        }

        binding.tilFeedback.error = null
        return true
    }

    private fun sendFeedback() {
        // In a real app, you would send this to your backend server
        // For now, we'll just show the thank you dialog

        // TODO: Send feedback to backend
        // val feedback = Feedback(
        //     tags = selectedTags.toList(),
        //     message = feedbackText,
        //     timestamp = System.currentTimeMillis()
        // )

        showThankYouDialog()
    }

    private fun showThankYouDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

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

        var selectedRating = 0

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedRating = index + 1
                updateStarRating(stars, selectedRating)
            }
        }

        // Rate on Google button
        dialogBinding.btnRateOnGoogle.setOnClickListener {
            openGooglePlayStore()
            dialog.dismiss()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // No thanks button
        dialogBinding.btnNoThanks.setOnClickListener {
            dialog.dismiss()
            requireActivity().onBackPressedDispatcher.onBackPressed()
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // If Play Store app is not available, open in browser
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

