package com.meta.brain.file.recovery.ui.results

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.meta.brain.file.recovery.R
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.FragmentResultsBinding
import com.meta.brain.file.recovery.databinding.DialogRestoreConfirmBinding
import com.meta.brain.file.recovery.databinding.DialogRestoreProgressBinding
import com.meta.brain.file.recovery.ui.home.HomeViewModel
import com.meta.brain.file.recovery.ui.home.MediaUiState
import com.meta.brain.file.recovery.ui.home.adapter.MediaAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: HomeViewModel by activityViewModels()
    private val filterViewModel: ResultsFilterViewModel by viewModels()
    private val resultsViewModel: ResultsViewModel by viewModels()

    @Inject
    lateinit var mediaRepository: MediaRepository

    private lateinit var mediaAdapter: MediaAdapter

    private var allMediaItems: List<MediaEntry> = emptyList()
    private var currentTabFilter: MediaKind? = null

    private var selectionActionMode: ActionMode? = null
    private var restoreProgressDialog: Dialog? = null

    private val args: ResultsFragmentArgs by navArgs()

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
        observeFilterViewModel()
        observeSelectionViewModel()
        observeRestoreState()

        // Fetch results if scanConfig is provided
        args.scanConfig.let { config ->
            val types = config.toMediaTypes()
            if (config.depth == com.meta.brain.file.recovery.data.model.ScanDepth.QUICK) {
                sharedViewModel.quickScan(
                    types = types,
                    minSize = config.minSize,
                    fromSec = config.fromSec,
                    toSec = config.toSec
                )
            } else {
                sharedViewModel.deepScan(
                    types = types,
                    minSize = config.minSize,
                    fromSec = config.fromSec,
                    toSec = config.toSec
                )
            }
        }
    }

    private fun setupToolbar() {
        // Set up the toolbar with proper back navigation
        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }

        // Select button click handler
        val selectButton = binding.toolbar.findViewById<View>(R.id.btnSelect)
        selectButton?.setOnClickListener {
            android.util.Log.d("ResultsFragment", "Select button clicked")
            if (!resultsViewModel.isSelectionMode.value) {
                // Enter selection mode without selecting any item initially
                resultsViewModel.enterSelectionMode()
            }
        }

        // Filter button click handler - access via findViewById since it's inside toolbar
        val filterButton = binding.toolbar.findViewById<View>(R.id.btnFilter)
        filterButton?.setOnClickListener {
            android.util.Log.d("ResultsFragment", "Filter button clicked")
            showFilterSheet()
        }

        // Also handle system back button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPressed()
        }
    }

    private fun handleBackPressed() {
        // Exit selection mode first if active
        if (resultsViewModel.isSelectionMode.value) {
            resultsViewModel.exitSelectionMode()
        } else {
            try {
                if (!findNavController().popBackStack()) {
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                requireActivity().finish()
            }
        }
    }

    private fun showFilterSheet() {
        android.util.Log.d("ResultsFragment", "Showing filter sheet")
        val filterSheet = ResultsFilterSheet()
        filterSheet.show(childFragmentManager, "filter_sheet")
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Ảnh (0)"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Video (0)"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Audio (0)"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("File khác (0)"))

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> filterByMediaKind(MediaKind.IMAGE)
                    1 -> filterByMediaKind(MediaKind.VIDEO)
                    2 -> filterByMediaKind(MediaKind.AUDIO)
                    3 -> filterByMediaKind(null) // File khác = DOCUMENT + OTHER
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(
            mediaRepository = mediaRepository,
            onItemClick = { mediaEntry ->
                if (resultsViewModel.isSelectionMode.value) {
                    resultsViewModel.toggleSelect(mediaEntry)
                } else {
                    openMediaPreview(mediaEntry)
                }
            },
            onItemLongClick = { mediaEntry ->
                if (!resultsViewModel.isSelectionMode.value) {
                    resultsViewModel.enterSelectionMode(mediaEntry)
                    true
                } else {
                    false
                }
            }
        )

        val spanCount = filterViewModel.filterSpec.value.spanCount

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

    private fun observeFilterViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            filterViewModel.filterSpec.collect { spec ->
                android.util.Log.d("ResultsFragment", "Filter spec changed: $spec")
                updateFilterSummary(spec)
                updateGridSpan(spec.spanCount)
                applyFiltersAndSort()
            }
        }
    }

    private fun observeSelectionViewModel() {
        // Observe selection mode
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.isSelectionMode.collect { isSelectionMode ->
                mediaAdapter.setSelectionMode(isSelectionMode)

                if (isSelectionMode) {
                    startSelectionMode()
                } else {
                    stopSelectionMode()
                }
            }
        }

        // Observe selected items
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.selectedItems.collect { selectedItems ->
                mediaAdapter.updateSelection(selectedItems)
                updateSelectionModeTitle(selectedItems.size)
            }
        }
    }

    private fun observeRestoreState() {
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.restoreState.collect { state ->
                handleRestoreState(state)
            }
        }
    }

    private fun startSelectionMode() {
        if (selectionActionMode == null) {
            selectionActionMode = (requireActivity() as? androidx.appcompat.app.AppCompatActivity)?.startSupportActionMode(
                object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        mode?.menuInflater?.inflate(R.menu.menu_selection, menu)
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        return when (item?.itemId) {
                            R.id.action_restore -> {
                                showRestoreConfirmDialog()
                                true
                            }
                            R.id.action_delete -> {
                                showDeleteConfirmDialog()
                                true
                            }
                            R.id.action_select_all -> {
                                resultsViewModel.selectAll(mediaAdapter.currentList)
                                true
                            }
                            R.id.action_clear -> {
                                resultsViewModel.clearSelection()
                                true
                            }
                            else -> false
                        }
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        selectionActionMode = null
                        resultsViewModel.exitSelectionMode()
                    }
                }
            )
        }
    }

    private fun stopSelectionMode() {
        selectionActionMode?.finish()
        selectionActionMode = null
    }

    private fun updateSelectionModeTitle(count: Int) {
        selectionActionMode?.title = "$count selected"
    }

    private fun showRestoreConfirmDialog() {
        val selectedItems = resultsViewModel.selectedItems.value
        if (selectedItems.isEmpty()) {
            Snackbar.make(binding.root, "No items selected", Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogRestoreConfirmBinding.inflate(layoutInflater)
        val totalSize = resultsViewModel.getFormattedSelectedSize()
        val count = selectedItems.size

        dialogBinding.tvConfirmMessage.text = "Restore $count ${if (count == 1) "file" else "files"} ($totalSize)?"
        dialogBinding.tvDestinationPath.text = "To: Downloads/RELive/Restored"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnRestore.setOnClickListener {
            dialog.dismiss()
            resultsViewModel.startRestore("RELive/Restored")
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog() {
        val selectedItems = resultsViewModel.selectedItems.value
        if (selectedItems.isEmpty()) {
            Snackbar.make(binding.root, "No items selected", Snackbar.LENGTH_SHORT).show()
            return
        }

        val count = selectedItems.size
        val totalSize = resultsViewModel.getFormattedSelectedSize()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Files")
            .setMessage("Are you sure you want to delete $count ${if (count == 1) "file" else "files"} ($totalSize)? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteSelectedFiles()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun deleteSelectedFiles() {
        val selectedItems = resultsViewModel.selectedItems.value.toList()
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0

            selectedItems.forEach { mediaEntry ->
                try {
                    val deleted = requireContext().contentResolver.delete(mediaEntry.uri, null, null)
                    if (deleted > 0) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ResultsFragment", "Failed to delete ${mediaEntry.displayName}: ${e.message}")
                    failCount++
                }
            }

            withContext(Dispatchers.Main) {
                // Exit selection mode
                resultsViewModel.exitSelectionMode()

                // Show result message
                val message = if (failCount == 0) {
                    "Deleted $successCount ${if (successCount == 1) "file" else "files"}"
                } else {
                    "Deleted $successCount/${selectedItems.size} (${failCount} failed)"
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

                // Refresh the list by removing deleted items
                if (successCount > 0) {
                    val deletedUris = selectedItems.map { it.uri }.toSet()
                    allMediaItems = allMediaItems.filter { it.uri !in deletedUris }
                    updateTabCounts()
                    applyFiltersAndSort()
                }
            }
        }
    }

    private fun handleRestoreState(state: RestoreState) {
        when (state) {
            is RestoreState.Idle -> {
                restoreProgressDialog?.dismiss()
                restoreProgressDialog = null
            }

            is RestoreState.Running -> {
                if (restoreProgressDialog == null) {
                    showRestoreProgressDialog()
                }
                updateRestoreProgress(state)
            }

            is RestoreState.Done -> {
                restoreProgressDialog?.dismiss()
                restoreProgressDialog = null
                showRestoreCompleteSnackbar(state)
                resultsViewModel.resetRestoreState()
            }

            is RestoreState.Error -> {
                restoreProgressDialog?.dismiss()
                restoreProgressDialog = null
                Snackbar.make(binding.root, "Restore failed: ${state.message}", Snackbar.LENGTH_LONG).show()
                resultsViewModel.resetRestoreState()
            }
        }
    }

    private fun showRestoreProgressDialog() {
        val dialogBinding = DialogRestoreProgressBinding.inflate(layoutInflater)

        restoreProgressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnCancelRestore.setOnClickListener {
            resultsViewModel.cancelRestore()
        }

        restoreProgressDialog?.show()
    }

    private fun updateRestoreProgress(state: RestoreState.Running) {
        val dialogBinding = restoreProgressDialog?.findViewById<View>(R.id.tvProgressCount)?.parent as? ViewGroup
        if (dialogBinding != null) {
            val progressBinding = DialogRestoreProgressBinding.bind(dialogBinding)
            progressBinding.tvProgressCount.text = "${state.progress} / ${state.total}"
            progressBinding.tvCurrentFile.text = state.currentFileName
            progressBinding.progressBar.max = state.total
            progressBinding.progressBar.progress = state.progress
        }
    }

    private fun showRestoreCompleteSnackbar(state: RestoreState.Done) {
        val message = if (state.failCount == 0) {
            "Restored ${state.successCount} ${if (state.successCount == 1) "file" else "files"}"
        } else {
            "Restored ${state.successCount}/${state.successCount + state.failCount} (${state.failCount} failed)"
        }

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Open Folder") {
                openDownloadsFolder()
            }
            .show()
    }

    private fun openDownloadsFolder() {
        try {
            // Try to open Downloads folder - API 29+ only
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "resource/folder"
                    putExtra("android.provider.extra.INITIAL_URI", android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
                }
                startActivity(intent)
            } else {
                // Fallback for older versions
                openFileManager()
            }
        } catch (_: Exception) {
            openFileManager()
        }
    }

    private fun openFileManager() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }
            startActivity(Intent.createChooser(intent, "Open Downloads"))
        } catch (_: Exception) {
            Snackbar.make(binding.root, "Could not open Downloads folder", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateFilterSummary(spec: ResultsFilterSpec) {
        val parts = mutableListOf<String>()

        // Date part
        when (spec.datePreset) {
            DatePreset.ANY -> parts.add("All")
            DatePreset.LAST_1_MONTH -> parts.add("Last 1M")
            DatePreset.LAST_6_MONTHS -> parts.add("Last 6M")
            DatePreset.CUSTOM -> {
                if (spec.fromMillis != null && spec.toMillis != null) {
                    val format = SimpleDateFormat("MMM dd", Locale.getDefault())
                    parts.add("${format.format(Date(spec.fromMillis))}–${format.format(Date(spec.toMillis))}")
                }
            }
        }

        // Size part
        when (spec.sizePreset) {
            SizePreset.ANY -> parts.add("Any size")
            SizePreset.LT_1MB -> parts.add("0–1 MB")
            SizePreset.FROM_1_TO_5MB -> parts.add("1–5 MB")
            SizePreset.GT_5MB -> parts.add("> 5 MB")
            SizePreset.CUSTOM -> {
                if (spec.minSizeBytes != null && spec.maxSizeBytes != null) {
                    parts.add("${formatSize(spec.minSizeBytes)}–${formatSize(spec.maxSizeBytes)}")
                }
            }
        }

        // Sort part
        val sortLabel = when (spec.sortBy) {
            SortBy.DATE -> "Date"
            SortBy.SIZE -> "Size"
            SortBy.NAME -> "Name"
        }
        val sortDir = if (spec.sortDir == SortDirection.DESC) "↓" else "↑"
        parts.add("$sortLabel $sortDir")

        // Span count
        parts.add("${spec.spanCount} cols")

        binding.tvFilterInfo.text = parts.joinToString(" · ")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> String.format(Locale.US, "%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun updateGridSpan(spanCount: Int) {
        val layoutManager = binding.rvMediaGrid.layoutManager as? GridLayoutManager
        if (layoutManager?.spanCount != spanCount) {
            layoutManager?.spanCount = spanCount
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
                updateResultsInfo(0)
            }

            is MediaUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE
                updateResultsInfo(0)
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
            }

            is MediaUiState.Empty -> {
                android.util.Log.d("ResultsFragment", "Showing empty state")
                binding.progressBar.visibility = View.GONE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutErrorState.visibility = View.GONE
                updateResultsInfo(0)
            }

            is MediaUiState.Error -> {
                android.util.Log.d("ResultsFragment", "Showing error: ${state.message}")
                binding.progressBar.visibility = View.GONE
                binding.progressBarBottom.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.VISIBLE

                binding.tvErrorMessage.text = state.message
                updateResultsInfo(0)

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
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val startTime = System.currentTimeMillis()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val spec = filterViewModel.filterSpec.value

            // Step 1: Filter by tab (media kind)
            var filtered = when (currentTabFilter) {
                MediaKind.IMAGE ->
                    allMediaItems.filter { it.mediaKind == MediaKind.IMAGE }
                MediaKind.VIDEO ->
                    allMediaItems.filter { it.mediaKind == MediaKind.VIDEO }
                MediaKind.AUDIO ->
                    allMediaItems.filter { it.mediaKind == MediaKind.AUDIO }
                null -> // Other Files tab
                    allMediaItems.filter {
                        it.mediaKind == MediaKind.DOCUMENT ||
                        it.mediaKind == MediaKind.OTHER
                    }
                else -> emptyList()
            }

            // Step 2: Apply date filter
            val (fromMillis, toMillis) = spec.getEffectiveDateRange()
            if (fromMillis != null || toMillis != null) {
                filtered = filtered.filter { entry ->
                    val timestamp = (entry.dateTaken ?: entry.dateAdded) * 1000L
                    val afterFrom = fromMillis == null || timestamp >= fromMillis
                    val beforeTo = toMillis == null || timestamp <= toMillis
                    afterFrom && beforeTo
                }
            }

            // Step 3: Apply size filter
            val (minSize, maxSize) = spec.getEffectiveSizeRange()
            if (minSize != null || maxSize != null) {
                filtered = filtered.filter { entry ->
                    val largerThanMin = minSize == null || entry.size >= minSize
                    val smallerThanMax = maxSize == null || entry.size <= maxSize
                    largerThanMin && smallerThanMax
                }
            }

            // Step 4: Sort
            filtered = when (spec.sortBy) {
                SortBy.DATE -> {
                    filtered.sortedBy { (it.dateTaken ?: it.dateAdded) }
                }
                SortBy.SIZE -> {
                    filtered.sortedBy { it.size }
                }
                SortBy.NAME -> {
                    filtered.sortedBy { it.displayName?.lowercase() ?: "" }
                }
            }

            // Apply sort direction
            if (spec.sortDir == SortDirection.DESC) {
                filtered = filtered.reversed()
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            android.util.Log.d("ResultsFragment", "Filtering took ${elapsedTime}ms, result: ${filtered.size} items")

            // Step 5: Update UI on main thread
            withContext(Dispatchers.Main) {
                mediaAdapter.submitList(filtered)
                updateResultsInfo(filtered.size)

                // Show empty state if no results after filtering
                if (filtered.isEmpty() && allMediaItems.isNotEmpty()) {
                    binding.rvMediaGrid.visibility = View.GONE
                    binding.layoutEmptyState.visibility = View.VISIBLE
                } else if (filtered.isNotEmpty()) {
                    binding.rvMediaGrid.visibility = View.VISIBLE
                    binding.layoutEmptyState.visibility = View.GONE
                }
            }
        }
    }

    private fun updateTabCounts() {
        val imageCount = allMediaItems.count { it.mediaKind == MediaKind.IMAGE }
        val videoCount = allMediaItems.count { it.mediaKind == MediaKind.VIDEO }
        val audioCount = allMediaItems.count { it.mediaKind == MediaKind.AUDIO }
        val otherCount = allMediaItems.count {
            it.mediaKind == MediaKind.DOCUMENT ||
            it.mediaKind == MediaKind.OTHER
        }

        binding.tabLayout.getTabAt(0)?.text = "Ảnh ($imageCount)"
        binding.tabLayout.getTabAt(1)?.text = "Video ($videoCount)"
        binding.tabLayout.getTabAt(2)?.text = "Audio ($audioCount)"
        binding.tabLayout.getTabAt(3)?.text = "File khác ($otherCount)"
    }

    private fun updateResultsInfo(count: Int) {
        binding.tvResultsCount.text = if (count == 1) {
            "Found $count media file"
        } else {
            "Found $count media files"
        }
    }

    private fun openMediaPreview(mediaEntry: MediaEntry) {
        // Get the current visible items from the adapter
        val visibleItems = mediaAdapter.currentList.toTypedArray()
        val startIndex = visibleItems.indexOf(mediaEntry)

        if (startIndex >= 0) {
            val action = ResultsFragmentDirections.actionResultsToPreview(
                visibleItems = visibleItems,
                startIndex = startIndex
            )
            findNavController().navigate(action)
        } else {
            Snackbar.make(binding.root, "Unable to open preview", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}