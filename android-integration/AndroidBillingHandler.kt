// REFERENCE FILE — not compiled. See android-integration/README.md for setup.
package com.forgeflail.android

import android.app.Activity
import com.badlogic.gdx.Gdx
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.forgeflail.core.monetization.BillingHandler
import com.forgeflail.core.monetization.GemStoreCatalog
import com.forgeflail.core.monetization.PurchaseCallback

/**
 * Google Play Billing implementation of the game's BillingHandler seam.
 * Gem packs are CONSUMABLE products: grant gems -> consumeAsync so they can be
 * repurchased. The grant fires only after Play confirms the purchase (PURCHASED
 * state), and consumption doubles as acknowledgement.
 *
 * Threading: PurchasesUpdatedListener arrives on the main thread; every game
 * callback is trampolined onto the GL thread with Gdx.app.postRunnable.
 */
class AndroidBillingHandler(private val activity: Activity) : BillingHandler {

    private var pendingCallback: PurchaseCallback? = null
    private val productDetailsCache = HashMap<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val callback = pendingCallback
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                purchases.forEach { handlePurchase(it, callback) }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                notifyFailed(callback, "unknown", "User cancelled")
            else ->
                notifyFailed(callback, "unknown", "Billing error ${result.responseCode}")
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    init {
        connect()
    }

    override fun launchPurchase(productId: String, callback: PurchaseCallback) {
        // ---------------------------------------------------------------
        // PARENTAL GATE (required for the under-16 audience): before the Play
        // sheet, show an adult-verification challenge (e.g. "enter your year of
        // birth" / hold-to-confirm math task). Only proceed on success:
        //   ParentalGate.show(activity) { passed -> if (passed) doLaunch(...) }
        // ---------------------------------------------------------------
        val details = productDetailsCache[productId]
        if (details == null) {
            notifyFailed(callback, productId, "Product details not loaded yet")
            return
        }
        pendingCallback = callback
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        activity.runOnUiThread { billingClient.launchBillingFlow(activity, params) }
    }

    override fun restorePurchases(callback: PurchaseCallback) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it, callback) }
            }
        }
    }

    fun endConnection() = billingClient.endConnection()

    // -------------------------------------------------------------------

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) queryProducts()
            }

            override fun onBillingServiceDisconnected() {
                connect() // Play recommends reconnect-on-demand; simple retry is fine here
            }
        })
    }

    private fun queryProducts() {
        val products = GemStoreCatalog.PACKS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsList.forEach { productDetailsCache[it.productId] = it }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase, callback: PurchaseCallback?) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Consume = acknowledge for consumables. Grant AFTER consume succeeds so a
        // crash can't double-grant (restorePurchases re-runs unconsumed purchases).
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(params) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchase.products.forEach { productId ->
                    Gdx.app.postRunnable { callback?.onPurchaseGranted(productId) }
                }
            }
        }
    }

    private fun notifyFailed(callback: PurchaseCallback?, productId: String, reason: String) {
        Gdx.app.postRunnable { callback?.onPurchaseFailed(productId, reason) }
    }
}
