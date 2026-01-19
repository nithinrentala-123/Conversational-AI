package com.niquewrld.conversationalai

import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class PolicyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure edge-to-edge display
        setupEdgeToEdge()

        // Make status bar transparent
        window.statusBarColor = 0

        // Configure system bars appearance (light/dark icons)
        setupSystemBarsAppearance()

        // Set Compose content
        setContent {
            // PolicyScreenContent()
            // The actual composable content is defined elsewhere (obfuscated as Z1/f)
        }
    }

    private fun setupEdgeToEdge() {
        val window = window
        val decorView = window.decorView
        val resources = decorView.resources

        // Check if dark theme based on resources configuration
        val isDarkTheme = (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Enable edge-to-edge based on SDK version
        when {
            Build.VERSION.SDK_INT >= 30 -> {
                // Android 11+ (API 30)
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            Build.VERSION.SDK_INT >= 29 -> {
                // Android 10 (API 29)
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            Build.VERSION.SDK_INT >= 28 -> {
                // Android 9 (API 28)
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            else -> {
                // Older versions
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }

    private fun setupSystemBarsAppearance() {
        val window = window
        val decorView = window.decorView
        
        // Set up WindowInsetsController for system bars
        val controller = WindowInsetsControllerCompat(window, decorView)
        
        // Light status bar (dark icons on light background)
        controller.isAppearanceLightStatusBars = false
    }
}
