package com.meta.brain.file.recovery.ui.results.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.ItemDateGroupBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data model for date-grouped media items
 */
data class DateGroup(
    val date: String,
    val items: List<MediaEntry>
)

/**
 * Adapter for displaying media items grouped by date in ResultsFragment
 */
class DateGroupAdapter(
    private val onItemClick: (MediaEntry) -> Unit,
    private val onItemLongClick: (MediaEntry) -> Boolean,
    private val onDateSelectAllChanged: (DateGroup, Boolean) -> Unit,
    private val onItemSelectionChanged: (MediaEntry, Boolean) -> Unit
) : ListAdapter<DateGroup, DateGroupAdapter.DateGroupViewHolder>(DateGroupDiffCallback()) {

    private var selectionMode = false
    private val selectedItems = mutableSetOf<String>() // Use URI strings for comparison

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun updateSelection(selected: Set<MediaEntry>) {
        selectedItems.clear()
        selectedItems.addAll(selected.map { it.uri.toString() })
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<MediaEntry> {
        return currentList.flatMap { group ->
            group.items.filter { selectedItems.contains(it.uri.toString()) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateGroupViewHolder {
        val binding = ItemDateGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DateGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DateGroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DateGroupViewHolder(
        private val binding: ItemDateGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var mediaAdapter: MediaThumbnailAdapter? = null

        fun bind(dateGroup: DateGroup) {
            // Set date label
            binding.tvDateLabel.text = dateGroup.date

            // Set item count
            binding.tvDateItemCount.text = "(${dateGroup.items.size})"

            // Set up media items grid
            if (mediaAdapter == null) {
                mediaAdapter = MediaThumbnailAdapter(
                    onItemClick = { item ->
                        onItemClick(item)
                    },
                    onItemLongClick = { item ->
                        onItemLongClick(item)
                    },
                    onSelectionChanged = { item, isSelected ->
                        if (isSelected) {
                            selectedItems.add(item.uri.toString())
                        } else {
                            selectedItems.remove(item.uri.toString())
                        }
                        updateSelectAllCheckbox(dateGroup)
                        // Notify the fragment about the selection change
                        onItemSelectionChanged(item, isSelected)
                    }
                )
                binding.rvMediaItems.adapter = mediaAdapter
            }

            // Update selection state in media adapter
            mediaAdapter?.setSelectionMode(selectionMode)
            mediaAdapter?.updateSelection(dateGroup.items.filter {
                selectedItems.contains(it.uri.toString())
            }.toSet())
            mediaAdapter?.submitList(dateGroup.items)

            // Update select all checkbox for this date group
            updateSelectAllCheckbox(dateGroup)

            // Handle select all checkbox click
            binding.cbSelectDateGroup.setOnClickListener {
                val isChecked = binding.cbSelectDateGroup.isChecked
                dateGroup.items.forEach { item ->
                    if (isChecked) {
                        selectedItems.add(item.uri.toString())
                    } else {
                        selectedItems.remove(item.uri.toString())
                    }
                }
                mediaAdapter?.updateSelection(
                    if (isChecked) dateGroup.items.toSet() else emptySet()
                )
                onDateSelectAllChanged(dateGroup, isChecked)
            }
        }

        private fun updateSelectAllCheckbox(dateGroup: DateGroup) {
            val allSelected = dateGroup.items.all { selectedItems.contains(it.uri.toString()) }

            binding.cbSelectDateGroup.isChecked = allSelected
            binding.cbSelectDateGroup.isEnabled = selectionMode
        }
    }

    class DateGroupDiffCallback : DiffUtil.ItemCallback<DateGroup>() {
        override fun areItemsTheSame(oldItem: DateGroup, newItem: DateGroup): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: DateGroup, newItem: DateGroup): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Extension to group media entries by date while preserving the original sort order
 * @param sortDateGroupsDescending if true, newest date groups appear first; if false, oldest first
 */
fun List<MediaEntry>.groupByDate(sortDateGroupsDescending: Boolean = true): List<DateGroup> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val groups = this
        .groupBy { entry ->
            val timestamp = (entry.dateTaken ?: entry.dateAdded) * 1000
            dateFormat.format(Date(timestamp))
        }
        .map { (date, items) ->
            // Preserve the original sort order from the list
            DateGroup(date, items)
        }

    return if (sortDateGroupsDescending) {
        groups.sortedByDescending { it.date }
    } else {
        groups.sortedBy { it.date }
    }
}



