package com.meta.brain.file.recovery.ui.common

/**
 * Example usage of AppDialogFragment in various scenarios.
 * This file demonstrates how to use the reusable dialog component.
 */

/*
 * ============================================================================
 * EXAMPLE 1: Exit Confirmation Dialog
 * ============================================================================
 * Use case: User tries to exit from scan results before restoring
 */
fun exampleExitDialog() {
    // Using extension function (simplest way)
    /*
    showExitDialog {
        // User confirmed exit
        findNavController().navigateUp()
    }
    */

    // Using DialogConfig directly
    /*
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_attention,
            title = "Attention",
            message = "Exiting will lose results. Do you want to exit?",
            positiveText = "Stay",
            negativeText = "Cancel",
            onPositive = { /* User stays */ },
            onNegative = {
                // User exits
                findNavController().navigateUp()
            }
        )
    ).show(childFragmentManager, "exit_warning")
    */
}

/*
 * ============================================================================
 * EXAMPLE 2: Delete Confirmation Dialog
 * ============================================================================
 * Use case: User wants to delete 24 selected photos
 */
fun exampleDeleteDialog() {
    // Using extension function with photo count
    /*
    showDeleteDialog(
        itemCount = 24,
        itemType = "photos"
    ) {
        // User confirmed delete
        deleteSelectedFiles()
    }
    */

    // Using DialogConfig directly with highlighted text
    /*
    val photoCount = 24
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_attention,
            title = "Attention",
            message = "Do you want to delete these $photoCount photos?\nThe files will be completely deleted and cannot be recovered.",
            highlightText = "$photoCount", // This text will be highlighted in blue
            positiveText = "Confirm",
            negativeText = "Cancel",
            onPositive = {
                // Delete confirmed
                deleteSelectedFiles()
            }
        )
    ).show(childFragmentManager, "delete_confirmation")
    */
}

/*
 * ============================================================================
 * EXAMPLE 3: Permission Request Dialog
 * ============================================================================
 * Use case: App needs storage permission to continue
 */
fun examplePermissionDialog() {
    // Using extension function
    /*
    showPermissionDialog(
        message = "Storage permission is required to recover deleted files. Please grant permission to continue."
    ) {
        // User agreed to grant permission
        requestStoragePermission()
    }
    */
}

/*
 * ============================================================================
 * EXAMPLE 4: Restore Complete Dialog
 * ============================================================================
 * Use case: Files have been successfully restored
 */
fun exampleRestoreCompleteDialog() {
    // Using extension function
    /*
    showRestoreCompleteDialog(
        itemCount = 15
    ) {
        // Dialog dismissed, navigate to archive
        findNavController().navigate(R.id.action_to_archive)
    }
    */

    // Using DialogConfig with custom success icon
    /*
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_check_circle,
            title = "Restore Complete",
            message = "Successfully restored 15 files to Downloads/RELive/Restored",
            highlightText = "15",
            positiveText = "View Files",
            negativeText = null, // No cancel button
            onPositive = {
                navigateToRestoredFiles()
            }
        )
    ).show(childFragmentManager, "restore_success")
    */
}

/*
 * ============================================================================
 * EXAMPLE 5: Simple Info Dialog (Single Button)
 * ============================================================================
 * Use case: Show informational message with single OK button
 */
fun exampleInfoDialog() {
    // Using extension function
    /*
    showInfoDialog(
        title = "Info",
        message = "Scan completed successfully. Found 120 recoverable files.",
        iconRes = R.drawable.ic_info
    ) {
        // Dialog dismissed
    }
    */
}

/*
 * ============================================================================
 * EXAMPLE 6: Custom Dialog Configuration
 * ============================================================================
 * Use case: Highly customized dialog with specific requirements
 */
fun exampleCustomDialog() {
    /*
    showCustomDialog(
        config = DialogConfig(
            iconRes = R.drawable.ic_restore,
            title = "Deep Scan Required",
            message = "Quick scan found 50 files. Run deep scan to find more recoverable files?\n\nDeep scan may take 5-10 minutes.",
            highlightText = "50 files",
            positiveText = "Start Deep Scan",
            negativeText = "Skip",
            onPositive = {
                startDeepScan()
            },
            onNegative = {
                continueWithQuickScanResults()
            }
        ),
        tag = "deep_scan_prompt"
    )
    */
}

/*
 * ============================================================================
 * EXAMPLE 7: Using from Activity (not Fragment)
 * ============================================================================
 */
fun exampleFromActivity() {
    /*
    // In your Activity:
    supportFragmentManager.showAppDialog(
        config = DialogConfig(
            title = "Welcome",
            message = "Welcome to File Recovery app!",
            positiveText = "Get Started",
            negativeText = null
        ),
        tag = "welcome_dialog"
    )
    */
}

/*
 * ============================================================================
 * EXAMPLE 8: Multiple Highlighted Texts (Advanced)
 * ============================================================================
 * Note: Currently supports single highlight. For multiple highlights,
 * you can extend the createHighlightedText function in AppDialogFragment
 */
fun exampleMultipleHighlights() {
    /*
    // Current limitation: only one highlightText supported
    // To highlight multiple parts, modify the message to emphasize one key metric

    AppDialogFragment.newInstance(
        DialogConfig(
            title = "Scan Complete",
            message = "Found 120 photos, 45 videos, and 30 documents.\nTotal size: 2.5 GB",
            highlightText = "120", // Highlights the first number
            positiveText = "View Results"
        )
    ).show(childFragmentManager, "scan_results")
    */
}

/*
 * ============================================================================
 * QUICK REFERENCE: DialogConfig Parameters
 * ============================================================================
 *
 * @param iconRes: Int? = R.drawable.ic_attention
 *        - Icon shown at top of dialog
 *        - Set to null to hide icon
 *
 * @param title: String? = null
 *        - Bold title text
 *        - Set to null to hide title
 *
 * @param message: String? = null
 *        - Main message text
 *        - Supports multi-line text
 *
 * @param highlightText: String? = null
 *        - Text within message to highlight in blue
 *        - Useful for numbers or key words
 *
 * @param positiveText: String = "Confirm"
 *        - Text for primary (blue) button
 *        - Always visible
 *
 * @param negativeText: String? = null
 *        - Text for secondary (light) button
 *        - Set to null to hide second button
 *
 * @param onPositive: (() -> Unit)? = null
 *        - Callback when primary button clicked
 *        - Dialog auto-dismisses after callback
 *
 * @param onNegative: (() -> Unit)? = null
 *        - Callback when secondary button clicked
 *        - Dialog auto-dismisses after callback
 *
 * ============================================================================
 */

