// REFERENCE FILE — not compiled. See android-integration/README.md for setup.
package com.forgeflail.android

import android.app.Activity
import com.badlogic.gdx.Gdx
import com.forgeflail.core.monetization.AdHandler
import com.forgeflail.core.monetization.AdPlacement
import com.forgeflail.core.monetization.RewardedAdCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob-backed rewarded video for the two placements, configured for a
 * CHILD-DIRECTED audience. This configuration is a legal requirement
 * (COPPA / GDPR-K / Play Families), not a tuning knob:
 *  - child-directed treatment ON  -> disables personalized ads & ad-id use
 *  - max content rating G
 *
 * Threading: every game-facing callback is trampolined onto the GL thread
 * with Gdx.app.postRunnable — game state is not thread-safe.
 */
class AndroidAdHandler(private val activity: Activity) : AdHandler {

    private val loadedAds = HashMap<AdPlacement, RewardedAd?>()

    init {
        // MANDATORY child-directed configuration — set BEFORE initialize().
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build()
        )
        MobileAds.initialize(activity) {
            for (placement in AdPlacement.entries) loadAd(placement)
        }
    }

    override fun isRewardedAdReady(placement: AdPlacement): Boolean = loadedAds[placement] != null

    override fun showRewardedAd(placement: AdPlacement, callback: RewardedAdCallback) {
        val ad = loadedAds[placement]
        if (ad == null) {
            Gdx.app.postRunnable { callback.onRewardNotEarned(placement) }
            loadAd(placement)
            return
        }
        loadedAds[placement] = null // one-shot; reload after show

        var rewardEarned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadAd(placement)
                if (!rewardEarned) {
                    Gdx.app.postRunnable { callback.onRewardNotEarned(placement) }
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadAd(placement)
                Gdx.app.postRunnable { callback.onRewardNotEarned(placement) }
            }
        }
        activity.runOnUiThread {
            ad.show(activity) { _ ->
                rewardEarned = true
                // GL-thread hop: this is the ONLY line that grants the reward.
                Gdx.app.postRunnable { callback.onRewardEarned(placement) }
            }
        }
    }

    fun endConnection() {
        loadedAds.clear()
    }

    private fun loadAd(placement: AdPlacement) {
        RewardedAd.load(
            activity,
            adUnitIdFor(placement),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadedAds[placement] = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadedAds[placement] = null
                    // No retry storm: next showRewardedAd miss triggers one reload.
                }
            }
        )
    }

    private fun adUnitIdFor(placement: AdPlacement): String = when (placement) {
        // TODO replace with real ad unit ids from the AdMob console.
        // These are Google's public TEST ids — ship-blocking to leave in.
        AdPlacement.DOUBLE_RUN_COINS -> "ca-app-pub-3940256099942544/5224354917"
        AdPlacement.REVIVE_RUN -> "ca-app-pub-3940256099942544/5224354917"
    }
}
