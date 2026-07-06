package com.forgeflail.core.gacha

import com.badlogic.gdx.graphics.Color

/**
 * Gacha rarity tiers. Weights sum to exactly [WEIGHT_TOTAL] (100), so each weight
 * IS the drop percentage — which keeps the legally required odds disclosure honest
 * and trivial to render (see [GachaSystem] docs).
 *
 * Each tier also carries the 3D presentation traits applied to the sword's blade
 * material by VoxelModelFactory.applyRarityTraits: a diffuse tint and an emissive
 * glow whose strength scales with [glowIntensity].
 */
enum class Rarity(
    val weight: Int,
    val displayName: String,
    val bladeColor: Color,
    val glowColor: Color,
    val glowIntensity: Float,
    /** Scales sale price, craft duration, and passive income of blueprints in this tier. */
    val valueMultiplier: Float,
) {
    COMMON(70, "Common", Color.valueOf("9AA5B1"), Color.BLACK, 0f, 1f),
    RARE(20, "Rare", Color.valueOf("4FC3F7"), Color.valueOf("1E88E5"), 0.35f, 3f),
    EPIC(8, "Epic", Color.valueOf("B388FF"), Color.valueOf("7C4DFF"), 0.6f, 10f),
    LEGENDARY(2, "Legendary", Color.valueOf("FFD54F"), Color.valueOf("FF8F00"), 1f, 40f);

    /** Drop chance in percent — valid because weights sum to 100. */
    val dropChancePercent: Int get() = weight

    companion object {
        const val WEIGHT_TOTAL = 100

        init {
            check(entries.sumOf { it.weight } == WEIGHT_TOTAL) {
                "Rarity weights must sum to $WEIGHT_TOTAL so disclosed odds stay accurate"
            }
        }
    }
}
