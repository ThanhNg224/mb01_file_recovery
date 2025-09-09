package com.thanhng224.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thanhng224.app.presentation.screen.HomeScreen
import com.thanhng224.app.presentation.viewmodel.HomeViewModel

@Composable
fun App() {
    val navController = rememberNavController()
    val viewModel = hiltViewModel<HomeViewModel>()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(viewModel.productDetailsState.collectAsState().value)
        }
    }
}