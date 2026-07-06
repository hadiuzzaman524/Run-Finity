package com.forgeflail.core.gacha

import com.badlogic.gdx.math.RandomXS128

/**
 * A craftable weapon recipe won from a gacha chest. Immutable; produced only by
 * [BlueprintCatalog.roll]. Feeds the crafting loop:
 *   chest -> Blueprint -> CraftingManager (time gate) -> finished sword
 *   -> sale gold + permanent passive income bump.
 */
data class Blueprint(
    val id: String,
    val displayName: String,
    val rarity: Rarity,
    /** Real-time craft duration — the time gate monetized by gem/ad skips. */
    val craftDurationSeconds: Long,
    /** One-time gold payout when the craft completes. */
    val saleGold: Long,
    /** Permanent gold-per-second added to offline/idle earnings on completion. */
    val passiveGoldPerSecondBonus: Long,
)

object BlueprintCatalog {

    private val COMMON_NAMES = arrayOf("Sturdy Shortsword", "Apprentice Blade", "Iron Poker")
    private val RARE_NAMES = arrayOf("Riverglass Saber", "Skyshard Cutlass", "Frostbite Edge")
    private val EPIC_NAMES = arrayOf("Stormcaller Claymore", "Voidpetal Fang", "Ember Colossus")
    private val LEGENDARY_NAMES = arrayOf("Dawnbreaker", "The Flail-Forged King", "Solar Tempest")

    /** Base values for COMMON; higher tiers scale by [Rarity.valueMultiplier]. */
    private const val BASE_CRAFT_SECONDS = 45L
    private const val BASE_SALE_GOLD = 150L
    private const val BASE_PASSIVE_BONUS = 1L

    private var nextInstanceId = 0

    fun roll(rarity: Rarity, random: RandomXS128): Blueprint {
        val names = namesFor(rarity)
        val name = names[random.nextInt(names.size)]
        return Blueprint(
            id = "bp_${rarity.name.lowercase()}_${nextInstanceId++}",
            displayName = name,
            rarity = rarity,
            craftDurationSeconds = (BASE_CRAFT_SECONDS * rarity.valueMultiplier).toLong(),
            saleGold = (BASE_SALE_GOLD * rarity.valueMultiplier).toLong(),
            passiveGoldPerSecondBonus = (BASE_PASSIVE_BONUS * rarity.valueMultiplier).toLong(),
        )
    }

    private fun namesFor(rarity: Rarity): kotlin.Array<String> = when (rarity) {
        Rarity.COMMON -> COMMON_NAMES
        Rarity.RARE -> RARE_NAMES
        Rarity.EPIC -> EPIC_NAMES
        Rarity.LEGENDARY -> LEGENDARY_NAMES
    }
}
