package com.meta.brain.file.recovery.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.FragmentOnboardingBinding
import com.meta.brain.module.ads.AdEvent
import com.meta.brain.module.ads.AdsController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels()

    private lateinit var pagerAdapter: OnboardingPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupViewPager() {
        pagerAdapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // Attach TabLayoutMediator and set custom dot views
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val dotView = LayoutInflater.from(requireContext()).inflate(R.layout.tab_dot, null)
            val dotImage = dotView.findViewById<ImageView>(R.id.tab_dot)
            dotImage.setImageResource(if (position == 0) R.drawable.tab_selected else R.drawable.tab_unselected)
            tab.customView = dotView
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonState(position)
                updateTabDots(binding.tabLayout, position)
            }
        })

        updateButtonState(0)
    }

    private fun updateTabDots(tabLayout: TabLayout, selectedIndex: Int) {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            val dot = tab?.customView?.findViewById<ImageView>(R.id.tab_dot)
            dot?.setImageResource(if (i == selectedIndex) R.drawable.tab_selected else R.drawable.tab_unselected)
        }
    }

    private fun updateButtonState(position: Int) {
        val isLastPage = position == pagerAdapter.itemCount - 1
        binding.btnNext.text = if (isLastPage) {
            getString(R.string.finish)
        } else {
            getString(R.string.next)
        }

        binding.btnSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE
    }

    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < pagerAdapter.itemCount - 1) {
                // Move to next page
                binding.viewPager.currentItem = currentItem + 1
            } else {
                // Last page - finish onboarding
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        // Prevent duplicate taps
        binding.btnNext.isEnabled = false
        binding.btnSkip.isEnabled = false

        viewModel.onOnboardingFinished()
    }

    private fun observeViewModel() {
        viewModel.showAdEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                showInterstitialAd()
            }
        }

        viewModel.navigateToHome.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                navigateToHome()
            }
        }
    }

    private fun showInterstitialAd() {
        requireActivity().let { activity ->
            AdsController.showInter(activity, object : AdEvent() {
                override fun onComplete() {
                    viewModel.onAdCompleted()
                }
            })
        }
    }

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_onboarding_to_home)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
