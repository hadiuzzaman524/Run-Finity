package com.forgeflail.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.forgeflail.core.ForgeFlailGame
import com.forgeflail.core.monetization.DebugAdHandler
import com.forgeflail.core.monetization.DebugBillingHandler

/**
 * Desktop dev launcher: phone-portrait window, debug monetization handlers.
 * All interaction is through the on-screen HUD (chest, forge skips, shop,
 * offline claim); dragging outside the UI orbits the camera.
 */
fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Forge & Flail: Voxel Tycoon (dev)")
        setWindowedMode(432, 768) // 9:16 phone aspect
        useVsync(true)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4) // 4x MSAA for the voxel edges
    }
    Lwjgl3Application(ForgeFlailGame(DebugBillingHandler(), DebugAdHandler()), config)
}
