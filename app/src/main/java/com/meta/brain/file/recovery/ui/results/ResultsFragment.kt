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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.FragmentResultsBinding
import com.meta.brain.file.recovery.ui.home.HomeViewModel
import com.meta.brain.file.recovery.ui.home.MediaUiState
import com.meta.brain.file.recovery.ui.home.adapter.MediaAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    // Share the same ViewModel with HomeFragment to access scan results
    private val sharedViewModel: HomeViewModel by activityViewModels()

    @Inject
    lateinit var mediaRepository: MediaRepository

    private lateinit var mediaAdapter: MediaAdapter

    private var allMediaItems: List<MediaEntry> = emptyList()
    private var currentTabFilter: MediaKind? = null

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
        setupTabs()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        // Set up the toolbar with proper back navigation
        binding.toolbar.setNavigationOnClickListener {
            android.util.Log.d("ResultsFragment", "Back button clicked")
            try {
                if (!findNavController().popBackStack()) {
                    // If popBackStack returns false, try alternative navigation
                    android.util.Log.d("ResultsFragment", "PopBackStack failed, trying navigateUp")
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                android.util.Log.e("ResultsFragment", "Navigation error: ${e.message}")
                // Fallback: finish activity or handle manually
                requireActivity().onBackPressed()
            }
        }

        // Also handle system back button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            android.util.Log.d("ResultsFragment", "System back button pressed")
            try {
                if (!findNavController().popBackStack()) {
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                android.util.Log.e("ResultsFragment", "System back navigation error: ${e.message}")
                requireActivity().finish()
            }
        }
    }

    private fun setupTabs() {
        // Add tabs with Vietnamese labels and counts (will be updated when data loads)
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Ảnh (0)"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Video (0)"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("File khác (0)"))

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> filterByMediaKind(MediaKind.IMAGE)
                    1 -> filterByMediaKind(MediaKind.VIDEO)
                    2 -> filterByMediaKind(null) // File khác = DOCUMENT + OTHER
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(mediaRepository) { mediaEntry ->
            openMediaPreview(mediaEntry)
        }

        val spanCount = if (resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3

        binding.rvMediaGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), spanCount)
            adapter = mediaAdapter

            // Endless scroll listener for loading more results
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    val currentState = sharedViewModel.uiState.value
                    if (currentState is MediaUiState.Items &&
                        currentState.canLoadMore &&
                        !currentState.appending &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 10) {
                        sharedViewModel.loadMore()
                    }
                }
            })
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.uiState.collect { state ->
                android.util.Log.d("ResultsFragment", "Results UI State: $state")
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: MediaUiState) {
        android.util.Log.d("ResultsFragment", "Updating UI with state: ${state::class.simpleName}")

        when (state) {
            is MediaUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutErrorState.visibility = View.GONE
                updateResultsInfo(0, "No scan performed")
            }

            is MediaUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE
                updateResultsInfo(0, "Scanning...")
            }

            is MediaUiState.Items -> {
                android.util.Log.d("ResultsFragment", "Displaying ${state.list.size} items")
                binding.progressBar.visibility = View.GONE
                binding.progressBarBottom.visibility = if (state.appending) View.VISIBLE else View.GONE
                binding.rvMediaGrid.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE

                allMediaItems = state.list // Cache all items for filtering
                updateTabCounts() // Update tab counts first

                // Apply current tab filter or show first tab (Images) by default
                if (currentTabFilter != null) {
                    filterByMediaKind(currentTabFilter)
                } else {
                    // Default to first tab (Images) when items load
                    binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
                    filterByMediaKind(MediaKind.IMAGE)
                }

                updateResultsInfo(state.list.size, generateFilterInfo(state.list))
            }

            is MediaUiState.Empty -> {
                android.util.Log.d("ResultsFragment", "Showing empty state")
                binding.progressBar.visibility = View.GONE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutErrorState.visibility = View.GONE
                updateResultsInfo(0, "No results")
            }

            is MediaUiState.Error -> {
                android.util.Log.d("ResultsFragment", "Showing error: ${state.message}")
                binding.progressBar.visibility = View.GONE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.VISIBLE

                binding.tvErrorMessage.text = state.message
                updateResultsInfo(0, "Error")

                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        findNavController().popBackStack()
                    }
                    .show()
            }
        }
    }

    private fun filterByMediaKind(kind: MediaKind?) {
        currentTabFilter = kind
        val filtered = when (kind) {
            MediaKind.IMAGE ->
                allMediaItems.filter { it.mediaKind == MediaKind.IMAGE }
            MediaKind.VIDEO ->
                allMediaItems.filter { it.mediaKind == MediaKind.VIDEO }
            null -> // Other Files tab
                allMediaItems.filter {
                    it.mediaKind == MediaKind.DOCUMENT ||
                    it.mediaKind == MediaKind.OTHER
                }
            else -> emptyList()
        }
        mediaAdapter.submitList(filtered)
        android.util.Log.d("ResultsFragment", "Filtered ${filtered.size} items for kind: $kind")
    }

    private fun updateTabCounts() {
        val imageCount = allMediaItems.count { it.mediaKind == MediaKind.IMAGE }
        val videoCount = allMediaItems.count { it.mediaKind == MediaKind.VIDEO }
        val otherCount = allMediaItems.count {
            it.mediaKind == MediaKind.DOCUMENT ||
            it.mediaKind == MediaKind.OTHER
        }

        binding.tabLayout.getTabAt(0)?.text = "Ảnh ($imageCount)"
        binding.tabLayout.getTabAt(1)?.text = "Video ($videoCount)"
        binding.tabLayout.getTabAt(2)?.text = "File khác ($otherCount)"
    }

    private fun updateResultsInfo(count: Int, filterInfo: String) {
        binding.tvResultsCount.text = if (count == 1) {
            "Found $count media file"
        } else {
            "Found $count media files"
        }
        binding.tvFilterInfo.text = filterInfo
    }

    private fun generateFilterInfo(items: List<MediaEntry>): String {
        val imageCount = items.count { it.mediaKind == MediaKind.IMAGE }
        val videoCount = items.count { it.mediaKind == MediaKind.VIDEO }
        val docCount = items.count { it.mediaKind == MediaKind.DOCUMENT }

        return when {
            imageCount > 0 && videoCount > 0 && docCount > 0 -> "All file types"
            imageCount > 0 && videoCount > 0 -> "Images & Videos"
            imageCount > 0 && docCount > 0 -> "Images & Documents"
            videoCount > 0 && docCount > 0 -> "Videos & Documents"
            imageCount > 0 -> "Images only"
            videoCount > 0 -> "Videos only"
            docCount > 0 -> "Documents only"
            else -> "All media"
        }
    }

    private fun openMediaPreview(mediaEntry: MediaEntry) {
        // TODO: Implement media preview
        val message = "Opening ${mediaEntry.displayName ?: "media file"}"
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
