package com.meta.brain.file.recovery.ui.home.adapter

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.LruCache

/**
 * Adapter for displaying media items in a grid layout with selection support
 */
class MediaAdapter(
    private val mediaRepository: MediaRepository,
    private val onItemClick: (MediaEntry) -> Unit,
    private val onItemLongClick: ((MediaEntry) -> Boolean)? = null
) : ListAdapter<MediaEntry, MediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    // LRU cache for thumbnails (10MB max)
    private val thumbnailCache = LruCache<String, Bitmap>(10 * 1024 * 1024)

    // Selection mode state
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<MediaEntry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Handle partial updates for selection changes
            if (payloads.contains(PAYLOAD_SELECTION)) {
                holder.updateSelection(getItem(position))
            }
        }
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelThumbnailJob()
    }

    /**
     * Set selection mode and update UI
     */
    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedItems.clear()
            }
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    /**
     * Update selected items (for efficient updates from ViewModel)
     */
    fun updateSelection(selected: Set<MediaEntry>) {
        val oldSelection = selectedItems.toSet()
        selectedItems.clear()
        selectedItems.addAll(selected)

        // Only update changed items
        currentList.forEachIndexed { index, item ->
            val wasSelected = oldSelection.contains(item)
            val isSelected = selectedItems.contains(item)
            if (wasSelected != isSelected) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImage: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val typeBadge: TextView = itemView.findViewById(R.id.tvTypeBadge)
        private val sizeText: TextView = itemView.findViewById(R.id.tvSize)
        private val dateText: TextView = itemView.findViewById(R.id.tvDate)
        private val durationText: TextView = itemView.findViewById(R.id.tvDuration)
        private val selectionOverlay: View = itemView.findViewById(R.id.vSelectionOverlay)
        private val selectionCheckbox: CheckBox = itemView.findViewById(R.id.cbSelection)

        private var thumbnailJob: Job? = null
        private var currentItem: MediaEntry? = null

        fun bind(item: MediaEntry) {
            currentItem = item

            // Cancel any existing thumbnail job
            cancelThumbnailJob()

            // Set basic info based on MediaKind
            typeBadge.text = when (item.mediaKind) {
                com.meta.brain.file.recovery.data.model.MediaKind.IMAGE -> "IMG"
                com.meta.brain.file.recovery.data.model.MediaKind.VIDEO -> "VID"
                com.meta.brain.file.recovery.data.model.MediaKind.DOCUMENT -> "DOC"
                com.meta.brain.file.recovery.data.model.MediaKind.AUDIO -> "AUD"
                com.meta.brain.file.recovery.data.model.MediaKind.OTHER -> "FILE"
            }

            sizeText.text = item.getFormattedSize()
            dateText.text = item.getFormattedDate()

            // Show duration for videos
            if (item.isVideo && item.durationMs != null) {
                durationText.visibility = View.VISIBLE
                durationText.text = formatDuration(item.durationMs)
            } else {
                durationText.visibility = View.GONE
            }

            // Load thumbnail or show placeholder based on MediaKind
            if (item.mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.IMAGE ||
                item.mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.VIDEO) {
                loadThumbnail(item)
            } else {
                // Show document/file placeholder
                thumbnailImage.setImageResource(R.drawable.ic_document_placeholder)
            }

            // Update selection state
            updateSelection(item)

            // Set click listeners
            itemView.setOnClickListener { onItemClick(item) }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(item) ?: false
            }
        }

        fun updateSelection(item: MediaEntry) {
            val isSelected = selectedItems.contains(item)

            if (isSelectionMode) {
                selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                selectionCheckbox.visibility = View.VISIBLE
                selectionCheckbox.isChecked = isSelected
            } else {
                selectionOverlay.visibility = View.GONE
                selectionCheckbox.visibility = View.GONE
            }
        }

        private fun loadThumbnail(item: MediaEntry) {
            val cacheKey = item.uri.toString()

            // Check cache first
            thumbnailCache.get(cacheKey)?.let { cached ->
                thumbnailImage.setImageBitmap(cached)
                return
            }

            // Set placeholder
            thumbnailImage.setImageResource(
                if (item.isVideo) R.drawable.ic_video_placeholder
                else R.drawable.ic_image_placeholder
            )

            // Load asynchronously
            thumbnailJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        mediaRepository.loadThumbnail(item.uri, 200, 200)
                    }

                    bitmap?.let {
                        // Cache the bitmap
                        thumbnailCache.put(cacheKey, it)
                        // Set to ImageView
                        thumbnailImage.setImageBitmap(it)
                    }
                } catch (e: Exception) {
                    // errors
                }
            }
        }

        fun cancelThumbnailJob() {
            thumbnailJob?.cancel()
            thumbnailJob = null
        }

        private fun formatDuration(durationMs: Long): String {
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return if (minutes > 0) {
                "${minutes}:${String.format("%02d", remainingSeconds)}"
            } else {
                "${remainingSeconds}s"
            }
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection_changed"
    }
}

/**
 * DiffUtil callback for efficient list updates
 */
class MediaDiffCallback : DiffUtil.ItemCallback<MediaEntry>() {
    override fun areItemsTheSame(oldItem: MediaEntry, newItem: MediaEntry): Boolean {
        return oldItem.uri == newItem.uri
    }

    override fun areContentsTheSame(oldItem: MediaEntry, newItem: MediaEntry): Boolean {
        return oldItem == newItem
    }
}
