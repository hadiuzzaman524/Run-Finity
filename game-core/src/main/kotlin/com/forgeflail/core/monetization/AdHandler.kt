package com.forgeflail.core.monetization

/**
 * Every rewarded-video surface in the game. The Android implementation maps each
 * placement to an AdMob rewarded ad unit id (see android-integration/AndroidAdHandler.kt).
 */
enum class AdPlacement {
    /** Game-over panel: double the coins collected in the finished run. */
    DOUBLE_RUN_COINS,

    /** Game-over panel: revive once per run and keep going. */
    REVIVE_RUN,
}

/**
 * Result callback for a rewarded ad request. Implementations of [AdHandler]
 * MUST invoke these on the GL thread (`Gdx.app.postRunnable { ... }` from the
 * Android UI thread) — game state is not thread-safe.
 */
interface RewardedAdCallback {
    /** The user watched to completion: grant exactly the advertised reward, once. */
    fun onRewardEarned(placement: AdPlacement)

    /** Closed early / failed to show: grant nothing, keep the normal path available. */
    fun onRewardNotEarned(placement: AdPlacement)
}

/**
 * Platform seam for rewarded video. game-core never imports an ad SDK; the
 * launcher injects an implementation:
 *  - desktop: [DebugAdHandler] (instant fake rewards for iteration)
 *  - Android: AdMob-backed handler — see android-integration/AndroidAdHandler.kt,
 *    which also carries the MANDATORY child-directed configuration
 *    (TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE, MAX_AD_CONTENT_RATING_G,
 *    non-personalized only) required for an under-16 audience under
 *    COPPA / GDPR-K / Play Families policy.
 *
 * UX rule for this audience: ads are always player-initiated via a clearly
 * labeled button ("Watch a video to revive"), never interstitials or timers
 * that auto-play.
 */
interface AdHandler {
    /** Poll before showing the button so kids never tap a dead "watch ad" offer. */
    fun isRewardedAdReady(placement: AdPlacement): Boolean

    fun showRewardedAd(placement: AdPlacement, callback: RewardedAdCallback)
}
