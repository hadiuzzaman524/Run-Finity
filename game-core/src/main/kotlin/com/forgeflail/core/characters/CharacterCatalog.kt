package com.forgeflail.core.characters

import com.badlogic.gdx.graphics.Color

/**
 * How the head is dressed; drives which boxes VoxelModelFactory adds. The
 * flags say which stock face features stay visible under the headdress, so
 * covered-face styles (mask, wrap, helm, dome) compose without per-style
 * face rebuilds.
 */
enum class HeadStyle(
    /** Skin dome on top of the head (bald/robot crown). */
    val crownBox: Boolean = false,
    val showsEars: Boolean = true,
    val showsBrows: Boolean = true,
    val showsNose: Boolean = true,
    val showsMouth: Boolean = true,
    /** Accent-glow eyes with no pupils (robot look). */
    val emissiveEyes: Boolean = false,
) {
    /** Bald crown, black fringe around the sides/back (the Gopal look). */
    BALD_FRINGE(crownBox = true),

    /** Simple hair cap over the top and back. */
    HAIR_CAP,

    /** Hair cap plus long hair falling to the shoulders. */
    LONG_HAIR,

    /** Wrapped turban in the accent color with a top knot. */
    TURBAN,

    /** Metal head: antenna, glowing eyes, no hair/nose. */
    ROBOT(crownBox = true, showsBrows = false, showsNose = false, emissiveEyes = true),

    /** Superhero eye mask in the accent color; eyes show through. */
    MASKED_HERO(showsBrows = false),

    /** Full ninja hood + face wrap: only the eyes show through the slit. */
    NINJA_WRAP(showsEars = false, showsBrows = false, showsNose = false, showsMouth = false),

    /** Steel helmet with an open visor slit and an accent plume. */
    KNIGHT_HELM(showsEars = false, showsBrows = false, showsNose = false, showsMouth = false),

    /** Astronaut glass dome; face shows behind the tinted visor. */
    ASTRO_DOME(showsEars = false, showsNose = false),
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
    /** Accent-colored cape down the back (superhero look). */
    val hasCape: Boolean = false,
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
        // Global-archetype heroes (appended only — roster index is the saved
        // selection value and the unlock-bitmask bit, so never insert above).
        CharacterSpec(
            id = "blaze", costGems = 0L, displayName = "Blaze",
            intro = "Cape on, chin up - time to save the day!",
            skin = Color.valueOf("F2CB9B"), top = Color.valueOf("2D5BE3"),
            bottom = Color.valueOf("2450C8"), shoe = Color.valueOf("C1121F"),
            hair = Color.valueOf("2B1E12"), accent = Color.valueOf("E3262E"),
            headStyle = HeadStyle.MASKED_HERO, fat = 0.85f, hasScarf = false,
            hasCape = true,
        ),
        CharacterSpec(
            id = "kiro", costGems = 120L, displayName = "Kiro",
            intro = "Silent steps. Super sneaky.",
            skin = Color.valueOf("E8A870"), top = Color.valueOf("23262E"),
            bottom = Color.valueOf("1A1D24"), shoe = Color.valueOf("14161B"),
            hair = Color.valueOf("2B2F3A"), accent = Color.valueOf("E33B3B"),
            headStyle = HeadStyle.NINJA_WRAP, fat = 0.8f, hasScarf = false,
        ),
        CharacterSpec(
            id = "sirsteel", costGems = 180L, displayName = "Sir Steel",
            intro = "Shiny armor, zero fear!",
            skin = Color.valueOf("F2CB9B"), top = Color.valueOf("C7CEDA"),
            bottom = Color.valueOf("8B95A5"), shoe = Color.valueOf("4B5563"),
            hair = Color.valueOf("AEB8C6"), accent = Color.valueOf("D62828"),
            headStyle = HeadStyle.KNIGHT_HELM, fat = 1.0f, hasScarf = false,
        ),
        CharacterSpec(
            id = "nova", costGems = 260L, displayName = "Nova",
            intro = "3... 2... 1... blast off!",
            skin = Color.valueOf("F0C08C"), top = Color.valueOf("F4F6F8"),
            bottom = Color.valueOf("E8ECF2"), shoe = Color.valueOf("9AA5B1"),
            hair = Color.valueOf("FDFEFF"), accent = Color.valueOf("E8890C"),
            headStyle = HeadStyle.ASTRO_DOME, fat = 0.95f, hasScarf = false,
        ),
    )

    /** Roster index preselected for brand-new players (fresh save). */
    val DEFAULT_INDEX = ROSTER.indexOfFirst { it.id == "blaze" }
}
