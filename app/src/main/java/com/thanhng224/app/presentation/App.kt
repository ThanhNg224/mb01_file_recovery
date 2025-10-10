package com.thanhng224.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thanhng224.app.presentation.screen.FavoritesScreen
import com.thanhng224.app.presentation.screen.HomeScreen
import com.thanhng224.app.presentation.screen.ProfileScreen
import com.thanhng224.app.presentation.screen.SettingsScreen
import com.thanhng224.app.presentation.ui.theme.GradientEnd
import com.thanhng224.app.presentation.ui.theme.GradientStart
import com.thanhng224.app.presentation.viewmodel.HomeViewModel
import com.thanhng224.app.util.ApiState

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    open val unreadCount: Int = 0

    object Home : Screen("home", "Home", Icons.Default.Home)
    object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite) {
        override val unreadCount: Int = 5
    }
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun App() {
    val items = listOf(Screen.Home, Screen.Favorites, Screen.Profile, Screen.Settings)
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val productDetailsState by homeViewModel.productDetailsState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                NavHost(
                    navController,
                    startDestination = Screen.Home.route
                ) {
                    composable(Screen.Home.route) { HomeScreen(productDetails = productDetailsState) }
                    composable(Screen.Favorites.route) { FavoritesScreen() }
                    composable(Screen.Profile.route) { ProfileScreen() }
                    composable(Screen.Settings.route) { SettingsScreen() }
                }

                FloatingNavBar(
                    items = items,
                    navController = navController,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBar(
    items: List<Screen>,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // This is the floating navigation bar composable
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(24.dp))
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (screen is Screen.Favorites && screen.unreadCount > 0) {
                                BadgedBox(badge = { Badge { Text("${screen.unreadCount}") } }) {
                                    Icon(screen.icon, contentDescription = screen.title)
                                }
                            } else {
                                val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "iconScale")
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                                )
                            }
                        },
                        label = {
                            AnimatedVisibility(visible = selected) { Text(screen.title) }
                        },
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
