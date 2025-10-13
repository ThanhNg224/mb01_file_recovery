package com.meta.brain.file.recovery.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.SheetResultsFilterBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ResultsFilterSheet : BottomSheetDialogFragment() {

    private var _binding: SheetResultsFilterBinding? = null
    private val binding get() = _binding!!

    private val filterViewModel: ResultsFilterViewModel by viewModels({ requireParentFragment() })

    private var currentSpec: ResultsFilterSpec = ResultsFilterSpec.DEFAULT
    private var customDateFrom: Long? = null
    private var customDateTo: Long? = null
    private var customSizeMin: Long? = null
    private var customSizeMax: Long? = null
    private var currentSortDir: SortDirection = SortDirection.DESC

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetResultsFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand the bottom sheet to full height
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        // Get current filter spec
        currentSpec = filterViewModel.filterSpec.value
        currentSortDir = currentSpec.sortDir
        customDateFrom = currentSpec.fromMillis
        customDateTo = currentSpec.toMillis
        customSizeMin = currentSpec.minSizeBytes
        customSizeMax = currentSpec.maxSizeBytes

        setupDateFilter()
        setupSizeFilter()
        setupSortOptions()
        setupGridSpan()
        setupActionButtons()
    }

    private fun setupDateFilter() {
        // Set current selection
        when (currentSpec.datePreset) {
            DatePreset.ANY -> binding.chipDateAny.isChecked = true
            DatePreset.LAST_1_MONTH -> binding.chipDateLast1M.isChecked = true
            DatePreset.LAST_6_MONTHS -> binding.chipDateLast6M.isChecked = true
            DatePreset.CUSTOM -> {
                binding.chipDateCustom.isChecked = true
                updateCustomDateDisplay()
            }
        }

        // Handle chip clicks
        binding.chipDateCustom.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.date_custom)

        // Set initial selection if available
        if (customDateFrom != null && customDateTo != null) {
            builder.setSelection(Pair(customDateFrom, customDateTo))
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            customDateFrom = selection.first
            customDateTo = selection.second
            binding.chipDateCustom.isChecked = true
            updateCustomDateDisplay()
        }
        picker.show(parentFragmentManager, "date_range_picker")
    }

    private fun updateCustomDateDisplay() {
        if (customDateFrom != null && customDateTo != null) {
            val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val fromStr = format.format(Date(customDateFrom!!))
            val toStr = format.format(Date(customDateTo!!))
            binding.tvCustomDateRange.text = getString(R.string.custom_date_range, fromStr, toStr)
            binding.tvCustomDateRange.visibility = View.VISIBLE
        } else {
            binding.tvCustomDateRange.visibility = View.GONE
        }
    }

    private fun setupSizeFilter() {
        // Set current selection
        when (currentSpec.sizePreset) {
            SizePreset.ANY -> binding.chipSizeAny.isChecked = true
            SizePreset.LT_1MB -> binding.chipSizeLt1MB.isChecked = true
            SizePreset.FROM_1_TO_5MB -> binding.chipSize1To5MB.isChecked = true
            SizePreset.GT_5MB -> binding.chipSizeGt5MB.isChecked = true
            SizePreset.CUSTOM -> {
                binding.chipSizeCustom.isChecked = true
                showCustomSizeControls()
            }
        }

        // Handle custom chip
        binding.chipSizeCustom.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showCustomSizeControls()
            } else {
                binding.layoutCustomSize.visibility = View.GONE
            }
        }

        // Setup range slider (0-100 MB mapped to 0-100 on slider, but display shows actual MB/GB)
        //use a logarithmic scale for better UX: 0-10 MB (0-50), 10-100 MB (50-80), 100MB-1GB (80-100)
        binding.rangeSliderSize.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            customSizeMin = sliderToBytes(values[0])
            customSizeMax = sliderToBytes(values[1])
            updateCustomSizeDisplay()
        }

        // Initialize custom size if already set
        if (currentSpec.sizePreset == SizePreset.CUSTOM && customSizeMin != null && customSizeMax != null) {
            val minSlider = bytesToSlider(customSizeMin!!)
            val maxSlider = bytesToSlider(customSizeMax!!)
            binding.rangeSliderSize.values = listOf(minSlider, maxSlider)
            updateCustomSizeDisplay()
        }
    }

    private fun showCustomSizeControls() {
        binding.layoutCustomSize.visibility = View.VISIBLE
        updateCustomSizeDisplay()
    }

    private fun sliderToBytes(value: Float): Long {
        // Logarithmic mapping: 0-100 slider -> 0 bytes to 1 GB
        // 0-50: 0 to 10 MB
        // 50-80: 10 MB to 100 MB
        // 80-100: 100 MB to 1 GB
        return when {
            value <= 50f -> {
                (value / 50f * 10L * 1024L * 1024L).toLong()
            }
            value <= 80f -> {
                val progress = (value - 50f) / 30f
                (10L * 1024L * 1024L + progress * 90L * 1024L * 1024L).toLong()
            }
            else -> {
                val progress = (value - 80f) / 20f
                (100L * 1024L * 1024L + progress * 924L * 1024L * 1024L).toLong()
            }
        }
    }

    private fun bytesToSlider(bytes: Long): Float {
        val mb = bytes / (1024f * 1024f)
        return when {
            mb <= 10f -> mb / 10f * 50f
            mb <= 100f -> 50f + (mb - 10f) / 90f * 30f
            else -> 80f + (mb - 100f) / 924f * 20f
        }.coerceIn(0f, 100f)
    }

    private fun updateCustomSizeDisplay() {
        if (customSizeMin != null && customSizeMax != null) {
            val minStr = formatSize(customSizeMin!!)
            val maxStr = formatSize(customSizeMax!!)
            binding.tvCustomSizeRange.text = getString(R.string.custom_size_range, minStr, maxStr)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
        }
    }

    private fun setupSortOptions() {
        // Set current sort selection
        when (currentSpec.sortBy) {
            SortBy.DATE -> binding.chipSortDate.isChecked = true
            SortBy.SIZE -> binding.chipSortSize.isChecked = true
            SortBy.NAME -> binding.chipSortName.isChecked = true
        }

        // Set sort direction button
        updateSortDirectionButton()

        binding.btnSortDirection.setOnClickListener {
            currentSortDir = if (currentSortDir == SortDirection.DESC) {
                SortDirection.ASC
            } else {
                SortDirection.DESC
            }
            updateSortDirectionButton()
        }
    }

    private fun updateSortDirectionButton() {
        val iconRes = if (currentSortDir == SortDirection.DESC) {
            R.drawable.ic_arrow_down
        } else {
            R.drawable.ic_arrow_up
        }
        binding.btnSortDirection.setIconResource(iconRes)
    }

    private fun setupGridSpan() {
        when (currentSpec.spanCount) {
            2 -> binding.chipSpan2.isChecked = true
            3 -> binding.chipSpan3.isChecked = true
            4 -> binding.chipSpan4.isChecked = true
            5 -> binding.chipSpan5.isChecked = true
        }
    }

    private fun setupActionButtons() {
        binding.btnReset.setOnClickListener {
            filterViewModel.resetToDefaults()
            dismiss()
        }

        binding.btnApply.setOnClickListener {
            applyFilters()
        }
    }

    private fun applyFilters() {
        // Determine date preset
        val datePreset = when {
            binding.chipDateAny.isChecked -> DatePreset.ANY
            binding.chipDateLast1M.isChecked -> DatePreset.LAST_1_MONTH
            binding.chipDateLast6M.isChecked -> DatePreset.LAST_6_MONTHS
            binding.chipDateCustom.isChecked -> DatePreset.CUSTOM
            else -> DatePreset.ANY
        }

        // Determine size preset
        val sizePreset = when {
            binding.chipSizeAny.isChecked -> SizePreset.ANY
            binding.chipSizeLt1MB.isChecked -> SizePreset.LT_1MB
            binding.chipSize1To5MB.isChecked -> SizePreset.FROM_1_TO_5MB
            binding.chipSizeGt5MB.isChecked -> SizePreset.GT_5MB
            binding.chipSizeCustom.isChecked -> SizePreset.CUSTOM
            else -> SizePreset.ANY
        }

        // Determine sort by
        val sortBy = when {
            binding.chipSortDate.isChecked -> SortBy.DATE
            binding.chipSortSize.isChecked -> SortBy.SIZE
            binding.chipSortName.isChecked -> SortBy.NAME
            else -> SortBy.DATE
        }

        // Determine span count
        val spanCount = when {
            binding.chipSpan2.isChecked -> 2
            binding.chipSpan3.isChecked -> 3
            binding.chipSpan4.isChecked -> 4
            binding.chipSpan5.isChecked -> 5
            else -> 3
        }

        val newSpec = ResultsFilterSpec(
            datePreset = datePreset,
            fromMillis = if (datePreset == DatePreset.CUSTOM) customDateFrom else null,
            toMillis = if (datePreset == DatePreset.CUSTOM) customDateTo else null,
            sizePreset = sizePreset,
            minSizeBytes = if (sizePreset == SizePreset.CUSTOM) customSizeMin else null,
            maxSizeBytes = if (sizePreset == SizePreset.CUSTOM) customSizeMax else null,
            sortBy = sortBy,
            sortDir = currentSortDir,
            spanCount = spanCount
        )

        android.util.Log.d("ResultsFilterSheet", "Applying filter: $newSpec")
        filterViewModel.updateFilterSpec(newSpec)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
