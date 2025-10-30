package com.meta.brain.file.recovery.ui.instruction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.meta.brain.file.recovery.databinding.FragmentRecoveryInstructionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment displaying recovery instructions and information
 */
@AndroidEntryPoint
class RecoveryInstructionFragment : Fragment() {

    private var _binding: FragmentRecoveryInstructionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecoveryInstructionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecoveryInstructionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        // Setup toolbar back navigation
        binding.toolbar.setNavigationOnClickListener {
            viewModel.onBackPressed()
        }
    }

    private fun observeViewModel() {
        // Observe one-time UI events
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collect { event ->
                handleUiEvent(event)
            }
        }
    }

    private fun handleUiEvent(event: InstructionUiEvent) {
        when (event) {
            is InstructionUiEvent.NavigateBack -> {
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

