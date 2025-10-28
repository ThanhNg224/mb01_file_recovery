package com.meta.brain.file.recovery.ui.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.DialogAppPopupBinding

/**
 * Reusable custom popup dialog component with modern Material 3 design.
 * Features blue accent tone, rounded corners, glass effect, and smooth animations.
 *
 * Usage example:
 * ```
 * AppDialogFragment.newInstance(
 *     DialogConfig(
 *         title = "Attention",
 *         message = "Exiting will lose results. Do you want to exit?",
 *         positiveText = "Stay",
 *         negativeText = "Cancel",
 *         onPositive = { /* action */ }
 *     )
 * ).show(childFragmentManager, "exit_warning")
 * ```
 */
class AppDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CONFIG = "config"

        fun newInstance(config: DialogConfig): AppDialogFragment {
            return AppDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_CONFIG, config)
                }
            }
        }
    }

    private var _binding: DialogAppPopupBinding? = null
    private val binding get() = _binding!!

    private var config: DialogConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppDialogTheme)
        config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_CONFIG, DialogConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_CONFIG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAppPopupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set transparent background for rounded corners
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        setupDialog()
    }

    private fun setupDialog() {
        config?.let { cfg ->
            // Setup icon
            if (cfg.iconRes != null) {
                binding.ivDialogIcon.setImageResource(cfg.iconRes)
                binding.ivDialogIcon.visibility = View.VISIBLE
            } else {
                binding.ivDialogIcon.visibility = View.GONE
            }

            // Setup title
            if (!cfg.title.isNullOrEmpty()) {
                binding.tvDialogTitle.text = cfg.title
                binding.tvDialogTitle.visibility = View.VISIBLE
            } else {
                binding.tvDialogTitle.visibility = View.GONE
            }

            // Setup message with optional highlight
            if (!cfg.message.isNullOrEmpty()) {
                binding.tvDialogMessage.text = if (!cfg.highlightText.isNullOrEmpty()) {
                    createHighlightedText(cfg.message, cfg.highlightText)
                } else {
                    cfg.message
                }
                binding.tvDialogMessage.visibility = View.VISIBLE
            } else {
                binding.tvDialogMessage.visibility = View.GONE
            }

            // Setup positive button
            binding.btnPositive.text = cfg.positiveText
            binding.btnPositive.setOnClickListener {
                cfg.onPositive?.invoke()
                dismiss()
            }

            // Setup negative button
            if (!cfg.negativeText.isNullOrEmpty()) {
                binding.btnNegative.text = cfg.negativeText
                binding.btnNegative.visibility = View.VISIBLE
                binding.btnNegative.setOnClickListener {
                    cfg.onNegative?.invoke()
                    dismiss()
                }
            } else {
                binding.btnNegative.visibility = View.GONE
            }

            // If only one button, make it full width
            if (cfg.negativeText.isNullOrEmpty()) {
                val params = binding.btnPositive.layoutParams as ViewGroup.MarginLayoutParams
                params.marginStart = 0
                binding.btnPositive.layoutParams = params
            }
        }
    }

    /**
     * Creates a spannable string with highlighted text in blue color
     */
    private fun createHighlightedText(fullText: String, highlightText: String): SpannableString {
        val spannable = SpannableString(fullText)
        val startIndex = fullText.indexOf(highlightText)

        if (startIndex >= 0) {
            val highlightColor = ContextCompat.getColor(requireContext(), R.color.dialog_highlight_blue)
            spannable.setSpan(
                ForegroundColorSpan(highlightColor),
                startIndex,
                startIndex + highlightText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    override fun onStart() {
        super.onStart()
        // Set dialog width
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

