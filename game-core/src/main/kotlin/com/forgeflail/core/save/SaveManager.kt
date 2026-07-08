package com.forgeflail.core.save

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Thin persistence wrapper over libGDX [Preferences] (SharedPreferences on Android).
 * All game state that must survive process death funnels through here so the storage
 * backend can later be swapped (e.g. for cloud save) in one place.
 *
 * NOTE for the under-16 audience: keep saves fully local/anonymous. Do not add any
 * personally identifiable data here without a COPPA/GDPR-K review.
 */
class SaveManager {

    // Lazy: Gdx.app does not exist until ApplicationListener.create().
    private val prefs: Preferences by lazy { Gdx.app.getPreferences(PREFS_NAME) }

    fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_INITIALIZED, false)

    fun markInitialized() {
        prefs.putBoolean(KEY_INITIALIZED, true)
        prefs.flush()
    }

    fun loadGold(default: Long): Long = prefs.getLong(KEY_GOLD, default)

    fun loadGems(default: Long): Long = prefs.getLong(KEY_GEMS, default)

    /** Defaults to [fallbackNowMs] so a fresh install never sees phantom offline time. */
    fun loadLastSeenUtcMs(fallbackNowMs: Long): Long = prefs.getLong(KEY_LAST_SEEN_MS, fallbackNowMs)

    fun loadGoldPerSecond(default: Long): Long = prefs.getLong(KEY_GOLD_PER_SECOND, default)

    fun loadBestScore(): Int = prefs.getInteger(KEY_BEST_SCORE, 0)

    fun loadEquippedRarity(defaultOrdinal: Int): Int = prefs.getInteger(KEY_EQUIPPED_RARITY, defaultOrdinal)

    /** -1 means "never chosen" — the game shows the onboarding select screen. */
    fun loadSelectedCharacter(): Int = prefs.getInteger(KEY_CHARACTER, -1)

    /**
     * Bitmask of unlocked roster indices (bit i = CharacterCatalog.ROSTER[i]).
     * Default covers the free starter heroes; the game ORs it with the
     * cost-0 mask on load so a roster change can never lock everyone out.
     */
    fun loadUnlockedCharacters(default: Int): Int = prefs.getInteger(KEY_UNLOCKED_CHARACTERS, default)

    /** How many levels of LevelCatalog are playable (skill-unlocked, min 1). */
    fun loadUnlockedLevels(default: Int): Int = prefs.getInteger(KEY_UNLOCKED_LEVELS, default)

    fun loadSelectedLevel(): Int = prefs.getInteger(KEY_SELECTED_LEVEL, 0)

    // Sound toggles from the settings screen; everything defaults to ON.
    fun loadCoinSoundsOn(): Boolean = prefs.getBoolean(KEY_SOUND_COINS, true)

    fun loadFootstepSoundsOn(): Boolean = prefs.getBoolean(KEY_SOUND_FOOTSTEPS, true)

    fun loadActionSoundsOn(): Boolean = prefs.getBoolean(KEY_SOUND_ACTIONS, true)

    /** Flushed on every toggle — user-driven and rare, so the disk hit is fine. */
    fun persistSoundSettings(coinsOn: Boolean, footstepsOn: Boolean, actionsOn: Boolean) {
        prefs.putBoolean(KEY_SOUND_COINS, coinsOn)
        prefs.putBoolean(KEY_SOUND_FOOTSTEPS, footstepsOn)
        prefs.putBoolean(KEY_SOUND_ACTIONS, actionsOn)
        prefs.flush()
    }

    /** Runner-mode batch write. Call from `pause()` — not per frame. */
    fun persistRunner(
        gold: Long,
        gems: Long,
        bestScore: Int,
        equippedRarityOrdinal: Int,
        selectedCharacter: Int,
        unlockedCharacters: Int,
        unlockedLevels: Int,
        selectedLevel: Int,
    ) {
        prefs.putLong(KEY_GOLD, gold)
        prefs.putLong(KEY_GEMS, gems)
        prefs.putInteger(KEY_BEST_SCORE, bestScore)
        prefs.putInteger(KEY_EQUIPPED_RARITY, equippedRarityOrdinal)
        prefs.putInteger(KEY_CHARACTER, selectedCharacter)
        prefs.putInteger(KEY_UNLOCKED_CHARACTERS, unlockedCharacters)
        prefs.putInteger(KEY_UNLOCKED_LEVELS, unlockedLevels)
        prefs.putInteger(KEY_SELECTED_LEVEL, selectedLevel)
        prefs.flush()
    }

    /** Batch-write and flush. Call from `pause()` — not per frame, flushing hits disk. */
    fun persist(gold: Long, gems: Long, goldPerSecond: Long, lastSeenUtcMs: Long) {
        prefs.putLong(KEY_GOLD, gold)
        prefs.putLong(KEY_GEMS, gems)
        prefs.putLong(KEY_GOLD_PER_SECOND, goldPerSecond)
        prefs.putLong(KEY_LAST_SEEN_MS, lastSeenUtcMs)
        prefs.flush()
    }

    private companion object {
        const val PREFS_NAME = "forge_flail_save"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_GOLD = "gold"
        const val KEY_GEMS = "gems"
        const val KEY_GOLD_PER_SECOND = "gold_per_second"
        const val KEY_LAST_SEEN_MS = "last_seen_utc_ms"
        const val KEY_BEST_SCORE = "best_score_m"
        const val KEY_EQUIPPED_RARITY = "equipped_rarity"
        const val KEY_CHARACTER = "selected_character"
        const val KEY_UNLOCKED_CHARACTERS = "unlocked_characters"
        const val KEY_UNLOCKED_LEVELS = "unlocked_levels"
        const val KEY_SELECTED_LEVEL = "selected_level"
        const val KEY_SOUND_COINS = "sound_coins"
        const val KEY_SOUND_FOOTSTEPS = "sound_footsteps"
        const val KEY_SOUND_ACTIONS = "sound_actions"
    }
}
