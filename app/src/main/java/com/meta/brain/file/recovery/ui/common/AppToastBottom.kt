package com.meta.brain.file.recovery.ui.common

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.LayoutAppToastBottomBinding
import java.lang.ref.WeakReference

/**
 * Custom bottom toast component with smooth animations.
 * Displays a floating message above the bottom navigation bar.
 */
object AppToastBottom {

    private var currentToast: WeakReference<View>? = null

    /**
     * Shows a bottom toast with the given message.
     *
     * @param activity The activity context
     * @param message The message to display
     * @param iconRes Optional icon resource (defaults to check circle)
     * @param duration How long to show the toast in milliseconds (default 2000ms)
     */
    fun show(
        activity: Activity,
        message: String,
        @DrawableRes iconRes: Int = R.drawable.ic_check_circle,
        duration: Long = 2000L
    ) {
        // Remove any existing toast first
        currentToast?.get()?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }

        // Inflate the toast layout
        val binding = LayoutAppToastBottomBinding.inflate(LayoutInflater.from(activity))
        binding.tvToastMessage.text = message
        binding.ivToastIcon.setImageResource(iconRes)

        val toastView = binding.root

        // Add to decor view
        val decorView = activity.window.decorView as ViewGroup
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = getBottomMargin(activity)
        }

        // Initially invisible and translated down
        toastView.alpha = 0f
        toastView.translationY = 100f

        // Add to view hierarchy
        decorView.addView(toastView, layoutParams)
        currentToast = WeakReference(toastView)

        // Animate in
        toastView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // After showing, wait for duration then animate out
                toastView.postDelayed({
                    animateOut(toastView, decorView)
                }, duration)
            }
            .start()
    }

    /**
     * Animates the toast out and removes it from the view hierarchy.
     */
    private fun animateOut(toastView: View, parent: ViewGroup) {
        toastView.animate()
            .alpha(0f)
            .translationY(100f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                parent.removeView(toastView)
                if (currentToast?.get() == toastView) {
                    currentToast = null
                }
            }
            .start()
    }

    /**
     * Calculate bottom margin to float above bottom navigation if present.
     * Defaults to 80dp to accommodate most bottom nav bars.
     */
    private fun getBottomMargin(activity: Activity): Int {
        val density = activity.resources.displayMetrics.density
        // 80dp in pixels - adjust if you have a specific bottom nav height
        return (80 * density).toInt()
    }
}

