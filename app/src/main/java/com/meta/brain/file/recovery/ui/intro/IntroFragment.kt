package com.meta.brain.file.recovery.ui.intro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.FragmentIntroBinding
import com.meta.brain.module.ads.AdEvent
import com.meta.brain.module.ads.AdsController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IntroFragment : Fragment() {

    private var _binding: FragmentIntroBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: IntroViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener {
            viewModel.onStartButtonClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.showAdEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                showInterstitialAd()
            }
        }

        viewModel.startTouchCount.observe(viewLifecycleOwner) { count ->
            Log.d("IntroFragment", "Start button touched $count time(s)")
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
        findNavController().navigate(R.id.action_intro_to_home)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
