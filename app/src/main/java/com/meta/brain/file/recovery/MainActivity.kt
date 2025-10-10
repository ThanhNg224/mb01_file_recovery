package com.meta.brain.file.recovery

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.meta.brain.file.recovery.data.repository.MetricsRepository
import com.meta.brain.module.loading.LoadingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {

        private var hasShownLoading = false
    }

    @Inject
    lateinit var metricsRepository: MetricsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Gọi LoadingActivity của module 1 lần khi app mở
        if (!hasShownLoading && savedInstanceState == null) {
            hasShownLoading = true
            startActivity(
                Intent(
                    this,
                    LoadingActivity::class.java
                )

                    .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)

        }

        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
        // Handle insets for bottom nav
        val extraTop = resources.getDimensionPixelSize(R.dimen.bottom_nav_item_offset_top)
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = extraTop, bottom = sys.bottom)
            insets
        }
        bottomNav.clipToPadding = false

        // Set dynamic start destination based on onboarding status
        if (savedInstanceState == null) {
            // Synchronously get onboarding status before setting the graph
            val onboardingDone = kotlinx.coroutines.runBlocking { metricsRepository.onboardingDone.first() }
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (onboardingDone) R.id.homeFragment else R.id.introFragment
            )
            navController.graph = navGraph
            // Immediately trigger destination change logic for current destination
            navController.currentDestination?.let { destination ->
                val hideOn = setOf(R.id.introFragment, R.id.onboardingFragment)
                val shouldShow = destination.id !in hideOn
                if (shouldShow && !bottomNav.isVisible) {
                    bottomNav.isVisible = true
                    bottomNav.translationY = 0f
                    bottomNav.alpha = 1f
                } else if (!shouldShow && bottomNav.isVisible) {
                    bottomNav.isVisible = false
                    bottomNav.translationY = bottomNav.height.toFloat()
                    bottomNav.alpha = 0f
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            android.util.Log.d("NavBarDebug", "Destination changed: ${destination.label} (${destination.id})")
            val hideOn = setOf(R.id.introFragment, R.id.onboardingFragment)
            val shouldShow = destination.id !in hideOn
            if (shouldShow && !bottomNav.isVisible) {
                bottomNav.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .withStartAction { bottomNav.isVisible = true }
                    .start()
            } else if (!shouldShow && bottomNav.isVisible) {
                bottomNav.animate()
                    .translationY(bottomNav.height.toFloat())
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { bottomNav.isVisible = false }
                    .start()
            }
        }

        bottomNav.setOnItemReselectedListener {
            bottomNav.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
