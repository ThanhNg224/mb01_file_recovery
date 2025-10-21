package com.meta.brain.file.recovery

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import com.meta.brain.module.loading.LoadingActivity
import com.meta.brain.file.recovery.data.repository.MetricsRepository
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

        // Enable immersive mode: hide navigation bar, allow swipe to show (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val navHostView = findViewById<android.view.View>(R.id.nav_host_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(navHostView) { v, insets ->
            val sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sysInsets.top)
            insets
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        // Set dynamic start destination based on onboarding status
        if (savedInstanceState == null) {
            // Synchronously get onboarding status before setting the graph
            val onboardingDone = kotlinx.coroutines.runBlocking { metricsRepository.onboardingDone.first() }
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (onboardingDone) R.id.homeFragment else R.id.introFragment
            )
            navController.graph = navGraph
        }
    }
}
