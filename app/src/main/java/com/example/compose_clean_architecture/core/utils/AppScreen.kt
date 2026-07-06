package com.example.compose_clean_architecture.core.utils

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import cafe.adriel.voyager.core.screen.Screen

interface AppScreen : Screen {

    @SuppressLint("ConfigurationScreenWidthHeight")
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    override fun Content() {
        val activity = LocalActivity.current
        if (activity != null) {
            val windowSize = calculateWindowSizeClass(activity)
            RenderLayout(windowSize.widthSizeClass)
        } else {
            // Fallback for previews and situations where LocalActivity is not available
            val configuration = LocalConfiguration.current
            val widthSizeClass = when {
                configuration.screenWidthDp < 600 -> WindowWidthSizeClass.Compact
                configuration.screenWidthDp < 840 -> WindowWidthSizeClass.Medium
                else -> WindowWidthSizeClass.Expanded
            }
            RenderLayout(widthSizeClass)
        }
    }

    @Composable
    private fun RenderLayout(windowSize: WindowWidthSizeClass) {
        when (windowSize) {
            WindowWidthSizeClass.Compact -> BuildCompact()
            WindowWidthSizeClass.Medium -> BuildTablet()
            WindowWidthSizeClass.Expanded -> BuildDesktop()
        }
    }

    @Composable
    fun BuildCompact()

    @Composable
    fun BuildTablet() {
        BuildCompact()
    }

    @Composable
    fun BuildDesktop() {
        BuildTablet()
    }
}
