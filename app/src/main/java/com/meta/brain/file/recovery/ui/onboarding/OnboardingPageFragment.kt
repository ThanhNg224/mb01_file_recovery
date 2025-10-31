package com.meta.brain.file.recovery.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.FragmentOnboardingPageBinding

class OnboardingPageFragment : Fragment() {

    private var _binding: FragmentOnboardingPageBinding? = null
    private val binding get() = _binding!!

    private var pagePosition: Int = 0

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POSITION, position)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pagePosition = arguments?.getInt(ARG_POSITION) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPageContent()
    }

    private fun setupPageContent() {
        when (pagePosition) {
            0 -> {
                // TODO: Replace with actual image resource
                binding.ivOnboardingImage.setImageResource(R.drawable.logo)
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_title_1)
                binding.tvOnboardingDescription.text = getString(R.string.onboarding_desc_1)
            }
            1 -> {
                // TODO: Replace with actual image resource
                binding.ivOnboardingImage.setImageResource(R.drawable.logo)
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_title_2)
                binding.tvOnboardingDescription.text = getString(R.string.onboarding_desc_2)
            }
            2 -> {
                // TODO: Replace with actual image resource
                binding.ivOnboardingImage.setImageResource(R.drawable.logo)
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_title_3)
                binding.tvOnboardingDescription.text = getString(R.string.onboarding_desc_3)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

