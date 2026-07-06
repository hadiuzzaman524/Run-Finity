package com.forgeflail.core.monetization

import com.badlogic.gdx.Gdx

/**
 * Desktop/test stand-ins for the platform SDKs. They log and immediately succeed
 * so the whole monetization loop is exercisable in the LWJGL3 launcher.
 * NEVER ship these in the Android build.
 */
class DebugAdHandler : AdHandler {

    override fun isRewardedAdReady(placement: AdPlacement): Boolean = true

    override fun showRewardedAd(placement: AdPlacement, callback: RewardedAdCallback) {
        Gdx.app.log(TAG, "Fake rewarded ad completed for $placement")
        callback.onRewardEarned(placement)
    }

    private companion object {
        const val TAG = "DebugAdHandler"
    }
}

class DebugBillingHandler : BillingHandler {

    override fun launchPurchase(productId: String, callback: PurchaseCallback) {
        if (GemStoreCatalog.gemsFor(productId) <= 0L) {
            callback.onPurchaseFailed(productId, "Unknown product id")
            return
        }
        Gdx.app.log(TAG, "Fake purchase granted for $productId")
        callback.onPurchaseGranted(productId)
    }

    override fun restorePurchases(callback: PurchaseCallback) {
        Gdx.app.log(TAG, "Fake restore: nothing to restore")
    }

    private companion object {
        const val TAG = "DebugBillingHandler"
    }
}
