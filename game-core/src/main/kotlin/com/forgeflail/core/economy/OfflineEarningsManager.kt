package com.forgeflail.core.economy

import com.forgeflail.core.save.SaveManager

/**
 * The idle half of the tycoon loop: gold accrues while the app is closed, based on
 * the passive income rate the player built up by crafting weapons.
 *
 * Flow:
 *  1. `pause()`  -> [onGamePaused] persists the wall-clock timestamp.
 *  2. `resume()` -> [onGameResumed] computes capped earnings into [pendingGold]
 *     and fires [Listener.onOfflineEarningsReady] so the UI can show the
 *     "Welcome back!" dialog with two buttons:
 *       - "Claim"            -> [claim] (multiplier 1)
 *       - "Double (watch ad)" -> show AdPlacement.DOUBLE_OFFLINE_EARNINGS rewarded
 *         video; on onRewardEarned call [claim] with [DOUBLED]. See ForgeFlailGame.
 *
 * Earnings are capped at [MAX_OFFLINE_SECONDS] so the optimal cadence is checking
 * in a couple of times a day — a session driver that doesn't punish kids who can
 * only play after school.
 */
class OfflineEarningsManager(
    private val economy: EconomyManager,
    private val save: SaveManager,
) {

    fun interface Listener {
        fun onOfflineEarningsReady(pendingGold: Long, awaySeconds: Long)
    }

    /** Passive income rate; grows as the player crafts better weapons. Persisted. */
    var goldPerSecond: Long = save.loadGoldPerSecond(BASE_GOLD_PER_SECOND)

    /** Unclaimed offline gold awaiting the player's claim/double decision. */
    var pendingGold: Long = 0L
        private set

    var listener: Listener? = null

    fun onGamePaused(nowUtcMs: Long) {
        save.persist(economy.gold, economy.gems, goldPerSecond, nowUtcMs)
    }

    fun onGameResumed(nowUtcMs: Long) {
        val lastSeen = save.loadLastSeenUtcMs(fallbackNowMs = nowUtcMs)
        val awaySeconds = ((nowUtcMs - lastSeen) / 1000L).coerceIn(0L, MAX_OFFLINE_SECONDS)
        if (awaySeconds < MIN_AWAY_SECONDS) return
        pendingGold = awaySeconds * goldPerSecond
        listener?.onOfflineEarningsReady(pendingGold, awaySeconds)
    }

    /**
     * Banks [pendingGold] * [multiplier]. Only [NORMAL] and [DOUBLED] are legal;
     * DOUBLED must only follow a completed rewarded ad ([AdPlacement.DOUBLE_OFFLINE_EARNINGS]).
     */
    fun claim(multiplier: Long = NORMAL) {
        require(multiplier == NORMAL || multiplier == DOUBLED) { "Unsupported multiplier: $multiplier" }
        if (pendingGold <= 0L) return
        economy.addGold(pendingGold * multiplier)
        pendingGold = 0L
    }

    companion object {
        const val NORMAL = 1L
        const val DOUBLED = 2L
        const val BASE_GOLD_PER_SECOND = 2L
        const val MAX_OFFLINE_SECONDS = 6L * 60L * 60L // 6 hour cap
        const val MIN_AWAY_SECONDS = 60L               // ignore quick app switches
    }
}
