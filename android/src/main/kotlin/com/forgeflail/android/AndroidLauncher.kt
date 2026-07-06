package com.forgeflail.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.forgeflail.core.ForgeFlailGame
import com.forgeflail.core.monetization.DebugAdHandler
import com.forgeflail.core.monetization.DebugBillingHandler

/**
 * Dev launcher: runs the game with the DEBUG monetization handlers (instant fake
 * rewards/purchases). Before any release build, replace them with the real
 * AdMob/Play Billing implementations from android-integration/ — including the
 * child-directed ad configuration and parental gate, which are release blockers.
 */
class AndroidLauncher : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            numSamples = 2
            useAccelerometer = false
            useCompass = false
        }

        initialize(ForgeFlailGame(DebugBillingHandler(), DebugAdHandler()), config)
    }
}
