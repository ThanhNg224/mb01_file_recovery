package com.meta.brain.file.recovery.ui.scanloading

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.FragmentScanLoadingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanLoadingFragment : Fragment() {

    private var _binding: FragmentScanLoadingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanLoadingViewModel by viewModels()
    private val args: ScanLoadingFragmentArgs by navArgs()

    private var isScanning = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanLoadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupBackPressHandler()
        observeViewModel()
        setupClickListeners()
        setupAnimationColorFilter()

        // Start scan if not already started
        if (savedInstanceState == null) {
            viewModel.startScan(args.scanConfig)
        }
    }

    /**
     * Setup color filter for Lottie animation to make ripples more vivid
     */
    private fun setupAnimationColorFilter() {
        binding.scanAnimationView.addValueCallback(
            KeyPath("**"),
            LottieProperty.COLOR_FILTER
        ) {
            PorterDuffColorFilter(
                Color.parseColor("#0099FF"),
                PorterDuff.Mode.SRC_ATOP
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume animation if scanning is active
        if (isScanning && binding.scanningContainer.visibility == View.VISIBLE) {
            binding.scanAnimationView.resumeAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause animation to save resources
        binding.scanAnimationView.pauseAnimation()
    }

    private fun setupToolbar() {
        binding.toolbar.title = args.scanConfig.getScanningTitle()
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }
    }

    private fun handleBackPress() {
        if (isScanning) {
            showExitConfirmationDialog()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Exit Scan")
            .setMessage("If you exit, the scan results will be discarded. Are you sure?")
            .setPositiveButton("Exit") { _, _ ->
                viewModel.cancelScan()
                findNavController().navigateUp()
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnScanOther.setOnClickListener {
            // Navigate back to home
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        binding.btnRetry.setOnClickListener {
            isScanning = true
            resetScanningUI()
            viewModel.retry(args.scanConfig)
        }
    }

    /**
     * Reset the scanning UI to initial state
     */
    private fun resetScanningUI() {
        // Reset animation container visibility and alpha
        binding.scanningAnimationContainer.visibility = View.VISIBLE
        binding.scanningAnimationContainer.alpha = 1f

        // Hide done icon
        binding.ivDoneIcon.visibility = View.GONE
        binding.ivDoneIcon.alpha = 0f
        binding.ivDoneIcon.scaleX = 1f
        binding.ivDoneIcon.scaleY = 1f
    }

    private fun updateUI(state: ScanLoadingState) {
        when (state) {
            is ScanLoadingState.Progress -> {
                showScanningState(state)
            }

            is ScanLoadingState.Completed -> {
                isScanning = false
                handleScanCompleted(state.foundCount)
            }

            is ScanLoadingState.Error -> {
                isScanning = false
                showErrorState(state.message)
            }
        }
    }

    private fun showScanningState(state: ScanLoadingState.Progress) {
        binding.scanningContainer.visibility = View.VISIBLE
        binding.emptyResultContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE

        binding.tvPercent.text = "${state.percent}%"
        binding.tvHint.text = state.hint ?: ""
        binding.tvHint.visibility = if (state.hint != null) View.VISIBLE else View.GONE

        // Check if we've reached 100%
        if (state.percent >= 100) {
            // Transition to done icon
            transitionToDoneIcon()
        } else {
            // Ensure animation is playing and done icon is hidden
            bindLoadingState(LoadingAnimationState.SCANNING)
            binding.ivDoneIcon.visibility = View.GONE
            binding.scanningAnimationContainer.visibility = View.VISIBLE
        }
    }

    /**
     * Smoothly transition from scanning animation to done icon
     */
    private fun transitionToDoneIcon() {
        // Fade out the scanning animation container
        binding.scanningAnimationContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.scanningAnimationContainer.visibility = View.GONE
                binding.scanAnimationView.cancelAnimation()

                // Fade in the done icon
                binding.ivDoneIcon.visibility = View.VISIBLE
                binding.ivDoneIcon.alpha = 0f
                binding.ivDoneIcon.scaleX = 0.5f
                binding.ivDoneIcon.scaleY = 0.5f

                binding.ivDoneIcon.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .start()
            }
            .start()
    }

    /**
     * Manage Lottie animation state
     */
    private fun bindLoadingState(state: LoadingAnimationState) {
        when (state) {
            LoadingAnimationState.SCANNING -> {
                if (!binding.scanAnimationView.isAnimating) {
                    binding.scanAnimationView.playAnimation()
                }
            }
            LoadingAnimationState.COMPLETE -> {
                binding.scanAnimationView.cancelAnimation()
                binding.scanAnimationView.progress = 1f // Freeze on last frame
            }
            LoadingAnimationState.STOPPED -> {
                binding.scanAnimationView.cancelAnimation()
            }
        }
    }

    /**
     * Animation states for the Lottie animation
     */
    private enum class LoadingAnimationState {
        SCANNING,
        COMPLETE,
        STOPPED
    }

    private fun handleScanCompleted(foundCount: Int) {
        // Stop the animation gracefully
        bindLoadingState(LoadingAnimationState.COMPLETE)

        if (foundCount > 0) {
            // Navigate to results screen
            try {
                val action = ScanLoadingFragmentDirections.actionScanLoadingToResults(args.scanConfig)
                findNavController().navigate(action)
            } catch (e: Exception) {
                android.util.Log.e("ScanLoadingFragment", "Navigation error: ${e.message}")
            }
        } else {
            // Show empty results state
            showEmptyResultState()
        }
    }

    private fun showEmptyResultState() {
        binding.scanningContainer.visibility = View.GONE
        binding.emptyResultContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE

        // Stop the animation
        bindLoadingState(LoadingAnimationState.STOPPED)

        val targetName = args.scanConfig.getTargetDisplayName()
        binding.tvEmptyMessage.text = "Completed! No $targetName found on your device!"
        binding.toolbar.title = "Scan Complete"
    }

    private fun showErrorState(message: String) {
        binding.scanningContainer.visibility = View.GONE
        binding.emptyResultContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE

        // Stop the animation
        bindLoadingState(LoadingAnimationState.STOPPED)

        binding.tvErrorMessage.text = message
        binding.toolbar.title = "Scan Failed"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel animation and release resources
        binding.scanAnimationView.cancelAnimation()
        _binding = null
    }
}
