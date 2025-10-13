package com.meta.brain.file.recovery.ui.scanloading

import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.AnimationDrawable
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

        // Start scan if not already started
        if (savedInstanceState == null) {
            viewModel.startScan(args.scanConfig)
        }
    }

    private fun setupToolbar() {
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
            viewModel.retry(args.scanConfig)
        }
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

        // Start the rotation animation
        startScanningAnimation()
    }

    private fun startScanningAnimation() {
        // Start the animated drawable rotation
        binding.animationView.drawable?.let { drawable ->
            when (drawable) {
                is AnimationDrawable -> drawable.start()
                is AnimatedVectorDrawable -> drawable.start()
            }
        }
    }

    private fun handleScanCompleted(foundCount: Int) {
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

        val targetName = args.scanConfig.getTargetDisplayName()
        binding.tvEmptyMessage.text = "Completed! No $targetName found on your device!"
        binding.toolbar.title = "Scan Complete"
    }

    private fun showErrorState(message: String) {
        binding.scanningContainer.visibility = View.GONE
        binding.emptyResultContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE

        binding.tvErrorMessage.text = message
        binding.toolbar.title = "Scan Failed"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
