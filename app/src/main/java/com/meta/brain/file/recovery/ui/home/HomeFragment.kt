package com.meta.brain.file.recovery.ui.home

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.data.model.ScanDepth
import com.meta.brain.file.recovery.data.model.MediaScanKind
import com.meta.brain.file.recovery.databinding.FragmentHomeBinding
import com.meta.brain.file.recovery.ui.common.showPermissionDialog
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

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

        setupViews()
        observeViewModel()
        checkPermissions()
    }

    private fun setupViews() {
        // Set up click handlers for the four main scan tiles
        binding.tilePhoto.setOnClickListener {
            viewModel.onTileClicked(ScanDepth.NORMAL, MediaScanKind.IMAGE)
        }
        binding.tileVideo.setOnClickListener {
            viewModel.onTileClicked(ScanDepth.VIDEO, MediaScanKind.VIDEO)
        }
        binding.tileAudio.setOnClickListener {
            viewModel.onTileClicked(ScanDepth.AUDIO, MediaScanKind.AUDIO)
        }
        binding.tileOther.setOnClickListener {
            viewModel.onTileClicked(ScanDepth.OTHER, MediaScanKind.OTHER)
        }

        // Archive button
        binding.btnArchive.setOnClickListener {
            viewModel.onArchiveClicked()
        }

        // Help button
        binding.btnHelp.setOnClickListener {
            viewModel.onHelpClicked()
        }

        // Setting button
        binding.btnSetting.setOnClickListener {
            viewModel.onSettingsClicked()
        }

        // Ads button and banner
        binding.btnAds.setOnClickListener {
            viewModel.onAdsClicked(getString(R.string.home_ads))
        }
        binding.adsBanner.setOnClickListener {
            viewModel.onAdsClicked(getString(R.string.home_ads))
        }
    }

    private fun observeViewModel() {
        // Observe UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.homeUiState.collect { state ->
                handleUiState()
            }
        }

        // Observe one-time UI events
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collect { event ->
                handleUiEvent(event)
            }
        }
    }

    private fun handleUiState() {
    }

    private fun handleUiEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.NavigateToScan -> {
                navigateToScanLoading(event.scanConfig)
            }
            is HomeUiEvent.ShowPermissionDialog -> {
                showPermissionRationale()
            }
            is HomeUiEvent.ShowManageStorageDialog -> {
                showManageStoragePermissionDialog()
            }
            is HomeUiEvent.ShowToast -> {
                showToast(event.message)
            }
            is HomeUiEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
            }
            is HomeUiEvent.NavigateToHelp -> {
                findNavController().navigate(R.id.action_home_to_instruction)
            }
            is HomeUiEvent.NavigateToSettings -> {
                findNavController().navigate(R.id.action_home_to_setting)
            }
            is HomeUiEvent.NavigateToArchive -> {
                findNavController().navigate(R.id.action_home_to_archive)
            }
        }
    }

    private fun navigateToScanLoading(scanConfig: com.meta.brain.file.recovery.data.model.ScanConfig) {
        try {
            val action = HomeFragmentDirections.actionHomeToScanLoading(scanConfig)
            findNavController().navigate(action)
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Navigation error: ${e.message}")
            Snackbar.make(binding.root, "Navigation error occurred", Snackbar.LENGTH_SHORT).show()
        }
    }


    private fun checkPermissions() {
        val permissions = viewModel.getRequiredPermissions()
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
        }

        viewModel.updatePermissions(allGranted)

        // Debug logging
        permissions.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
            android.util.Log.d("HomeFragment", "Permission $permission: $granted")
        }
        android.util.Log.d("HomeFragment", "All permissions granted: $allGranted")

        // Check for MANAGE_EXTERNAL_STORAGE permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val managePermissionGranted = viewModel.checkManageStoragePermission()
            android.util.Log.d("HomeFragment", "MANAGE_EXTERNAL_STORAGE permission granted: $managePermissionGranted")

            if (!managePermissionGranted) {
                viewModel.showManageStoragePermissionDialog()
            }
        }
    }

    private fun showManageStoragePermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showPermissionDialog {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = "package:${requireContext().packageName}".toUri()
                    startActivity(intent)
                } catch (_: Exception) {
                    // Fallback to general settings
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    private fun showPermissionRationale() {
        showPermissionDialog {
            // Open app settings
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            startActivity(intent)
        }
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

