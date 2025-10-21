package com.meta.brain.file.recovery.ui.preview

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.data.model.MediaKind

/**
 * Adapter for ViewPager2 to display media preview pages
 */
class PreviewPagerAdapter(
    fragment: Fragment,
    private val mediaItems: MutableList<MediaEntry>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = mediaItems.size

    override fun createFragment(position: Int): Fragment {
        val mediaEntry = mediaItems[position]
        return when (mediaEntry.mediaKind) {
            MediaKind.IMAGE -> PreviewImageFragment.newInstance(mediaEntry)
            MediaKind.VIDEO -> PreviewVideoFragment.newInstance(mediaEntry)
            MediaKind.DOCUMENT, MediaKind.AUDIO, MediaKind.OTHER ->
                PreviewDocumentFragment.newInstance(mediaEntry)
        }
    }

    fun getMediaEntry(position: Int): MediaEntry? = mediaItems.getOrNull(position)

    //Thêm hàm update an toàn khi xóa
    fun removeItemAt(position: Int) {
        if (position in mediaItems.indices) {
            mediaItems.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, mediaItems.size)
        }
    }

    fun updateData(newItems: List<MediaEntry>) {
        mediaItems.clear()
        mediaItems.addAll(newItems)
        notifyDataSetChanged()
    }
}


