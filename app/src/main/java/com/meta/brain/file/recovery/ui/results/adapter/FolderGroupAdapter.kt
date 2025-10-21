package com.meta.brain.file.recovery.ui.results.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaGroup
import com.meta.brain.file.recovery.data.model.MediaKind

/**
 * Adapter for displaying folder groups in ResultsFragment
 */
class FolderGroupAdapter(
    private val onGroupClick: (MediaGroup) -> Unit
) : ListAdapter<MediaGroup, FolderGroupAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFolderName: TextView = itemView.findViewById(R.id.tvFolderName)
        private val tvFileCount: TextView = itemView.findViewById(R.id.tvFileCount)
        private val ivPreview1: ImageView = itemView.findViewById(R.id.ivPreview1)
        private val ivPreview2: ImageView = itemView.findViewById(R.id.ivPreview2)
        private val ivPreview3: ImageView = itemView.findViewById(R.id.ivPreview3)

        fun bind(group: MediaGroup) {
            tvFolderName.text = group.folderName
            tvFileCount.text = "${group.fileCount} files · ${group.getFormattedSize()}"

            // Load preview thumbnails
            val previews = group.previewItems.take(3)
            loadPreview(ivPreview1, previews.getOrNull(0))
            loadPreview(ivPreview2, previews.getOrNull(1))
            loadPreview(ivPreview3, previews.getOrNull(2))

            itemView.setOnClickListener {
                onGroupClick(group)
            }
        }

        private fun loadPreview(imageView: ImageView, mediaEntry: com.meta.brain.file.recovery.data.model.MediaEntry?) {
            if (mediaEntry == null) {
                imageView.visibility = View.GONE
                return
            }

            imageView.visibility = View.VISIBLE

            when (mediaEntry.mediaKind) {
                MediaKind.IMAGE, MediaKind.VIDEO -> {
                    Glide.with(imageView.context)
                        .load(mediaEntry.uri)
                        .thumbnail(0.1f)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(imageView)
                }
                MediaKind.DOCUMENT -> {
                    imageView.setImageResource(R.drawable.ic_document_placeholder)
                }
                MediaKind.AUDIO -> {
                    imageView.setImageResource(R.drawable.ic_audio_placeholder)
                }
                else -> {
                    imageView.setImageResource(R.drawable.ic_file_placeholder)
                }
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<MediaGroup>() {
        override fun areItemsTheSame(oldItem: MediaGroup, newItem: MediaGroup): Boolean {
            return oldItem.folderName == newItem.folderName
        }

        override fun areContentsTheSame(oldItem: MediaGroup, newItem: MediaGroup): Boolean {
            return oldItem == newItem
        }
    }
}

