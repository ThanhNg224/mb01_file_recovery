package com.meta.brain.file.recovery.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.FragmentPreviewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Full-screen preview fragment for single media item
 * Supports image, video, and document preview with Restore/Delete actions
 */
@AndroidEntryPoint
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val args: PreviewFragmentArgs by navArgs()
    private val previewViewModel: PreviewViewModel by viewModels()

    private lateinit var currentEntry: MediaEntry

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the single media entry from args
        currentEntry = args.visibleItems[args.startIndex]

        setupToolbar()
        setupMediaDisplay()
        setupButtons()
        setupPhotoInfo()
        observeViewModel()

        // Handle back press
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        // Observe file operation state
        viewLifecycleOwner.lifecycleScope.launch {
            previewViewModel.fileOpState.collect { state ->
                handleFileOpState(state)
            }
        }

        // Observe UI events
        viewLifecycleOwner.lifecycleScope.launch {
            previewViewModel.uiEvent.collect { event ->
                handleUiEvent(event)
            }
        }
    }

    private fun handleFileOpState(state: PreviewFileOpState) {
        // Can be used to show loading indicators if needed
        when (state) {
            is PreviewFileOpState.Deleting -> {
                // Optional: Show deleting indicator
            }
            is PreviewFileOpState.Restoring -> {
                // Optional: Show restoring indicator
            }
            else -> {
                // State handled by events
            }
        }
    }

    private fun handleUiEvent(event: PreviewUiEvent) {
        when (event) {
            is PreviewUiEvent.ShowMessage -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
            }
            is PreviewUiEvent.NavigateBack -> {
                findNavController().navigateUp()
            }
            is PreviewUiEvent.NotifyFileChanged -> {
                // Set result for ArchiveFragment or ResultsFragment to refresh
                if (event.fromArchive) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("files_changed", true)
                } else {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("results_files_changed", true)
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Set filename as title
        binding.toolbar.title = currentEntry.displayName ?: "Unknown"

        // Handle share menu item
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                com.meta.brain.file.recovery.R.id.action_share -> {
                    shareFile(currentEntry)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMediaDisplay() {
        // Load the single media item using Glide
        Glide.with(this)
            .load(currentEntry.uri)
            .fitCenter()
            .into(binding.ivPreview)
    }

    private fun setupButtons() {
        // Hide restore button if opened from Archive
        if (args.fromArchive) {
            binding.btnRestore.isVisible = false
        }

        // Restore button click listener
        binding.btnRestore.setOnClickListener {
            restoreSingleFile(currentEntry)
        }

        // Delete button click listener
        binding.btnDelete.setOnClickListener {
            showDeleteDialog(currentEntry)
        }
    }

    private fun setupPhotoInfo() {
        // Date created
        binding.tvDateCreated.text = currentEntry.getFormattedDate()

        // Size
        binding.tvSize.text = currentEntry.getFormattedSize()

        // Link/Path
        binding.tvLink.text = currentEntry.filePath ?: "Unknown"

        // Resolution
        binding.tvResolution.text = currentEntry.getFormattedResolution() ?: "N/A"
    }

    private fun showDeleteDialog(entry: MediaEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete file?")
            .setMessage("This will permanently delete ${entry.displayName}. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                previewViewModel.deleteSingleFile(entry, args.fromArchive)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreSingleFile(entry: MediaEntry) {
        previewViewModel.restoreSingleFile(entry)
    }

    private fun shareFile(entry: MediaEntry) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = entry.mimeType ?: "*/*"
                putExtra(android.content.Intent.EXTRA_STREAM, entry.uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share via"))
        } catch (_: Exception) {
            Snackbar.make(binding.root, "Failed to share file", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
