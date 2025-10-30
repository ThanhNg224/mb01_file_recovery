package com.meta.brain.file.recovery.ui.results

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
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
import com.meta.brain.file.recovery.ui.common.AppToastBottom
import com.meta.brain.file.recovery.ui.common.showDeleteDialog
import com.meta.brain.file.recovery.ui.common.showExitDialog
import com.meta.brain.file.recovery.ui.home.HomeViewModel
import com.meta.brain.file.recovery.ui.home.MediaUiState
import com.meta.brain.file.recovery.ui.results.adapter.DateGroupAdapter
import com.meta.brain.file.recovery.ui.results.adapter.groupByDate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: HomeViewModel by activityViewModels()
    private val resultsViewModel: ResultsViewModel by viewModels()
    private val args: ResultsFragmentArgs by navArgs()

    private lateinit var dateGroupAdapter: DateGroupAdapter

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
        binding.tvToolbarTitle.text = args.scanConfig.getResultsTitle()
        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }
    }

    private fun handleBackPressed() {
        // Show exit dialog if there are results to prevent accidental loss
        if (resultsViewModel.allItems.value.isNotEmpty()) {
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
                if (resultsViewModel.isSelectionMode.value) {
                    resultsViewModel.toggleSelect(item)
                } else {
                    // Navigate to preview
                    navigateToPreview(item)
                }
            },
            onItemLongClick = { item ->
                if (!resultsViewModel.isSelectionMode.value) {
                    resultsViewModel.enterSelectionMode(item)
                }
                true
            },
            onDateSelectAllChanged = { dateGroup, isChecked ->
                if (isChecked) {
                    resultsViewModel.selectAll(dateGroup.items)
                } else {
                    // Deselect items in this group
                    dateGroup.items.forEach { item ->
                        if (resultsViewModel.isSelected(item)) {
                            resultsViewModel.toggleSelect(item)
                        }
                    }
                }
            },
            onItemSelectionChanged = { item, isSelected ->
                if (isSelected) {
                    if (!resultsViewModel.isSelectionMode.value) {
                        resultsViewModel.enterSelectionMode(item)
                    } else {
                        resultsViewModel.toggleSelect(item)
                    }
                } else {
                    resultsViewModel.toggleSelect(item)
                }
            }
        )

        binding.rvMediaGrid.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = dateGroupAdapter
        }

        // Setup action bar buttons
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                resultsViewModel.selectAll(resultsViewModel.allItems.value)
            } else {
                resultsViewModel.clearSelection()
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
        binding.btnRecover.setOnClickListener {
            if (resultsViewModel.selectedItems.value.isNotEmpty()) {
                showRestoreDialog()
            }
        }

        binding.btnDelete.setOnClickListener {
            if (resultsViewModel.selectedItems.value.isNotEmpty()) {
                showDeleteDialog(
                    itemCount = resultsViewModel.selectedItems.value.size,
                    itemType = "files",
                    onConfirmDelete = {
                        performBatchDelete()
                    }
                )
            }
        }
    }

    private fun observeViewModel() {
        // Observe scan results from shared HomeViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.uiState.collect { state ->
                handleScanState(state)
            }
        }

        // Observe sorted items
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.sortedItems.collect { items ->
                updateMediaList(items)
            }
        }

        // Observe selection state
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.selectedItems.collect { selectedItems ->
                updateSelectionUI(selectedItems)
                dateGroupAdapter.updateSelection(selectedItems)
            }
        }

        // Observe selection mode
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.isSelectionMode.collect { isSelectionMode ->
                binding.layoutFloatingButtons.visibility =
                    if (isSelectionMode) View.VISIBLE else View.GONE
                dateGroupAdapter.setSelectionMode(isSelectionMode)
            }
        }

        // Observe folder count
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.folderCount.collect { count ->
                binding.tvCompletionFolders.text = getString(R.string.folder_count, count)
            }
        }

        // Observe all items for completion card
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.allItems.collect { items ->
                binding.tvCompletionItems.text = getString(R.string.item_count, items.size)
            }
        }

        // Observe restore state
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.restoreState.collect { state ->
                handleRestoreState(state)
            }
        }

        // Observe delete state
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.deleteState.collect { state ->
                handleDeleteState(state)
            }
        }
    }

    private fun handleScanState(state: MediaUiState) {
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

                // Initialize ViewModel with scan results
                resultsViewModel.setItems(state.list)
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

    private fun updateSelectionUI(selectedItems: Set<MediaEntry>) {
        val allItems = resultsViewModel.allItems.value

        if (selectedItems.isNotEmpty()) {
            binding.btnRecover.text = getString(R.string.restore_with_count, selectedItems.size)
            binding.btnDelete.text = getString(R.string.delete_with_count, selectedItems.size)
            binding.cbSelectAll.isChecked = selectedItems.size == allItems.size
        } else {
            binding.btnRecover.text = getString(R.string.recover)
            binding.btnDelete.text = getString(R.string.delete)
            binding.cbSelectAll.isChecked = false
        }
    }

    private fun updateMediaList(items: List<MediaEntry>) {
        if (items.isEmpty()) {
            // Show empty state
            binding.progressBar.visibility = View.GONE
            binding.rvMediaGrid.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.layoutErrorState.visibility = View.GONE
            binding.cardCompletionStatus.visibility = View.GONE
            binding.layoutActionBar.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Determine date group sort order based on sort type
            val sortDateGroupsDescending = when (resultsViewModel.currentSortType.value) {
                SortType.DATE_NEW_TO_OLD -> true  // Newest date groups first
                SortType.DATE_OLD_TO_NEW -> false // Oldest date groups first
                else -> true // Default to newest first for size sorting
            }

            val dateGroups = items.groupByDate(sortDateGroupsDescending)
            dateGroupAdapter.submitList(dateGroups)

            // Update item count
            binding.tvItemCount.text = getString(R.string.item_count_format, items.size)
        }
    }

    private fun navigateToPreview(item: MediaEntry) {
        val allItems = resultsViewModel.allItems.value
        val startIndex = allItems.indexOf(item)
        if (startIndex == -1) return

        val action = ResultsFragmentDirections.actionResultsToPreview(
            visibleItems = allItems.toTypedArray(),
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

        // Update UI based on current sort type from ViewModel
        updateSortDialogSelection(dialogBinding, resultsViewModel.currentSortType.value)

        // Set click listeners for each option
        dialogBinding.cardSizeSmallToLarge.setOnClickListener {
            resultsViewModel.applySorting(SortType.SIZE_SMALL_TO_LARGE)
            dialog.dismiss()
        }

        dialogBinding.cardSizeLargeToSmall.setOnClickListener {
            resultsViewModel.applySorting(SortType.SIZE_LARGE_TO_SMALL)
            dialog.dismiss()
        }

        dialogBinding.cardDateOldToNew.setOnClickListener {
            resultsViewModel.applySorting(SortType.DATE_OLD_TO_NEW)
            dialog.dismiss()
        }

        dialogBinding.cardDateNewToOld.setOnClickListener {
            resultsViewModel.applySorting(SortType.DATE_NEW_TO_OLD)
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
        val selectedBgColor = "#E9F5FF".toColorInt()
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
            val container = card.getChildAt(0) as? ViewGroup
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
        val selectedContainer = selectedCard.getChildAt(0) as? ViewGroup
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


    private fun showRestoreDialog() {
        val selectedItems = resultsViewModel.selectedItems.value
        val itemCount = selectedItems.size
        val totalSize = resultsViewModel.getFormattedSelectedSize()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restore Files")
            .setMessage("Restore $itemCount file(s) ($totalSize) to Downloads/RELive/Restored folder?")
            .setPositiveButton("Restore") { _, _ ->
                resultsViewModel.startRestore()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performBatchDelete() {
        val selectedItems = resultsViewModel.selectedItems.value

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var successCount = 0
                var failCount = 0
                val itemsToDelete = selectedItems.toList()
                val successfullyDeleted = mutableListOf<MediaEntry>()

                // Perform actual deletion - this is the only UI-layer responsibility
                itemsToDelete.forEach { entry ->
                    try {
                        val deleted = requireContext().contentResolver.delete(entry.uri, null, null)
                        if (deleted > 0) {
                            successCount++
                            successfullyDeleted.add(entry)
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ResultsFragment", "Failed to delete: ${e.message}", e)
                        failCount++
                    }
                }

                // Update ViewModel with results - business logic
                resultsViewModel.removeDeletedItems(successfullyDeleted)
                resultsViewModel.notifyDeleteComplete(successCount, failCount)

            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error deleting files: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun handleDeleteState(state: DeleteState) {
        when (state) {
            is DeleteState.Idle -> {
                // Do nothing
            }
            is DeleteState.Done -> {
                // Exit selection mode
                resultsViewModel.exitSelectionMode()

                // Show result toast
                val message = if (state.failCount > 0) {
                    "Deleted ${state.successCount} file(s), ${state.failCount} failed"
                } else {
                    "Deleted ${state.successCount} file(s) successfully"
                }

                AppToastBottom.show(
                    activity = requireActivity(),
                    message = message,
                    duration = 2000L
                )

                // Reset delete state
                resultsViewModel.resetDeleteState()
            }
            is DeleteState.Error -> {
                Snackbar.make(binding.root, "Delete failed: ${state.message}", Snackbar.LENGTH_LONG).show()
                resultsViewModel.resetDeleteState()
            }
        }
    }

    private fun handleRestoreState(state: RestoreState) {
        when (state) {
            is RestoreState.Idle -> {
                // Do nothing
            }
            is RestoreState.Running -> {
                showRestoreProgressDialog(state)
            }
            is RestoreState.Done -> {
                dismissRestoreProgressDialog()

                // Show success message
                val message = if (state.failCount > 0) {
                    "Restored ${state.successCount} file(s), ${state.failCount} failed"
                } else {
                    "Restored ${state.successCount} file(s) to ${state.destinationPath}"
                }

                AppToastBottom.show(
                    activity = requireActivity(),
                    message = message,
                    duration = 3000L
                )

                // Reset restore state (ViewModel already exited selection mode)
                resultsViewModel.resetRestoreState()
            }
            is RestoreState.Error -> {
                dismissRestoreProgressDialog()
                Snackbar.make(binding.root, "Restore failed: ${state.message}", Snackbar.LENGTH_LONG).show()
                resultsViewModel.resetRestoreState()
            }
        }
    }

    private var restoreProgressDialog: androidx.appcompat.app.AlertDialog? = null
    private var restoreProgressBinding: com.meta.brain.file.recovery.databinding.DialogRestoreProgressBinding? = null

    @SuppressLint("SetTextI18n")
    private fun showRestoreProgressDialog(state: RestoreState.Running) {
        if (restoreProgressDialog == null) {
            restoreProgressBinding = com.meta.brain.file.recovery.databinding.DialogRestoreProgressBinding.inflate(layoutInflater)

            restoreProgressDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(restoreProgressBinding!!.root)
                .setCancelable(false)
                .create()

            // Set up cancel button
            restoreProgressBinding?.btnCancelRestore?.setOnClickListener {
                resultsViewModel.cancelRestore()
                dismissRestoreProgressDialog()
            }

            restoreProgressDialog?.show()
        }

        // Update progress
        restoreProgressBinding?.apply {
            progressBar.max = state.total
            progressBar.progress = state.progress
            tvProgressCount.text = "${state.progress} / ${state.total}"
            tvCurrentFile.text = state.currentFileName
        }
    }

    private fun dismissRestoreProgressDialog() {
        restoreProgressDialog?.dismiss()
        restoreProgressDialog = null
        restoreProgressBinding = null
    }

    override fun onDestroyView() {
        dismissRestoreProgressDialog()
        super.onDestroyView()
        _binding = null
    }
}
