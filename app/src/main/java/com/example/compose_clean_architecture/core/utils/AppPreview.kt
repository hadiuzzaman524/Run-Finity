package com.example.compose_clean_architecture.core.utils

import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Mobile",
    group = "Devices",
    showBackground = true,
    device = Devices.PHONE
)
annotation class PreviewMobile

@Preview(
    name = "Tablet",
    group = "Devices",
    showBackground = true,
    device = Devices.TABLET
)
annotation class PreviewTablet

@Preview(
    name = "Desktop",
    group = "Devices",
    showBackground = true,
    device = Devices.DESKTOP
)
annotation class PreviewDesktop

@PreviewMobile
@PreviewTablet
@PreviewDesktop
annotation class PreviewAllDevices
