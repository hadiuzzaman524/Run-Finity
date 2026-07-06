package com.forgeflail.core.monetization

/**
 * Result callback for an in-app purchase. Same threading contract as ads:
 * implementations MUST deliver these on the GL thread via `Gdx.app.postRunnable`.
 */
interface PurchaseCallback {
    /**
     * The purchase is PAID AND ACKNOWLEDGED/CONSUMED by the store. Only now may
     * the game grant gems ([GemStoreCatalog.gemsFor]). Never grant on "flow
     * launched" — users can cancel, and unacknowledged purchases auto-refund.
     */
    fun onPurchaseGranted(productId: String)

    fun onPurchaseFailed(productId: String, reason: String)
}

/**
 * Platform seam for real-money purchases. game-core never imports Play Billing;
 * the launcher injects an implementation:
 *  - desktop: [DebugBillingHandler] (instant fake grants for iteration)
 *  - Android: Play Billing-backed handler — see
 *    android-integration/AndroidBillingHandler.kt for the full BillingClient
 *    lifecycle (connect, query ProductDetails, launch flow, consume, restore).
 *
 * Under-16 requirements baked into the design:
 *  - Gems are the ONLY thing sold; all SKUs live in [GemStoreCatalog] with low
 *    price points and no "best value mega pack" pressure framing.
 *  - The Android implementation must show a parental gate before launching the
 *    Google purchase sheet (Play Families expectation; App Store requirement).
 *  - No purchase is ever required to progress; every gem sink has a free path
 *    (waiting, earned gems, rewarded ads).
 */
interface BillingHandler {
    /** Launches the platform purchase flow for one [GemStoreCatalog] product id. */
    fun launchPurchase(productId: String, callback: PurchaseCallback)

    /** Re-grants owned-but-unconsumed purchases (app reinstall, crash mid-grant). */
    fun restorePurchases(callback: PurchaseCallback)
}

/**
 * The full IAP catalog. Product ids must match the Play Console exactly; prices
 * are configured in the console, the strings here are display hints only.
 */
object GemStoreCatalog {

    data class GemPack(val productId: String, val gems: Long, val priceHint: String)

    val PACKS = listOf(
        GemPack("gems_pouch_80", 80L, "$0.99"),
        GemPack("gems_bag_260", 260L, "$2.99"),
        GemPack("gems_chest_560", 560L, "$5.99"),
    )

    /** Gems granted for a store product id, or 0 for unknown ids (grant nothing). */
    fun gemsFor(productId: String): Long {
        for (i in PACKS.indices) {
            if (PACKS[i].productId == productId) return PACKS[i].gems
        }
        return 0L
    }
}
