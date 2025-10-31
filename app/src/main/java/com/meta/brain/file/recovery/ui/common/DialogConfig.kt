package com.meta.brain.file.recovery.ui.common

import android.os.Parcelable
import com.meta.brain.file.recovery.R
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Configuration data class for AppDialogFragment.
 *
 * @property iconRes Resource ID for the dialog icon (default: attention icon)
 * @property title Dialog title text (optional)
 * @property message Dialog message text (optional)
 * @property highlightText Text within message to highlight in bold and black (optional)
 * @property positiveText Text for the primary/positive button (default: "Confirm")
 * @property negativeText Text for the secondary/negative button (optional, hides button if null)
 * @property onPositive Callback when positive button is clicked
 * @property onNegative Callback when negative button is clicked
 */
@Parcelize
data class DialogConfig(
    val iconRes: Int? = R.drawable.ic_attention,
    val title: String? = null,
    val message: String? = null,
    val highlightText: String? = null,
    val positiveText: String = "Confirm",
    val negativeText: String? = null,
    val onPositive: @RawValue (() -> Unit)? = null,
    val onNegative: @RawValue (() -> Unit)? = null
) : Parcelable

