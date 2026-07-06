// REFERENCE FILE — not compiled. See android-integration/README.md for setup.
package com.forgeflail.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.forgeflail.core.ForgeFlailGame

/**
 * libGDX entry Activity. Constructs the REAL monetization handlers and injects
 * them into the platform-agnostic game — the only place Android and game-core meet.
 */
class AndroidLauncher : AndroidApplication() {

    private lateinit var billingHandler: AndroidBillingHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            numSamples = 2       // light MSAA; cheap win for flat-shaded voxels
            useAccelerometer = false
            useCompass = false   // disable unused sensors: battery + COPPA data minimization
        }

        val adHandler = AndroidAdHandler(this)
        billingHandler = AndroidBillingHandler(this)

        initialize(ForgeFlailGame(billingHandler, adHandler), config)
    }

    override fun onDestroy() {
        billingHandler.endConnection()
        super.onDestroy()
    }
}
