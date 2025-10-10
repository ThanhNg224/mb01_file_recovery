package com.thanhng224.app.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.thanhng224.app.presentation.ui.theme.GradientEnd
import com.thanhng224.app.presentation.ui.theme.GradientStart

@Composable
fun ProfileScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            ), // <-- FULL SCREEN background
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Profile Screen")
    }
}

