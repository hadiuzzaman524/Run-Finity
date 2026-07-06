package com.forgeflail.core.crafting

import com.forgeflail.core.economy.EconomyManager
import com.forgeflail.core.gacha.Blueprint

/**
 * Time-gated crafting: blueprints occupy a forge slot for their real-time
 * craft duration. Two monetized accelerators, both strictly opt-in:
 *
 *  - GEM SKIP  [skipWithGems]: pay gems proportional to remaining time
 *    (1 gem per started 3 minutes). Cost shrinks as the timer runs down, so
 *    waiting is always a respected choice — never a dead end.
 *  - AD SKIP   [applyRewardedAdSkip]: knocks [AD_SKIP_SECONDS] (15 min) off the
 *    timer. Call this ONLY from AdHandler's onRewardEarned for
 *    AdPlacement.SPEED_UP_CRAFTING (wiring shown in ForgeFlailGame).
 *
 * Performance: [update] runs every frame — it is a plain indexed loop over a
 * fixed slot array with zero allocation. Slots are long-lived mutable holders,
 * not per-craft objects.
 */
class CraftingManager(
    private val economy: EconomyManager,
    slotCount: Int = DEFAULT_SLOT_COUNT,
) {

    interface Listener {
        fun onCraftStarted(slot: Int, blueprint: Blueprint)
        fun onCraftCompleted(slot: Int, blueprint: Blueprint)
    }

    /** Reusable slot holder; cleared and refilled across crafts, never reallocated. */
    private class Slot {
        var blueprint: Blueprint? = null
        var endTimeUtcMs: Long = 0L
    }

    private val slots = Array(slotCount) { Slot() }

    var listener: Listener? = null

    val slotCount: Int get() = slots.size

    fun isSlotBusy(slot: Int): Boolean = slots[slot].blueprint != null

    /** Check BEFORE spending gems on a chest so a won blueprint is never lost. */
    fun hasFreeSlot(): Boolean {
        for (i in slots.indices) if (slots[i].blueprint == null) return true
        return false
    }

    fun blueprintIn(slot: Int): Blueprint? = slots[slot].blueprint

    /** Starts a craft in the first free slot; returns the slot index or [NO_SLOT]. */
    fun startCraft(blueprint: Blueprint, nowUtcMs: Long): Int {
        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.blueprint == null) {
                slot.blueprint = blueprint
                slot.endTimeUtcMs = nowUtcMs + blueprint.craftDurationSeconds * 1000L
                listener?.onCraftStarted(i, blueprint)
                return i
            }
        }
        return NO_SLOT
    }

    /** Frame tick. Allocation-free; fires [Listener.onCraftCompleted] on finish. */
    fun update(nowUtcMs: Long) {
        for (i in slots.indices) {
            val slot = slots[i]
            val blueprint = slot.blueprint ?: continue
            if (nowUtcMs >= slot.endTimeUtcMs) {
                slot.blueprint = null
                listener?.onCraftCompleted(i, blueprint)
            }
        }
    }

    fun remainingSeconds(slot: Int, nowUtcMs: Long): Long {
        if (slots[slot].blueprint == null) return 0L
        return ((slots[slot].endTimeUtcMs - nowUtcMs).coerceAtLeast(0L) + 999L) / 1000L
    }

    /** Current gem price to finish [slot] instantly: 1 gem per started 3 minutes. */
    fun gemSkipCost(slot: Int, nowUtcMs: Long): Long {
        val remaining = remainingSeconds(slot, nowUtcMs)
        if (remaining <= 0L) return 0L
        return (remaining + GEM_SKIP_GRANULARITY_SECONDS - 1L) / GEM_SKIP_GRANULARITY_SECONDS
    }

    /**
     * Instant-finish with gems. Show [gemSkipCost] on the button and require an
     * explicit tap — never chain this into another purchase flow automatically.
     */
    fun skipWithGems(slot: Int, nowUtcMs: Long): Boolean {
        val blueprint = slots[slot].blueprint ?: return false
        val cost = gemSkipCost(slot, nowUtcMs)
        if (cost > 0L && !economy.spendGems(cost)) return false
        slots[slot].blueprint = null
        listener?.onCraftCompleted(slot, blueprint)
        return true
    }

    /**
     * Rewarded-ad time skip: subtracts [AD_SKIP_SECONDS] from the timer. If that
     * empties the timer, the craft completes on the next [update] tick.
     * MUST only be called after the ad SDK confirms the reward was earned.
     */
    fun applyRewardedAdSkip(slot: Int) {
        if (slots[slot].blueprint == null) return
        slots[slot].endTimeUtcMs -= AD_SKIP_SECONDS * 1000L
    }

    companion object {
        const val NO_SLOT = -1
        const val DEFAULT_SLOT_COUNT = 2
        const val GEM_SKIP_GRANULARITY_SECONDS = 180L
        const val AD_SKIP_SECONDS = 15L * 60L
    }
}
