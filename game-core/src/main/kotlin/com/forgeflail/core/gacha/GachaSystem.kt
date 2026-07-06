package com.forgeflail.core.gacha

import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.TimeUtils
import com.forgeflail.core.economy.EconomyManager

/**
 * Gem-priced gacha chest awarding weapon [Blueprint]s by weighted rarity:
 * Common 70% / Rare 20% / Epic 8% / Legendary 2% (defined on [Rarity]).
 *
 * COMPLIANCE — NON-OPTIONAL for this game's under-16 audience:
 *  - Google Play and the App Store both REQUIRE the exact drop odds to be shown
 *    in-game before purchase. Render [Rarity.entries] with
 *    [Rarity.dropChancePercent] directly on the chest screen; never hard-code a
 *    second copy of the numbers that can drift from the real weights.
 *  - Several jurisdictions (e.g. Belgium) restrict paid loot boxes entirely —
 *    gate chest availability by region at the store/config level.
 *  - Keep chests purchasable ONLY with gems (never a direct cash SKU) and keep
 *    gems earnable in normal play, so every player can engage without paying.
 */
class GachaSystem(
    private val economy: EconomyManager,
    seed: Long = TimeUtils.nanoTime(),
) {

    interface Listener {
        fun onChestOpened(blueprint: Blueprint)
        fun onChestDenied(gemsShort: Long)
    }

    // XorShift128+ — fast, allocation-free, seedable for tests/replays.
    private val random = RandomXS128(seed)

    var listener: Listener? = null

    /**
     * Spends [CHEST_COST_GEMS] and rolls a blueprint. Returns null (and fires
     * [Listener.onChestDenied]) when the player can't afford it — the UI should
     * respond by showing how to EARN gems first, with the gem store as a
     * secondary option, not by deep-linking straight to purchase.
     */
    fun openChest(): Blueprint? {
        if (!economy.spendGems(CHEST_COST_GEMS)) {
            listener?.onChestDenied(gemsShort = CHEST_COST_GEMS - economy.gems)
            return null
        }
        val blueprint = BlueprintCatalog.roll(rollRarity(), random)
        listener?.onChestOpened(blueprint)
        return blueprint
    }

    /**
     * Weighted roll: one uniform int in [0, 100), then walk the cumulative weights.
     * O(#rarities), zero allocation ([Rarity.entries] is a cached list).
     */
    fun rollRarity(): Rarity {
        var roll = random.nextInt(Rarity.WEIGHT_TOTAL)
        for (rarity in Rarity.entries) {
            roll -= rarity.weight
            if (roll < 0) return rarity
        }
        return Rarity.COMMON // unreachable while weights sum to WEIGHT_TOTAL
    }

    companion object {
        const val CHEST_COST_GEMS = 40L
    }
}
