package com.meta.brain.file.recovery.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaGroup
import com.meta.brain.file.recovery.databinding.FragmentResultsBinding
import com.meta.brain.file.recovery.ui.home.HomeViewModel
import com.meta.brain.file.recovery.ui.home.MediaUiState
import com.meta.brain.file.recovery.ui.results.adapter.FolderGroupAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: HomeViewModel by activityViewModels()
    private val args: ResultsFragmentArgs by navArgs()

    private lateinit var folderGroupAdapter: FolderGroupAdapter
    private var allMediaItems: List<MediaEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()

        // Fetch results if scanConfig is provided
        args.scanConfig.let { config ->
            val types = config.toMediaTypes()
            sharedViewModel.deepScan(
                types = types,
                minSize = config.minSize,
                fromSec = config.fromSec,
                toSec = config.toSec
            )
        }

        // Handle back button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPressed()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }
    }

    private fun handleBackPressed() {
        try {
            if (!findNavController().popBackStack()) {
                findNavController().navigateUp()
            }
        } catch (_: Exception) {
            requireActivity().finish()
        }
    }

    private fun setupRecyclerView() {
        folderGroupAdapter = FolderGroupAdapter { group ->
            navigateToGroupDetail(group)
        }

        binding.rvMediaGrid.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = folderGroupAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: MediaUiState) {
        when (state) {
            is MediaUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutErrorState.visibility = View.GONE
            }

            is MediaUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE
            }

            is MediaUiState.Items -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE

                allMediaItems = state.list
                groupItemsIntoFolders(state.list)
            }

            is MediaUiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutErrorState.visibility = View.GONE
            }

            is MediaUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.VISIBLE

                binding.tvErrorMessage.text = state.message

                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        findNavController().popBackStack()
                    }
                    .show()
            }
        }
    }

    private fun groupItemsIntoFolders(items: List<MediaEntry>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val groups = mutableListOf<MediaGroup>()

            // Separate trashed items from normal items
            val trashedItems = items.filter { it.isTrashed || it.displayName?.startsWith(".trashed-", ignoreCase = true) == true }
            val normalItems = items.filter { !it.isTrashed && it.displayName?.startsWith(".trashed-", ignoreCase = true) != true }

            // Group normal items by real physical folder path
            val folderMap = normalItems.groupBy { entry ->
                getPhysicalFolderPath(entry)
            }

            // Create MediaGroup for each normal folder
            folderMap.forEach { (folderPath, folderItems) ->
                val previewItems = folderItems.take(3)
                groups.add(
                    MediaGroup(
                        folderName = folderPath,
                        fileCount = folderItems.size,
                        previewItems = previewItems,
                        allItems = folderItems
                    )
                )
            }

            // Create virtual "Trash" folder for all trashed items
            if (trashedItems.isNotEmpty()) {
                val previewItems = trashedItems.take(3)
                groups.add(
                    MediaGroup(
                        folderName = "Trash",
                        fileCount = trashedItems.size,
                        previewItems = previewItems,
                        allItems = trashedItems
                    )
                )
            }

            // Sort groups by file count (descending)
            val sortedGroups = groups.sortedByDescending { it.fileCount }

            withContext(Dispatchers.Main) {
                folderGroupAdapter.submitList(sortedGroups)
                updateResultsInfo(items.size, sortedGroups.size)
            }
        }
    }

    /**
     * Extract the real physical folder path from MediaEntry.
     * Uses the filePath field if available, otherwise extracts from URI.
     */
    private fun getPhysicalFolderPath(entry: MediaEntry): String {
        // Use filePath if available
        val path = entry.filePath ?: entry.uri.path ?: return "Unknown"

        // Get the parent folder path
        val file = File(path)
        return file.parent ?: "Unknown"
    }

    private fun updateResultsInfo(totalFiles: Int, groupCount: Int) {
        binding.tvResultsCount.text = "Found $totalFiles files in $groupCount folders"
    }

    private fun navigateToGroupDetail(group: MediaGroup) {
        val bundle = Bundle().apply {
            putParcelable("mediaGroup", group)
        }
        findNavController().navigate(R.id.resultGroupDetailFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
