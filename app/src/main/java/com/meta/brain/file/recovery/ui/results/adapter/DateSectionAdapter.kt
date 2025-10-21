package com.meta.brain.file.recovery.ui.results.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.DateSection
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.repository.MediaRepository

/**
 * Adapter for displaying date sections with media items in ResultGroupDetailFragment
 */
class DateSectionAdapter(
    private val mediaRepository: MediaRepository,
    private val onItemClick: (MediaEntry) -> Unit,
    private val onItemLongClick: (MediaEntry) -> Boolean,
    private val onSelectAllClick: (List<MediaEntry>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val sections = mutableListOf<DateSection>()
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<MediaEntry>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_GRID = 1
    }

    fun submitList(newSections: List<DateSection>) {
        sections.clear()
        sections.addAll(newSections)
        notifyDataSetChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun updateSelection(selected: Set<MediaEntry>) {
        selectedItems.clear()
        selectedItems.addAll(selected)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        // Each section has 2 items: header + grid
        return sections.size * 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (position % 2 == 0) VIEW_TYPE_HEADER else VIEW_TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = RecyclerView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                GridViewHolder(view as RecyclerView)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val sectionIndex = position / 2
        val section = sections[sectionIndex]

        when (holder) {
            is HeaderViewHolder -> holder.bind(section)
            is GridViewHolder -> holder.bind(section)
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateSize: TextView = itemView.findViewById(R.id.tvDateSize)
        private val tvSelectAll: TextView = itemView.findViewById(R.id.tvSelectAll)

        fun bind(section: DateSection) {
            tvDateSize.text = "${section.displayDate} · ${section.getFormattedSize()}"

            tvSelectAll.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            tvSelectAll.setOnClickListener {
                onSelectAllClick(section.items)
            }
        }
    }

    inner class GridViewHolder(private val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView) {
        private var gridAdapter: com.meta.brain.file.recovery.ui.home.adapter.MediaAdapter? = null

        fun bind(section: DateSection) {
            if (gridAdapter == null) {
                gridAdapter = com.meta.brain.file.recovery.ui.home.adapter.MediaAdapter(
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick
                )

                recyclerView.apply {
                    layoutManager = GridLayoutManager(context, 3)
                    adapter = gridAdapter
                    isNestedScrollingEnabled = false
                }
            }

            gridAdapter?.setSelectionMode(isSelectionMode)
            gridAdapter?.updateSelection(selectedItems)
            gridAdapter?.submitList(section.items)
        }
    }
}

