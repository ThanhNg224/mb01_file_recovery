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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.DialogSortFilterBinding
import com.meta.brain.file.recovery.databinding.FragmentResultsBinding
import com.meta.brain.file.recovery.ui.common.showExitDialog
import com.meta.brain.file.recovery.ui.home.HomeViewModel
import com.meta.brain.file.recovery.ui.home.MediaUiState
import com.meta.brain.file.recovery.ui.results.adapter.DateGroupAdapter
import com.meta.brain.file.recovery.ui.results.adapter.groupByDate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: HomeViewModel by activityViewModels()
    private val args: ResultsFragmentArgs by navArgs()

    private lateinit var dateGroupAdapter: DateGroupAdapter
    private var allMediaItems: List<MediaEntry> = emptyList()
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<MediaEntry>()

    // Sort state
    private var currentSortType: SortType = SortType.DATE_OLD_TO_NEW

    enum class SortType {
        SIZE_SMALL_TO_LARGE,
        SIZE_LARGE_TO_SMALL,
        DATE_OLD_TO_NEW,
        DATE_NEW_TO_OLD
    }

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
        // Show exit dialog if there are results to prevent accidental loss
        if (allMediaItems.isNotEmpty()) {
            showExitDialog {
                // User confirmed exit
                navigateBack()
            }
        } else {
            // No results, just navigate back
            navigateBack()
        }
    }

    private fun navigateBack() {
        try {
            if (!findNavController().popBackStack()) {
                findNavController().navigateUp()
            }
        } catch (_: Exception) {
            requireActivity().finish()
        }
    }

    private fun setupRecyclerView() {
        dateGroupAdapter = DateGroupAdapter(
            onItemClick = { item ->
                if (isSelectionMode) {
                    toggleItemSelection(item)
                } else {
                    // Navigate to preview
                    navigateToPreview(item)
                }
            },
            onItemLongClick = { item ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                    toggleItemSelection(item)
                }
                true
            },
            onDateSelectAllChanged = { dateGroup, isChecked ->
                dateGroup.items.forEach { item ->
                    if (isChecked) {
                        selectedItems.add(item)
                    } else {
                        selectedItems.remove(item)
                    }
                }
                updateSelectionUI()
            },
            onItemSelectionChanged = { item, isSelected ->
                if (isSelected) {
                    if (!isSelectionMode) {
                        enterSelectionMode()
                    }
                    selectedItems.add(item)
                } else {
                    selectedItems.remove(item)
                    if (selectedItems.isEmpty()) {
                        exitSelectionMode()
                    }
                }
                updateSelectionUI()
            }
        )

        binding.rvMediaGrid.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = dateGroupAdapter
        }

        // Setup action bar buttons
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectAllItems()
            } else {
                deselectAllItems()
            }
        }

        // Make "Select All" text clickable
        binding.tvSelectAllLabel.setOnClickListener {
            binding.cbSelectAll.isChecked = !binding.cbSelectAll.isChecked
        }

        binding.ivFilter.setOnClickListener {
            showSortFilterDialog()
        }

        // Floating action buttons
        binding.btnRestore.setOnClickListener {
            if (selectedItems.isNotEmpty()) {
                // TODO: Show restore dialog
                Snackbar.make(binding.root, "${selectedItems.size} items selected for restore", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnDelete.setOnClickListener {
            if (selectedItems.isNotEmpty()) {
                // TODO: Show delete confirmation dialog
                Snackbar.make(binding.root, "${selectedItems.size} items selected for deletion", Snackbar.LENGTH_SHORT).show()
            }
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
                binding.cardCompletionStatus.visibility = View.GONE
                binding.layoutActionBar.visibility = View.GONE
            }

            is MediaUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE
                binding.cardCompletionStatus.visibility = View.GONE
                binding.layoutActionBar.visibility = View.GONE
            }

            is MediaUiState.Items -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE
                binding.cardCompletionStatus.visibility = View.VISIBLE
                binding.layoutActionBar.visibility = View.VISIBLE

                allMediaItems = state.list
                groupItemsByDate(state.list)

                // Update completion card
                val folderCount = state.list.mapNotNull { it.filePath?.substringBeforeLast("/") }.distinct().size
                binding.tvCompletionDetails.text = getString(R.string.completion_details, state.list.size, folderCount)
            }

            is MediaUiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.layoutErrorState.visibility = View.GONE
                binding.cardCompletionStatus.visibility = View.GONE
                binding.layoutActionBar.visibility = View.GONE
            }

            is MediaUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.rvMediaGrid.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.VISIBLE
                binding.cardCompletionStatus.visibility = View.GONE
                binding.layoutActionBar.visibility = View.GONE

                binding.tvErrorMessage.text = state.message

                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        findNavController().popBackStack()
                    }
                    .show()
            }
        }
    }

    private fun groupItemsByDate(items: List<MediaEntry>, sortDateGroupsDescending: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dateGroups = items.groupByDate(sortDateGroupsDescending)
            dateGroupAdapter.submitList(dateGroups)

            // Update item count
            binding.tvItemCount.text = getString(R.string.item_count_format, items.size)
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        dateGroupAdapter.setSelectionMode(true)
        binding.layoutFloatingButtons.visibility = View.VISIBLE
        updateSelectionUI()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        dateGroupAdapter.setSelectionMode(false)
        dateGroupAdapter.updateSelection(emptySet())
        binding.layoutFloatingButtons.visibility = View.GONE
        binding.cbSelectAll.isChecked = false
        updateSelectionUI()
    }

    private fun toggleItemSelection(item: MediaEntry) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }

        if (selectedItems.isEmpty()) {
            exitSelectionMode()
        } else {
            dateGroupAdapter.updateSelection(selectedItems)
            updateSelectionUI()
        }
    }

    private fun selectAllItems() {
        selectedItems.clear()
        selectedItems.addAll(allMediaItems)
        if (!isSelectionMode) {
            enterSelectionMode()
        }
        dateGroupAdapter.updateSelection(selectedItems)
        updateSelectionUI()
    }

    private fun deselectAllItems() {
        selectedItems.clear()
        dateGroupAdapter.updateSelection(emptySet())
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        if (selectedItems.isNotEmpty()) {
            binding.btnRestore.text = getString(R.string.restore_with_count, selectedItems.size)
            binding.btnDelete.text = getString(R.string.delete_with_count, selectedItems.size)
            binding.cbSelectAll.isChecked = selectedItems.size == allMediaItems.size
        } else {
            binding.btnRestore.text = getString(R.string.restore)
            binding.btnDelete.text = getString(R.string.delete)
            binding.cbSelectAll.isChecked = false
        }
    }

    private fun navigateToPreview(item: MediaEntry) {
        val startIndex = allMediaItems.indexOf(item)
        if (startIndex == -1) return

        val action = ResultsFragmentDirections.actionResultsToPreview(
            visibleItems = allMediaItems.toTypedArray(),
            startIndex = startIndex,
            fromArchive = false
        )
        findNavController().navigate(action)
    }

    private fun showSortFilterDialog() {
        val dialogBinding = DialogSortFilterBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // Update UI based on current sort type
        updateSortDialogSelection(dialogBinding, currentSortType)

        // Set click listeners for each option
        dialogBinding.cardSizeSmallToLarge.setOnClickListener {
            applySorting(SortType.SIZE_SMALL_TO_LARGE)
            dialog.dismiss()
        }

        dialogBinding.cardSizeLargeToSmall.setOnClickListener {
            applySorting(SortType.SIZE_LARGE_TO_SMALL)
            dialog.dismiss()
        }

        dialogBinding.cardDateOldToNew.setOnClickListener {
            applySorting(SortType.DATE_OLD_TO_NEW)
            dialog.dismiss()
        }

        dialogBinding.cardDateNewToOld.setOnClickListener {
            applySorting(SortType.DATE_NEW_TO_OLD)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateSortDialogSelection(dialogBinding: DialogSortFilterBinding, sortType: SortType) {
        // Colors
        val defaultStrokeColor = requireContext().getColor(R.color.home_text_secondary).let {
            android.graphics.Color.argb(64,
                android.graphics.Color.red(it),
                android.graphics.Color.green(it),
                android.graphics.Color.blue(it)
            )
        }
        val defaultBgColor = requireContext().getColor(R.color.white)
        val defaultTextColor = requireContext().getColor(R.color.home_text_primary)
        val defaultSubTextColor = requireContext().getColor(R.color.home_text_secondary)
        val selectedStrokeColor = requireContext().getColor(R.color.dialog_primary_blue)
        @Suppress("DEPRECATION")
        val selectedBgColor = android.graphics.Color.parseColor("#E9F5FF")
        val selectedTextColor = requireContext().getColor(R.color.dialog_primary_blue)

        // Reset all cards to default style
        listOf(
            dialogBinding.cardSizeSmallToLarge,
            dialogBinding.cardSizeLargeToSmall,
            dialogBinding.cardDateOldToNew,
            dialogBinding.cardDateNewToOld
        ).forEach { card ->
            card.strokeColor = defaultStrokeColor
            card.strokeWidth = 2
            card.setCardBackgroundColor(defaultBgColor)

            // Reset all child views to default colors
            val container = card.getChildAt(0) as? android.view.ViewGroup
            container?.let { layout ->
                for (i in 0 until layout.childCount) {
                    when (val child = layout.getChildAt(i)) {
                        is android.widget.ImageView -> {
                            child.setColorFilter(defaultTextColor)
                        }
                        is android.widget.TextView -> {
                            if (child.textSize > 14f) { // Title text
                                child.setTextColor(defaultTextColor)
                            } else { // Subtitle text
                                child.setTextColor(defaultSubTextColor)
                            }
                        }
                    }
                }
            }
        }

        // Highlight selected card
        val selectedCard = when (sortType) {
            SortType.SIZE_SMALL_TO_LARGE -> dialogBinding.cardSizeSmallToLarge
            SortType.SIZE_LARGE_TO_SMALL -> dialogBinding.cardSizeLargeToSmall
            SortType.DATE_OLD_TO_NEW -> dialogBinding.cardDateOldToNew
            SortType.DATE_NEW_TO_OLD -> dialogBinding.cardDateNewToOld
        }
        selectedCard.strokeColor = selectedStrokeColor
        selectedCard.strokeWidth = 4
        selectedCard.setCardBackgroundColor(selectedBgColor)

        // Update selected card's child views to blue
        val selectedContainer = selectedCard.getChildAt(0) as? android.view.ViewGroup
        selectedContainer?.let { layout ->
            for (i in 0 until layout.childCount) {
                when (val child = layout.getChildAt(i)) {
                    is android.widget.ImageView -> {
                        child.setColorFilter(selectedTextColor)
                    }
                    is android.widget.TextView -> {
                        child.setTextColor(selectedTextColor)
                    }
                }
            }
        }
    }

    private fun applySorting(sortType: SortType) {
        currentSortType = sortType

        val sortedItems = when (sortType) {
            SortType.SIZE_SMALL_TO_LARGE -> {
                allMediaItems.sortedBy { it.size }
            }
            SortType.SIZE_LARGE_TO_SMALL -> {
                allMediaItems.sortedByDescending { it.size }
            }
            SortType.DATE_OLD_TO_NEW -> {
                allMediaItems.sortedBy { it.dateTaken ?: it.dateAdded }
            }
            SortType.DATE_NEW_TO_OLD -> {
                allMediaItems.sortedByDescending { it.dateTaken ?: it.dateAdded }
            }
        }

        allMediaItems = sortedItems

        // Determine date group sort order based on sort type
        val sortDateGroupsDescending = when (sortType) {
            SortType.DATE_NEW_TO_OLD -> true  // Newest date groups first
            SortType.DATE_OLD_TO_NEW -> false // Oldest date groups first
            else -> true // Default to newest first for size sorting
        }

        groupItemsByDate(sortedItems, sortDateGroupsDescending)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
