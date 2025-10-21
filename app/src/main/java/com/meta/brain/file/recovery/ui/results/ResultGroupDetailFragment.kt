package com.meta.brain.file.recovery.ui.results

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.DateSection
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaGroup
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.DialogRestoreConfirmBinding
import com.meta.brain.file.recovery.databinding.DialogRestoreProgressBinding
import com.meta.brain.file.recovery.databinding.FragmentResultGroupDetailBinding
import com.meta.brain.file.recovery.ui.results.adapter.DateSectionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ResultGroupDetailFragment : Fragment() {

    private var _binding: FragmentResultGroupDetailBinding? = null
    private val binding get() = _binding!!

    private val resultsViewModel: ResultsViewModel by activityViewModels()

    @Inject
    lateinit var mediaRepository: MediaRepository

    private lateinit var sectionAdapter: DateSectionAdapter
    private var restoreProgressDialog: Dialog? = null
    private lateinit var mediaGroup: MediaGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaGroup = arguments?.getParcelable("mediaGroup") ?: throw IllegalArgumentException("mediaGroup is required")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        observeRestoreState()
        loadGroupData()

        // Handle back button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPressed()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = mediaGroup.folderName
        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }

        // Select button
        binding.btnSelect.setOnClickListener {
            if (!resultsViewModel.isSelectionMode.value) {
                resultsViewModel.enterSelectionMode()
            }
        }
    }

    private fun handleBackPressed() {
        if (resultsViewModel.isSelectionMode.value) {
            resultsViewModel.exitSelectionMode()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        sectionAdapter = DateSectionAdapter(
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
            },
            onSelectAllClick = { items ->
                resultsViewModel.selectAll(items)
            }
        )

        binding.rvDateSections.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sectionAdapter
        }
    }

    private fun observeViewModel() {
        // Observe selection mode
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.isSelectionMode.collect { isSelectionMode ->
                sectionAdapter.setSelectionMode(isSelectionMode)
                updateActionBarVisibility(isSelectionMode)
            }
        }

        // Observe selected items
        viewLifecycleOwner.lifecycleScope.launch {
            resultsViewModel.selectedItems.collect { selectedItems ->
                sectionAdapter.updateSelection(selectedItems)
                updateSelectionCount(selectedItems.size)
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

    private fun loadGroupData() {
        // Group items by date
        val dateSections = groupItemsByDate(mediaGroup.allItems)

        if (dateSections.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvDateSections.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvDateSections.visibility = View.VISIBLE
            sectionAdapter.submitList(dateSections)
        }
    }

    private fun groupItemsByDate(items: List<MediaEntry>): List<DateSection> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        return items
            .groupBy { item ->
                val timestamp = (item.dateTaken ?: item.dateAdded) * 1000
                dateFormat.format(Date(timestamp))
            }
            .map { (dateStr, items) ->
                val timestamp = dateFormat.parse(dateStr)?.time ?: 0L
                DateSection(
                    date = dateStr,
                    displayDate = displayFormat.format(Date(timestamp)),
                    items = items.sortedByDescending { it.dateTaken ?: it.dateAdded },
                    totalSize = items.sumOf { it.size }
                )
            }
            .sortedByDescending { it.date }
    }

    private fun updateActionBarVisibility(visible: Boolean) {
        binding.cardActionBar.visibility = if (visible) View.VISIBLE else View.GONE

        if (visible) {
            binding.btnRestore.setOnClickListener {
                showRestoreConfirmDialog()
            }
            binding.btnDelete.setOnClickListener {
                showDeleteConfirmDialog()
            }
        }
    }

    private fun updateSelectionCount(count: Int) {
        binding.tvSelectionCount.text = getString(R.string.selection_count, count)
    }

    private fun openMediaPreview(mediaEntry: MediaEntry) {
        val allItems = mediaGroup.allItems.toTypedArray()
        val startIndex = allItems.indexOf(mediaEntry)

        val bundle = Bundle().apply {
            putParcelableArray("visibleItems", allItems)
            putInt("startIndex", startIndex)
            putBoolean("fromArchive", false)
        }
        findNavController().navigate(R.id.previewFragment, bundle)
    }

    private fun showRestoreConfirmDialog() {
        val selectedItems = resultsViewModel.selectedItems.value
        if (selectedItems.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_items_selected, Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogRestoreConfirmBinding.inflate(layoutInflater)
        val totalSize = resultsViewModel.getFormattedSelectedSize()
        val count = selectedItems.size

        val fileWord = resources.getQuantityString(R.plurals.files, count, count)
        dialogBinding.tvConfirmMessage.text = getString(R.string.restore_confirm_message, fileWord, totalSize)
        dialogBinding.tvDestinationPath.text = getString(R.string.restore_destination_path)

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
                } catch (_: Exception) {
                    failCount++
                }
            }

            withContext(Dispatchers.Main) {
                resultsViewModel.exitSelectionMode()

                val message = if (failCount == 0) {
                    "Deleted $successCount ${if (successCount == 1) "file" else "files"}"
                } else {
                    "Deleted $successCount/${selectedItems.size} (${failCount} failed)"
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

                // Navigate back if items were deleted
                if (successCount > 0) {
                    findNavController().navigateUp()
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
            progressBinding.tvProgressCount.text = getString(R.string.progress_count, state.progress, state.total)
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "resource/folder"
                    putExtra("android.provider.extra.INITIAL_URI", android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
                }
                startActivity(intent)
            } else {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
