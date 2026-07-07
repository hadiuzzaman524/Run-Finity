package com.forgeflail.core.runner

import com.badlogic.gdx.graphics.Color

/** Which track-tile / barrier art set a level renders with. */
enum class LevelTheme { TOWN_ROAD, VILLAGE_ROAD }

/**
 * Tuning + dressing for one stretch of the delivery route. Difficulty lives
 * entirely in data (speeds, row spacing, spawn-pattern odds) so [RunnerSystem]
 * stays a single allocation-free simulation for every level.
 *
 * Spawn odds: `spawnRow` rolls a d10 (0..9); roll <= [coinTrailRoll] spawns a
 * coin trail, <= [coinRowRoll] a full coin row, <= [singleBarrierRoll] one
 * barrier with an escape coin, and anything above the double-barrier squeeze.
 */
class LevelSpec(
    val displayName: String,
    /** One-line story beat shown on the menu when the level is selected. */
    val intro: String,
    /** Distance that passes the level and unlocks the next; 0 = endless finale. */
    val goalMeters: Int,
    val theme: LevelTheme,
    /** Sky color; also applied to the fog so the horizon never seams. */
    val sky: Color,
    val startSpeed: Float,
    val maxSpeed: Float,
    val speedRampPerSec: Float,
    val rowSpacingMin: Float,
    val rowSpacingMax: Float,
    val coinTrailRoll: Int,
    val coinRowRoll: Int,
    val singleBarrierRoll: Int,
)

/**
 * The blacksmith kid's delivery route, in order: swords go across town first,
 * then out to the old village. Reaching a level's [LevelSpec.goalMeters] in a
 * single run unlocks the next stretch. Progression is skill-only — levels are
 * never sold or ad-gated (under-16 policy: no pay-to-progress).
 */
object LevelCatalog {

    val LEVELS = listOf(
        LevelSpec(
            displayName = "Town Road",
            intro = "Deliver the day's swords across town!",
            goalMeters = 400,
            theme = LevelTheme.TOWN_ROAD,
            sky = Color(0.45f, 0.71f, 0.95f, 1f),
            // Tuned gentle for young players: slower cap, softer ramp.
            startSpeed = 8f, maxSpeed = 18f, speedRampPerSec = 0.15f,
            rowSpacingMin = 6f, rowSpacingMax = 11f,
            coinTrailRoll = 3, coinRowRoll = 5, singleBarrierRoll = 8,
        ),
        LevelSpec(
            displayName = "Village Road",
            intro = "The old village path: log fences, hay carts and a faster pace!",
            goalMeters = 0,
            theme = LevelTheme.VILLAGE_ROAD,
            sky = Color(0.98f, 0.82f, 0.58f, 1f),
            // The earned stretch: quicker ramp, tighter rows, more barriers —
            // still fair (spawner always leaves a free lane).
            startSpeed = 10f, maxSpeed = 21f, speedRampPerSec = 0.24f,
            rowSpacingMin = 5f, rowSpacingMax = 8.5f,
            coinTrailRoll = 2, coinRowRoll = 3, singleBarrierRoll = 7,
        ),
    )
}
