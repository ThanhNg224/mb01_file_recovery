package com.meta.brain.file.recovery.ui.common

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.meta.brain.file.recovery.R

/**
 * Extension functions and helper methods for showing AppDialogFragment easily.
 */

/**
 * Shows an exit confirmation dialog with "Stay" and "Cancel" options.
 */
fun Fragment.showExitDialog(onConfirmExit: () -> Unit) {
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_attention,
            title = getString(R.string.dialog_attention),
            message = getString(R.string.dialog_exit_message),
            positiveText = getString(R.string.dialog_stay),
            negativeText = getString(R.string.dialog_cancel),
            onPositive = { /* User wants to stay, do nothing */ },
            onNegative = onConfirmExit
        )
    ).show(childFragmentManager, "exit_dialog")
}

/**
 * Shows a delete confirmation dialog with highlight on the number of items.
 */
fun Fragment.showDeleteDialog(
    itemCount: Int,
    itemType: String = "files",
    onConfirmDelete: () -> Unit
) {
    val countText = "$itemCount"
    val message = if (itemType == "photos") {
        "Do you want to delete these $countText photos?\n${getString(R.string.dialog_delete_message)}"
    } else {
        "Do you want to delete these $countText files?\n${getString(R.string.dialog_delete_message)}"
    }

    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_attention,
            title = getString(R.string.dialog_attention),
            message = message,
            highlightText = countText,
            positiveText = getString(R.string.dialog_confirm),
            negativeText = getString(R.string.dialog_cancel),
            onPositive = onConfirmDelete
        )
    ).show(childFragmentManager, "delete_dialog")
}

/**
    * Shows a permission request dialog.
 */
fun Fragment.showPermissionDialog(
    onGrantPermission: () -> Unit
) {
    val message = getString(R.string.dialog_permission_message)
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_lock,
            title = "Enable access!",
            message = message,
            positiveText = "Confirm",
            negativeText = null,
            onPositive = onGrantPermission
        )
    ).show(childFragmentManager, "permission_dialog")
}

/**
 * Shows a restore complete dialog.
 */
fun Fragment.showRestoreCompleteDialog(
    itemCount: Int,
    onDismiss: (() -> Unit)? = null
) {
    val countText = "$itemCount"
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = R.drawable.ic_check_circle,
            title = getString(R.string.dialog_restore_complete),
            message = "Successfully restored $countText files.",
            highlightText = countText,
            positiveText = getString(R.string.dialog_ok),
            negativeText = null,
            onPositive = onDismiss
        )
    ).show(childFragmentManager, "restore_complete_dialog")
}

/**
 * Shows a generic information dialog with a single "OK" button.
 */
fun Fragment.showInfoDialog(
    title: String,
    message: String,
    iconRes: Int = R.drawable.ic_info,
    onDismiss: (() -> Unit)? = null
) {
    AppDialogFragment.newInstance(
        DialogConfig(
            iconRes = iconRes,
            title = title,
            message = message,
            positiveText = getString(R.string.dialog_ok),
            negativeText = null,
            onPositive = onDismiss
        )
    ).show(childFragmentManager, "info_dialog")
}

/**
 * Shows a custom dialog with full control over configuration.
 */
fun Fragment.showCustomDialog(
    config: DialogConfig,
    tag: String = "custom_dialog"
) {
    AppDialogFragment.newInstance(config).show(childFragmentManager, tag)
}

/**
 * Extension for FragmentManager to show dialogs from Activities.
 */
fun FragmentManager.showAppDialog(
    config: DialogConfig,
    tag: String = "app_dialog"
) {
    AppDialogFragment.newInstance(config).show(this, tag)
}

