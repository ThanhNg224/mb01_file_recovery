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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.MediaType
import com.meta.brain.file.recovery.data.repository.MediaRepository
import com.meta.brain.file.recovery.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import androidx.core.net.toUri

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Use activity-scoped ViewModel to share with ResultsFragment
    private val viewModel: HomeViewModel by activityViewModels()

    @Inject
    lateinit var mediaRepository: MediaRepository

    private var hasPermissions = false
    private var hasNavigatedToResults = false // Flag to prevent auto-navigation loop

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

        setupFilters()
        setupQuickScan()
        setupExistingViews()
        observeViewModel()
        checkPermissions()
    }

    private fun setupFilters() {
        // Filter chips for media types
        binding.chipImages.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipVideos.isChecked = false
                binding.chipDocuments.isChecked = false
                binding.chipAudio.isChecked = false
                binding.chipAll.isChecked = false
            }
        }

        binding.chipVideos.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipImages.isChecked = false
                binding.chipDocuments.isChecked = false
                binding.chipAudio.isChecked = false
                binding.chipAll.isChecked = false
            }
        }

        binding.chipDocuments.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipImages.isChecked = false
                binding.chipVideos.isChecked = false
                binding.chipAudio.isChecked = false
                binding.chipAll.isChecked = false
            }
        }

        binding.chipAudio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipImages.isChecked = false
                binding.chipVideos.isChecked = false
                binding.chipDocuments.isChecked = false
                binding.chipAll.isChecked = false
            }
        }

        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipImages.isChecked = false
                binding.chipVideos.isChecked = false
                binding.chipDocuments.isChecked = false
                binding.chipAudio.isChecked = false
            }
        }

        // Default to "All"
        binding.chipAll.isChecked = true
    }

    private fun setupQuickScan() {
        binding.btnQuickScan.setOnClickListener {
            if (hasPermissions) {
                performQuickScan()
            } else {
                requestPermissions()
            }
        }

        // Setup Deep Scan button
        binding.btnDeepScan.setOnClickListener {
            if (hasPermissions) {
                performDeepScan()
            } else {
                requestPermissions()
            }
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                android.util.Log.d("HomeFragment", "UI State changed: $state")
                updateUI(state)

                // Navigate to ResultsFragment when done loading items
                if (state is MediaUiState.Items && state.list.isNotEmpty() && !hasNavigatedToResults) {
                    android.util.Log.d("HomeFragment", "Navigating to results with ${state.list.size} items")
                    hasNavigatedToResults = true // Set the flag to true
                    try {
                        findNavController().navigate(R.id.action_home_to_results)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeFragment", "Navigation error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun updateUI(state: MediaUiState) {
        when (state) {
            is MediaUiState.Idle -> {
            }

            is MediaUiState.Loading -> {

                binding.btnQuickScan.text = "Scanning..."
                binding.btnQuickScan.isEnabled = false
            }

            is MediaUiState.Items -> {
                binding.btnQuickScan.text = if (hasPermissions) "Quick Scan" else "Grant Permissions"
                binding.btnQuickScan.isEnabled = true
            }

            is MediaUiState.Empty -> {
                binding.btnQuickScan.text = if (hasPermissions) "Quick Scan" else "Grant Permissions"
                binding.btnQuickScan.isEnabled = true
                Snackbar.make(binding.root, "No media files found", Snackbar.LENGTH_LONG).show()
            }

            is MediaUiState.Error -> {
                // Reset button and show error
                binding.btnQuickScan.text = if (hasPermissions) "Quick Scan" else "Grant Permissions"
                binding.btnQuickScan.isEnabled = true
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun performQuickScan() {
        // Reset navigation flag for new scan
        hasNavigatedToResults = false

        val selectedTypes = when {
            binding.chipImages.isChecked -> setOf(MediaType.IMAGES)
            binding.chipVideos.isChecked -> setOf(MediaType.VIDEOS)
            binding.chipAudio.isChecked -> setOf(MediaType.AUDIO)
            binding.chipDocuments.isChecked -> setOf(MediaType.DOCUMENTS)
            else -> setOf(MediaType.ALL)
        }

        // Check for MANAGE_EXTERNAL_STORAGE permission if scanning documents on Android 11+
        if (selectedTypes.contains(MediaType.DOCUMENTS) || selectedTypes.contains(MediaType.ALL)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkManageStoragePermission()) {
                showManageStoragePermissionDialog()
                return
            }
        }

        val minSize = when (binding.spinnerMinSize.selectedItemPosition) {
            1 -> 50L * 1024 // 50KB
            2 -> 1024L * 1024 // 1MB
            else -> null // No minimum
        }

        // Last 12 months by default
        val calendar = Calendar.getInstance()
        val toSec = calendar.timeInMillis / 1000
        calendar.add(Calendar.MONTH, -12)
        val fromSec = calendar.timeInMillis / 1000

        viewModel.quickScan(
            types = selectedTypes,
            minSize = minSize,
            fromSec = fromSec,
            toSec = toSec
        )
    }

    private fun performDeepScan() {
        // Reset navigation flag for new scan
        hasNavigatedToResults = false

        val selectedTypes = when {
            binding.chipImages.isChecked -> setOf(MediaType.IMAGES)
            binding.chipVideos.isChecked -> setOf(MediaType.VIDEOS)
            binding.chipAudio.isChecked -> setOf(MediaType.AUDIO)
            binding.chipDocuments.isChecked -> setOf(MediaType.DOCUMENTS)
            else -> setOf(MediaType.ALL)
        }

        // Check for MANAGE_EXTERNAL_STORAGE permission if scanning documents on Android 11+
        if (selectedTypes.contains(MediaType.DOCUMENTS) || selectedTypes.contains(MediaType.ALL)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkManageStoragePermission()) {
                showManageStoragePermissionDialog()
                return
            }
        }

        val minSize = when (binding.spinnerMinSize.selectedItemPosition) {
            1 -> 50L * 1024 // 50KB
            2 -> 1024L * 1024 // 1MB
            else -> null // No minimum
        }

        // Last 12 months by default
        val calendar = Calendar.getInstance()
        val toSec = calendar.timeInMillis / 1000
        calendar.add(Calendar.MONTH, -12)
        val fromSec = calendar.timeInMillis / 1000

        viewModel.deepScan(
            types = selectedTypes,
            minSize = minSize,
            fromSec = fromSec,
            toSec = toSec
        )
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
            true // Not needed for older versions
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
            val (pkg, type, name) = cleaned.split(":")
            resources.getIdentifier(name, type, if (pkg == "android") null else requireContext().packageName)
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
