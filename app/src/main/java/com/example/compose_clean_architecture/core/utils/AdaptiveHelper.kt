package com.example.compose_clean_architecture.core.utils

import android.annotation.SuppressLint
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class DeviceType {
    MOBILE, TABLET, DESKTOP
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun getDeviceType(): DeviceType {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp

    val windowSize = when {
        width < 600 -> WindowWidthSizeClass.Compact
        width < 840 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }

    return when (windowSize) {
        WindowWidthSizeClass.Compact -> DeviceType.MOBILE
        WindowWidthSizeClass.Medium -> DeviceType.TABLET
        WindowWidthSizeClass.Expanded if width <= 1280 -> DeviceType.TABLET
        WindowWidthSizeClass.Expanded -> DeviceType.DESKTOP
        else -> DeviceType.MOBILE
    }
}
