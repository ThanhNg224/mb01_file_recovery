package com.meta.brain.file.recovery.ui.results.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.ItemMediaThumbnailBinding

/**
 * Adapter for displaying media thumbnails in a grid
 */
class MediaThumbnailAdapter(
    private val onItemClick: (MediaEntry) -> Unit,
    private val onItemLongClick: (MediaEntry) -> Boolean,
    private val onSelectionChanged: (MediaEntry, Boolean) -> Unit
) : ListAdapter<MediaEntry, MediaThumbnailAdapter.ThumbnailViewHolder>(MediaEntryDiffCallback()) {

    private var selectionMode = false
    private val selectedItems = mutableSetOf<String>() // Use URI strings for comparison

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        notifyDataSetChanged()
    }

    fun updateSelection(selected: Set<MediaEntry>) {
        selectedItems.clear()
        selectedItems.addAll(selected.map { it.uri.toString() })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ItemMediaThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThumbnailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ThumbnailViewHolder(
        private val binding: ItemMediaThumbnailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaEntry) {
            val isSelected = selectedItems.contains(item.uri.toString())

            // Load thumbnail
            Glide.with(binding.root.context)
                .load(item.uri)
                .centerCrop()
                .placeholder(R.drawable.ic_video_placeholder)
                .into(binding.ivThumbnail)

            // Show file size
            binding.tvFileSize.text = item.getFormattedSize()

            // Show video duration if applicable
            if (item.isVideo && item.durationMs != null) {
                binding.tvDuration.visibility = View.VISIBLE
                binding.tvDuration.text = formatDuration(item.durationMs)
            } else {
                binding.tvDuration.visibility = View.GONE
            }

            // Update selection UI
            binding.cbSelectItem.isChecked = isSelected
            binding.viewSelectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Update card stroke for selection
            binding.cardMedia.strokeWidth = if (isSelected) 4 else 0

            // Handle card clicks (for preview)
            binding.cardMedia.setOnClickListener {
                if (!selectionMode) {
                    onItemClick(item)  // Navigate to preview
                }
            }

            // Handle long press (enter selection mode)
            binding.cardMedia.setOnLongClickListener {
                if (!selectionMode) {
                    onItemLongClick(item)
                } else {
                    false
                }
            }

            // Handle checkbox click (for selection only)
            binding.cbSelectItem.setOnClickListener {
                toggleSelection(item)
            }
        }

        private fun toggleSelection(item: MediaEntry) {
            val isCurrentlySelected = selectedItems.contains(item.uri.toString())
            val newState = !isCurrentlySelected

            if (newState) {
                selectedItems.add(item.uri.toString())
            } else {
                selectedItems.remove(item.uri.toString())
            }

            onSelectionChanged(item, newState)
            notifyItemChanged(bindingAdapterPosition)
        }

        private fun formatDuration(durationMs: Long): String {
            val seconds = (durationMs / 1000).toInt()
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return String.format(java.util.Locale.US, "%02d:%02d", minutes, remainingSeconds)
        }
    }

    class MediaEntryDiffCallback : DiffUtil.ItemCallback<MediaEntry>() {
        override fun areItemsTheSame(oldItem: MediaEntry, newItem: MediaEntry): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: MediaEntry, newItem: MediaEntry): Boolean {
            return oldItem == newItem
        }
    }
}

