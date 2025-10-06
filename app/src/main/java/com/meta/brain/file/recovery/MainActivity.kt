package com.meta.brain.file.recovery

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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        WindowCompat.setDecorFitsSystemWindows(window, false)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController) // NavigationUI integration


        val extraTop = resources.getDimensionPixelSize(R.dimen.bottom_nav_item_offset_top)
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = extraTop, bottom = sys.bottom)
            insets
        }
        bottomNav.clipToPadding = false


        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideOn = setOf(R.id.introFragment)
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
