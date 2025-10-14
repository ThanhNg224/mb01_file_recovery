package com.meta.brain.file.recovery.ui.preview

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.FragmentPreviewPageImageBinding

/**
 * Fragment for displaying image preview using PhotoView
 */
class PreviewImageFragment : Fragment() {

    private var _binding: FragmentPreviewPageImageBinding? = null
    private val binding get() = _binding!!

    private var mediaEntry: MediaEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_MEDIA_ENTRY, MediaEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_MEDIA_ENTRY)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewPageImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadImage()
    }

    private fun loadImage() {
        val entry = mediaEntry ?: return

        binding.progressBar.isVisible = true
        binding.tvError.isVisible = false
        binding.photoView.visibility = View.VISIBLE

        Glide.with(this)
            .load(entry.uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(android.R.drawable.ic_menu_report_image)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBar.isVisible = false
                    binding.tvError.isVisible = true
                    binding.photoView.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBar.isVisible = false
                    return false
                }
            })
            .into(binding.photoView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MEDIA_ENTRY = "media_entry"

        fun newInstance(mediaEntry: MediaEntry): PreviewImageFragment {
            return PreviewImageFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MEDIA_ENTRY, mediaEntry)
                }
            }
        }
    }
}
