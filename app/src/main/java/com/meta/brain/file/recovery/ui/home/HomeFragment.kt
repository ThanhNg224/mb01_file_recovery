package com.meta.brain.file.recovery.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.ScanConfig
import com.meta.brain.file.recovery.data.model.ScanDepth
import com.meta.brain.file.recovery.data.model.ScanTarget
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.net.toUri

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Use activity-scoped ViewModel to share with ResultsFragment
    // private val viewModel: HomeViewModel by activityViewModels()

    @Inject
    lateinit var mediaRepository: MediaRepository

    private var hasPermissions = false

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        updateQuickScanButton()

        if (!hasPermissions) {
            showPermissionRationale()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        setupFilters()
        setupQuickScan()
        setupExistingViews()
        checkPermissions()
    }

//    private fun setupFilters() {
//        // Filter chips for media types
//        binding.chipImages.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                binding.chipVideos.isChecked = false
//                binding.chipDocuments.isChecked = false
//                binding.chipAudio.isChecked = false
//                binding.chipAll.isChecked = false
//            }
//        }
//
//        binding.chipVideos.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                binding.chipImages.isChecked = false
//                binding.chipDocuments.isChecked = false
//                binding.chipAudio.isChecked = false
//                binding.chipAll.isChecked = false
//            }
//        }
//
//        binding.chipDocuments.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                binding.chipImages.isChecked = false
//                binding.chipVideos.isChecked = false
//                binding.chipAudio.isChecked = false
//                binding.chipAll.isChecked = false
//            }
//        }
//
//        binding.chipAudio.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                binding.chipImages.isChecked = false
//                binding.chipVideos.isChecked = false
//                binding.chipDocuments.isChecked = false
//                binding.chipAll.isChecked = false
//            }
//        }
//
//        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                binding.chipImages.isChecked = false
//                binding.chipVideos.isChecked = false
//                binding.chipDocuments.isChecked = false
//                binding.chipAudio.isChecked = false
//            }
//        }
//
//        // Default to "All"
//        binding.chipAll.isChecked = true
//    }

    private fun setupQuickScan() {
        binding.btnQuickScan.setOnClickListener {
            if (hasPermissions) {
                // Always scan for all file types and any size in Quick Scan
                navigateToScanLoading(ScanDepth.QUICK, forceAll = true)
            } else {
                requestPermissions()
            }
        }

        // Setup Deep Scan button
        binding.btnDeepScan.setOnClickListener {
            if (hasPermissions) {
                navigateToScanLoading(ScanDepth.DEEP)
            } else {
                requestPermissions()
            }
        }
    }

    // Modified to accept forceAll for Quick Scan
    private fun navigateToScanLoading(depth: ScanDepth, forceAll: Boolean = false) {
        val selectedTarget = if (forceAll && depth == ScanDepth.QUICK) {
            ScanTarget.ALL
        } else {
            when {
//                binding.chipImages.isChecked -> ScanTarget.PHOTOS
//                binding.chipVideos.isChecked -> ScanTarget.VIDEOS
//                binding.chipAudio.isChecked -> ScanTarget.AUDIO
//                binding.chipDocuments.isChecked -> ScanTarget.DOCUMENTS
                else -> ScanTarget.ALL
            }
        }

        // Check for MANAGE_EXTERNAL_STORAGE permission if scanning documents on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkManageStoragePermission()) {
            showManageStoragePermissionDialog()
            return
        }


        // Scan ALL files regardless of date

        val minDurationMs = when (depth) {
            ScanDepth.QUICK -> 2500L
            ScanDepth.DEEP -> 4000L
        }

        val scanConfig = ScanConfig(
            target = selectedTarget,
            depth = depth,
            minDurationMs = minDurationMs,
            fromSec = null,
            toSec = null
        )

        try {
            val action = HomeFragmentDirections.actionHomeToScanLoading(scanConfig)
            findNavController().navigate(action)
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Navigation error: ${e.message}")
            Snackbar.make(binding.root, "Navigation error occurred", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupExistingViews() {
        setupIncludeTile(binding.tileAudio.root)
        setupIncludeTile(binding.tileContacts.root)
        setupIncludeTile(binding.tileDocs.root)
        setupIncludeTile(binding.tileSms.root)

        // Existing click handlers
        binding.tilePhoto.setOnClickListener { toast("Khôi phục ảnh") }
        binding.tileVideo.setOnClickListener { toast("Khôi phục video") }
        binding.tileRecovered.setOnClickListener { toast("Đã khôi phục") }
        binding.tileVault.setOnClickListener { toast("Kho lưu trữ") }
        binding.btnHelp.setOnClickListener { toast("Help") }
        binding.btnAds.setOnClickListener { toast("Open Ads") }
        binding.adsBanner.setOnClickListener { toast("ADS area") }
    }

    private fun checkPermissions() {
        val permissions = getRequiredPermissions()
        hasPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
        }

        // Debug logging to see permission status
        permissions.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
            android.util.Log.d("HomeFragment", "Permission $permission: $granted")
        }

        android.util.Log.d("HomeFragment", "All permissions granted: $hasPermissions")
        updateQuickScanButton()

        // Check for MANAGE_EXTERNAL_STORAGE permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val managePermissionGranted = android.os.Environment.isExternalStorageManager()
            android.util.Log.d("HomeFragment", "MANAGE_EXTERNAL_STORAGE permission granted: $managePermissionGranted")

            if (!managePermissionGranted) {
                // Show dialog to request MANAGE_EXTERNAL_STORAGE permission
                showManageStoragePermissionDialog()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        android.util.Log.d("HomeFragment", "Requesting permissions: ${permissions.joinToString()}")

        // Check if we should show rationale for any permission
        val shouldShowRationale = permissions.any { permission ->
            shouldShowRequestPermissionRationale(permission)
        }

        if (shouldShowRationale) {
            showPermissionRationale()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun checkManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true // Not required below Android 11
        }
    }

    private fun showManageStoragePermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Snackbar.make(
                binding.root,
                "To scan documents, please enable 'All files access' permission",
                Snackbar.LENGTH_LONG
            ).setAction("Settings") {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = "package:${requireContext().packageName}".toUri()
                    startActivity(intent)
                } catch (_: Exception) {
                    // Fallback to general settings
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }.show()
        }
    }

    private fun updateQuickScanButton() {
        binding.btnQuickScan.text = if (hasPermissions) {
            "Quick Scan"
        } else {
            "Grant Permissions"
        }
        binding.btnQuickScan.isEnabled = true
    }

    private fun showPermissionRationale() {
        Snackbar.make(
            binding.root,
            "Media permissions are required to scan for images and videos",
            Snackbar.LENGTH_LONG
        ).setAction("Settings") {
            // Open app settings
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            startActivity(intent)
        }.show()
    }


    private fun setupIncludeTile(v: View) {
        val tag = v.tag?.toString().orEmpty()
        val parts = tag.split("|")
        val title = parts.getOrNull(0) ?: "Title"
        val iconRes = parts.getOrNull(1)?.let { resName ->
            // resName like "@android:drawable/ic_dialog_email"
            val cleaned = resName.removePrefix("@").replace("/", ":")
            val (_, _, _) = cleaned.split(":")
            android.R.drawable.ic_menu_help // fallback to a static icon
        } ?: android.R.drawable.ic_menu_help

        v.findViewById<TextView>(R.id.tvTitle).text = title
        v.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(
            ContextCompat.getDrawable(requireContext(), iconRes)
        )

        v.setOnClickListener { toast(title) }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
