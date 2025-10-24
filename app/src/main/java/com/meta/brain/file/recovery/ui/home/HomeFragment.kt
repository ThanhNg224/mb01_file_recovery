package com.meta.brain.file.recovery.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.ScanConfig
import com.meta.brain.file.recovery.data.model.ScanDepth
import com.meta.brain.file.recovery.data.model.ScanTarget
import com.meta.brain.file.recovery.data.model.MediaScanKind
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.net.toUri

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var mediaRepository: MediaRepository

    private var hasPermissions = false

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }

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

        setupDeepScan()
        setupExistingViews()
        checkPermissions()

        // Help button shows help
        binding.btnHelp.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Help coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
        // Setting button navigates to settings
        binding.btnSetting.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_setting)
        }
    }

    private fun setupDeepScan() {
        // Setup Deep Scan button - scans all media types
        binding.btnDeepScan.setOnClickListener {
            if (hasPermissions) {
                navigateToScanLoading(ScanDepth.DEEP, MediaScanKind.ALL)
            } else {
                requestPermissions()
            }
        }

        // Setup Archive button
        binding.btnArchive.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_archive)
        }
    }

    private fun navigateToScanLoading(depth: ScanDepth, mediaKind: MediaScanKind = MediaScanKind.ALL) {
        val selectedTarget = ScanTarget.ALL

        // Check for MANAGE_EXTERNAL_STORAGE permission if scanning documents on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkManageStoragePermission()) {
            showManageStoragePermissionDialog()
            return
        }

        val minDurationMs = 4000L

        val scanConfig = ScanConfig(
            target = selectedTarget,
            depth = depth,
            mediaKind = mediaKind,
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
        // Set up click handlers for the four main scan tiles
        // Each tile now scans only its specific media type
        binding.tilePhoto.setOnClickListener {
            if (hasPermissions) {
                navigateToScanLoading(ScanDepth.NORMAL, MediaScanKind.IMAGE)
            } else {
                requestPermissions()
            }
        }
        binding.tileVideo.setOnClickListener {
            if (hasPermissions) {
                navigateToScanLoading(ScanDepth.VIDEO, MediaScanKind.VIDEO)
            } else {
                requestPermissions()
            }
        }
        binding.tileAudio.setOnClickListener {
            if (hasPermissions) {
                navigateToScanLoading(ScanDepth.AUDIO, MediaScanKind.AUDIO)
            } else {
                requestPermissions()
            }
        }
        binding.tileOther.setOnClickListener {
            if (hasPermissions) {
                navigateToScanLoading(ScanDepth.OTHER, MediaScanKind.OTHER)
            } else {
                requestPermissions()
            }
        }

        // Ads button and banner
        binding.btnAds.setOnClickListener { toast(getString(R.string.home_ads)) }
        binding.adsBanner.setOnClickListener { toast(getString(R.string.home_ads)) }
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

    private fun toast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

