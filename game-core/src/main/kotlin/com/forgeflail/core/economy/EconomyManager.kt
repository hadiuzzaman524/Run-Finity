package com.forgeflail.core.economy

import com.forgeflail.core.save.SaveManager

/**
 * Single source of truth for both currencies:
 *  - Gold: soft currency, earned by crafting/idling, never sold for real money directly.
 *  - Gems: premium currency, granted by IAP (see monetization.BillingHandler) and
 *    small earned drips; spent on gacha chests and craft-timer skips.
 *
 * All mutations go through spend/add so the UI listener and analytics hooks see
 * every change. Balances are Long to avoid float drift and boxing.
 */
class EconomyManager(private val save: SaveManager) {

    /** UI/state observer. Single method with primitive params: no boxing, no varargs. */
    fun interface BalanceListener {
        fun onBalancesChanged(gold: Long, gems: Long)
    }

    var gold: Long = 0L
        private set

    var gems: Long = 0L
        private set

    var listener: BalanceListener? = null

    init {
        if (save.isFirstRun()) {
            // Starter grant: enough gems for ~3 gacha chests so new players learn the
            // loop with earned currency before any purchase decision exists.
            gold = STARTING_GOLD
            gems = STARTING_GEMS
            save.markInitialized()
        } else {
            gold = save.loadGold(STARTING_GOLD)
            gems = save.loadGems(STARTING_GEMS)
        }
    }

    fun addGold(amount: Long) {
        if (amount <= 0L) return
        gold += amount
        notifyChanged()
    }

    fun spendGold(amount: Long): Boolean {
        if (amount <= 0L || gold < amount) return false
        gold -= amount
        notifyChanged()
        return true
    }

    /** Called by the BillingHandler purchase callback and by earned-gem drops. */
    fun addGems(amount: Long) {
        if (amount <= 0L) return
        gems += amount
        notifyChanged()
    }

    fun spendGems(amount: Long): Boolean {
        if (amount <= 0L || gems < amount) return false
        gems -= amount
        notifyChanged()
        return true
    }

    private fun notifyChanged() {
        listener?.onBalancesChanged(gold, gems)
    }

    companion object {
        const val STARTING_GOLD = 500L
        const val STARTING_GEMS = 120L
    }
}
