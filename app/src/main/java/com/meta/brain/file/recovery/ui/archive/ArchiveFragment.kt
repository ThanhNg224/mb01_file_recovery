package com.meta.brain.file.recovery.ui.archive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.FragmentArchiveBinding
import com.meta.brain.file.recovery.ui.home.adapter.MediaAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ArchiveFragment : Fragment() {

    private var _binding: FragmentArchiveBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ArchiveViewModel by viewModels()

    @Inject
    lateinit var mediaRepository: MediaRepository

    private lateinit var mediaAdapter: MediaAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(
            mediaRepository = mediaRepository,
            onItemClick = { item ->
                if (viewModel.isSelectionMode.value) {
                    viewModel.toggleItemSelection(item)
                } else {
                    // TODO: Open file viewer or share
                    Snackbar.make(binding.root, "Open: ${item.displayName}", Snackbar.LENGTH_SHORT).show()
                }
            },
            onItemLongClick = { item ->
                viewModel.enterSelectionMode(item)
                true
            }
        )

        binding.rvRestoredFiles.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = mediaAdapter
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.filterByKind(null)
        }

        binding.chipImages.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.filterByKind(MediaKind.IMAGE)
        }

        binding.chipVideos.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.filterByKind(MediaKind.VIDEO)
        }

        binding.chipAudio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.filterByKind(MediaKind.AUDIO)
        }

        binding.chipDocuments.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.filterByKind(MediaKind.DOCUMENT)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUiState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedItems.collect { selectedItems ->
                mediaAdapter.updateSelection(selectedItems)
                updateToolbarTitle(selectedItems.size)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSelectionMode.collect { isSelectionMode ->
                mediaAdapter.setSelectionMode(isSelectionMode)
            }
        }
    }

    private fun updateUiState(state: ArchiveUiState) {
        when (state) {
            is ArchiveUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvRestoredFiles.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.GONE
                binding.errorStateLayout.visibility = View.GONE
            }
            is ArchiveUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.rvRestoredFiles.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
                binding.errorStateLayout.visibility = View.GONE

                mediaAdapter.submitList(state.filteredFiles)

                // Update chip counts
                updateChipCounts(state.files)
            }
            is ArchiveUiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.rvRestoredFiles.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.errorStateLayout.visibility = View.GONE
            }
            is ArchiveUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.rvRestoredFiles.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.GONE
                binding.errorStateLayout.visibility = View.VISIBLE

                binding.tvErrorMessage.text = state.message
                binding.btnRetry.setOnClickListener {
                    viewModel.loadRestoredFiles()
                }
            }
        }
    }

    private fun updateChipCounts(files: List<MediaEntry>) {
        val stats = files.groupBy { it.mediaKind }.mapValues { it.value.size }

        binding.chipAll.text = "All (${files.size})"
        binding.chipImages.text = "Images (${stats[MediaKind.IMAGE] ?: 0})"
        binding.chipVideos.text = "Videos (${stats[MediaKind.VIDEO] ?: 0})"
        binding.chipAudio.text = "Audio (${stats[MediaKind.AUDIO] ?: 0})"
        binding.chipDocuments.text = "Documents (${stats[MediaKind.DOCUMENT] ?: 0})"
    }

    private fun updateToolbarTitle(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.toolbar.title = "$selectedCount selected"
        } else {
            binding.toolbar.title = "Restored Files"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
