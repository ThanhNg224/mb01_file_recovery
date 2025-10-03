package com.meta.brain.file.recovery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.meta.brain.file.recovery.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBottomNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setupWithNavController(navController)


        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideOn = setOf(R.id.introFragment)
            val shouldShow = destination.id !in hideOn

            // Animate bottom navigation visibility changes
            if (shouldShow && !binding.bottomNav.isVisible) {
                binding.bottomNav.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withStartAction { binding.bottomNav.isVisible = true }
                    .start()
            } else if (!shouldShow && binding.bottomNav.isVisible) {
                binding.bottomNav.animate()
                    .translationY(binding.bottomNav.height.toFloat())
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction { binding.bottomNav.isVisible = false }
                    .start()
            }
        }


        binding.bottomNav.setOnItemReselectedListener {

            binding.bottomNav.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
