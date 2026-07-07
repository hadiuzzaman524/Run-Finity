package com.forgeflail.core.characters

import com.badlogic.gdx.graphics.Color

/** How the head is dressed; drives which boxes VoxelModelFactory adds. */
enum class HeadStyle {
    /** Bald crown, black fringe around the sides/back (the Gopal look). */
    BALD_FRINGE,

    /** Simple hair cap over the top and back. */
    HAIR_CAP,

    /** Hair cap plus long hair falling to the shoulders. */
    LONG_HAIR,

    /** Wrapped turban in the accent color with a top knot. */
    TURBAN,

    /** Metal head: antenna, glowing eyes, no hair/nose. */
    ROBOT,
}

/**
 * Everything needed to build one selectable runner. Purely visual — gameplay
 * stats are identical for all characters (fairness for the under-16 audience:
 * character choice is expression, never pay-to-win).
 */
class CharacterSpec(
    val id: String,
    val displayName: String,
    val intro: String,
    /**
     * Gems to unlock on the Heroes screen; 0 = starter hero, free from launch.
     * Gems are earned free while running (RunnerSystem.METERS_PER_GEM), so
     * every hero stays reachable without a purchase — and heroes are cosmetic
     * only, so a locked hero never gates progression.
     */
    val costGems: Long,
    val skin: Color,
    val top: Color,
    val bottom: Color,
    val shoe: Color,
    val hair: Color,
    val accent: Color,
    val headStyle: HeadStyle,
    /** Belly/torso width multiplier; 1.05 = Gopal-chubby, 0.8 = slim kid. */
    val fat: Float,
    val hasScarf: Boolean,
)

object CharacterCatalog {

    val ROSTER = listOf(
        CharacterSpec(
            id = "gopal", costGems = 0L, displayName = "Gopal",
            intro = "The clever one! Quick wits, quicker feet.",
            skin = Color.valueOf("F2CB9B"), top = Color.valueOf("F0B429"),
            bottom = Color.valueOf("D62828"), shoe = Color.valueOf("22201D"),
            hair = Color.valueOf("22201D"), accent = Color.valueOf("C1121F"),
            headStyle = HeadStyle.BALD_FRINGE, fat = 1.05f, hasScarf = true,
        ),
        CharacterSpec(
            id = "raju", costGems = 60L, displayName = "Raju",
            intro = "Fastest kid on the block.",
            skin = Color.valueOf("E8A870"), top = Color.valueOf("E4572E"),
            bottom = Color.valueOf("2D6CDF"), shoe = Color.valueOf("3A2E24"),
            hair = Color.valueOf("2B1E12"), accent = Color.valueOf("FFD54F"),
            headStyle = HeadStyle.HAIR_CAP, fat = 0.85f, hasScarf = false,
        ),
        CharacterSpec(
            id = "meena", costGems = 90L, displayName = "Meena",
            intro = "Never misses a coin.",
            skin = Color.valueOf("F0C08C"), top = Color.valueOf("2FA36B"),
            bottom = Color.valueOf("1B7A8C"), shoe = Color.valueOf("5C3A21"),
            hair = Color.valueOf("191512"), accent = Color.valueOf("E4572E"),
            headStyle = HeadStyle.LONG_HAIR, fat = 0.8f, hasScarf = false,
        ),
        CharacterSpec(
            id = "bir", costGems = 140L, displayName = "Bir Singh",
            intro = "Brave heart, bright turban.",
            skin = Color.valueOf("C98A4B"), top = Color.valueOf("F4EFE6"),
            bottom = Color.valueOf("3B3F51"), shoe = Color.valueOf("2A2118"),
            hair = Color.valueOf("20180F"), accent = Color.valueOf("E8890C"),
            headStyle = HeadStyle.TURBAN, fat = 0.9f, hasScarf = false,
        ),
        CharacterSpec(
            id = "voltu", costGems = 220L, displayName = "Voltu",
            intro = "Beep boop! Built to run.",
            skin = Color.valueOf("9AA5B1"), top = Color.valueOf("6B7684"),
            bottom = Color.valueOf("4B5563"), shoe = Color.valueOf("22262E"),
            hair = Color.valueOf("3A414D"), accent = Color.valueOf("39D5FF"),
            headStyle = HeadStyle.ROBOT, fat = 0.9f, hasScarf = false,
        ),
    )
}
