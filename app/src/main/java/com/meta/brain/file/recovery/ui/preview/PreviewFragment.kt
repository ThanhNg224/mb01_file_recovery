package com.meta.brain.file.recovery.ui.preview

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.BottomSheetInfoBinding
import com.meta.brain.file.recovery.databinding.FragmentPreviewBinding
import com.meta.brain.file.recovery.ui.results.ResultsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Full-screen preview fragment with ViewPager2
 * Supports image, video, and document preview
 */
@AndroidEntryPoint
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val args: PreviewFragmentArgs by navArgs()
    private val resultsViewModel: ResultsViewModel by activityViewModels()

    private lateinit var pagerAdapter: PreviewPagerAdapter
    private var visibleItems: ArrayList<MediaEntry> = arrayListOf()
    private var currentPosition: Int = 0
    private var isChromeVisible = true

    private var currentVideoFragment: PreviewVideoFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        visibleItems = args.visibleItems.toCollection(ArrayList())
        currentPosition = args.startIndex

        setupImmersiveMode()
        setupToolbar()
        setupViewPager()
        setupClickToToggleChrome()

        // Handle back press
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().navigateUp()
        }
    }

    private fun setupImmersiveMode() {
        val window = requireActivity().window
        val decorView = window.decorView

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, decorView)

        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Set navigation icon tint to white for visibility on dark background
        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(requireContext(), R.color.white))

        // Hide restore button if opened from Archive
        if (args.fromArchive) {
            binding.toolbar.menu.findItem(R.id.action_restore)?.isVisible = false
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            handleMenuItemClick(menuItem)
        }

        updateToolbarTitle()
    }

    private fun setupViewPager() {
        pagerAdapter = PreviewPagerAdapter(this, visibleItems)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setCurrentItem(currentPosition, false)
        binding.viewPager.offscreenPageLimit = 1 // Preload ±1 pages

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                updateToolbarTitle()
                updateBottomInfo()

                // Pause previous video if any
                currentVideoFragment?.pauseVideo()
                currentVideoFragment = null

                // Get current fragment if it's a video
                val fragment = childFragmentManager.fragments.find {
                    it is PreviewVideoFragment && it.isVisible
                } as? PreviewVideoFragment
                currentVideoFragment = fragment
            }
        })

        updateBottomInfo()
        preloadAdjacentPages()
    }

    private fun setupClickToToggleChrome() {
        binding.viewPager.setOnClickListener {
            toggleChrome()
        }
    }

    private fun toggleChrome() {
        isChromeVisible = !isChromeVisible

        binding.appBarLayout.animate()
            .alpha(if (isChromeVisible) 1f else 0f)
            .translationY(if (isChromeVisible) 0f else -binding.appBarLayout.height.toFloat())
            .setDuration(200)
            .withStartAction {
                if (isChromeVisible) binding.appBarLayout.isVisible = true
            }
            .withEndAction {
                if (!isChromeVisible) binding.appBarLayout.isVisible = false
            }
            .start()

        binding.bottomInfoBar.animate()
            .alpha(if (isChromeVisible) 1f else 0f)
            .translationY(if (isChromeVisible) 0f else binding.bottomInfoBar.height.toFloat())
            .setDuration(200)
            .withStartAction {
                if (isChromeVisible) binding.bottomInfoBar.isVisible = true
            }
            .withEndAction {
                if (!isChromeVisible) binding.bottomInfoBar.isVisible = false
            }
            .start()
    }

    private fun updateToolbarTitle() {
        val total = visibleItems.size
        binding.toolbar.title = "${currentPosition + 1} / $total"
    }

    private fun updateBottomInfo() {
        val entry = pagerAdapter.getMediaEntry(currentPosition) ?: return
        binding.tvFileName.text = entry.displayName ?: "Unknown"
        val fileInfo = "${entry.getFormattedSize()} • ${entry.getFormattedDate()}"
        binding.tvFileInfo.text = fileInfo
    }

    private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
        val entry = pagerAdapter.getMediaEntry(currentPosition) ?: return false

        return when (menuItem.itemId) {
            R.id.action_info -> {
                showInfoBottomSheet(entry)
                true
            }
            R.id.action_restore -> {
                restoreSingleFile(entry)
                true
            }
            R.id.action_delete -> {
                deleteSingleFile(entry)
                true
            }
            R.id.action_share -> {
                shareFile(entry)
                true
            }
            R.id.action_open_with -> {
                openWithExternalApp(entry)
                true
            }
            else -> false
        }
    }

    private fun showInfoBottomSheet(entry: MediaEntry) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetInfoBinding.inflate(layoutInflater)

        sheetBinding.tvInfoName.text = entry.displayName ?: "Unknown"
        sheetBinding.tvInfoType.text = entry.mimeType ?: "Unknown"
        sheetBinding.tvInfoSize.text = entry.getFormattedSize()
        sheetBinding.tvInfoDate.text = entry.getFormattedDate()

        // Show resolution for images/videos (if available)
        if (entry.mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.IMAGE ||
            entry.mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.VIDEO) {
            // TODO: Get actual resolution from ContentResolver
            sheetBinding.llResolution.isVisible = false
        }

        // Show duration for videos
        if (entry.durationMs != null && entry.durationMs > 0) {
            sheetBinding.llDuration.isVisible = true
            val duration = formatDuration(entry.durationMs)
            sheetBinding.tvInfoDuration.text = duration
        }

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetBinding.root)

        // Make the bottom sheet expand fully when opened
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        dialog.show()
    }

    private fun restoreSingleFile(entry: MediaEntry) {
        resultsViewModel.enterSelectionMode(entry)
        resultsViewModel.startRestore()

        Snackbar.make(binding.root, "Restoring ${entry.displayName}...", Snackbar.LENGTH_SHORT).show()
    }

    private fun deleteSingleFile(entry: MediaEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete file?")
            .setMessage("This will permanently delete ${entry.displayName}. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete(entry: MediaEntry) {
        try {
            val deleted = requireContext().contentResolver.delete(entry.uri, null, null)
            if (deleted > 0) {
                // Set result for ArchiveFragment or ResultsFragment to refresh
                if (args.fromArchive) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("files_changed", true)
                } else {
                    // From Results fragment
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("results_files_changed", true)
                }

                Snackbar.make(binding.root, "File deleted", Snackbar.LENGTH_SHORT).show()

                // Always navigate back after deletion to refresh the list
                binding.viewPager.postDelayed({
                    findNavController().navigateUp()
                }, 500) // Small delay to show the snackbar message
            } else {
                Snackbar.make(binding.root, "Failed to delete file", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(entry: MediaEntry) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, entry.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (_: Exception) {
            Snackbar.make(binding.root, "Failed to share file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openWithExternalApp(entry: MediaEntry) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(entry.uri, entry.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            Snackbar.make(binding.root, "No app found to open this file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun preloadAdjacentPages() {
        val prevIndex = currentPosition - 1
        val nextIndex = currentPosition + 1

        if (prevIndex >= 0) {
            preloadImage(visibleItems[prevIndex])
        }
        if (nextIndex < visibleItems.size) {
            preloadImage(visibleItems[nextIndex])
        }
    }

    private fun preloadImage(entry: MediaEntry) {
        if (entry.mediaKind == com.meta.brain.file.recovery.data.model.MediaKind.IMAGE) {
            Glide.with(this).load(entry.uri).preload()
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    override fun onPause() {
        super.onPause()
        currentVideoFragment?.pauseVideo()
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentVideoFragment = null
        _binding = null
    }
}
