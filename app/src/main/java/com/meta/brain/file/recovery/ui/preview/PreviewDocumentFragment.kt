package com.meta.brain.file.recovery.ui.preview

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.FragmentPreviewPageDocumentBinding

/**
 * Fragment for displaying document preview
 * For PDF: uses PdfRenderer (future implementation)
 * For others: shows "Open with" option
 */
class PreviewDocumentFragment : Fragment() {

    private var _binding: FragmentPreviewPageDocumentBinding? = null
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
        _binding = FragmentPreviewPageDocumentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDocumentView()
    }

    private fun setupDocumentView() {
        val entry = mediaEntry ?: return

        binding.openWithContainer.isVisible = true
        binding.scrollView.isVisible = false

        binding.tvDocumentName.text = entry.displayName ?: "Unknown"
        binding.tvDocumentType.text = entry.mimeType ?: "Unknown type"

        binding.btnOpenWith.setOnClickListener {
            openWithExternalApp()
        }

        // TODO: Future implementation for PDF rendering
        // if (entry.mimeType?.contains("pdf") == true) {
        //     renderPdf(entry)
        // }
    }

    private fun openWithExternalApp() {
        val entry = mediaEntry ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(entry.uri, entry.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            android.util.Log.e("PreviewDocument", "Failed to open file", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MEDIA_ENTRY = "media_entry"

        fun newInstance(mediaEntry: MediaEntry): PreviewDocumentFragment {
            return PreviewDocumentFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MEDIA_ENTRY, mediaEntry)
                }
            }
        }
    }
}
