package com.forgeflail.core.runner

import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.TimeUtils
import com.forgeflail.core.economy.EconomyManager

/**
 * Subway-style endless-runner simulation: the player auto-runs down a 3-lane
 * track; swipes change lane or jump; coins bank into the shared gold economy;
 * hitting a barrier ends the run (one rewarded-ad revive per run).
 *
 * World convention: the player stays at z=0 and the world scrolls toward +Z,
 * so entities spawn far ahead at [SPAWN_Z] and despawn once behind the camera.
 *
 * PERFORMANCE: [update] runs every frame with zero allocation. Coins and
 * barriers live in fixed pre-allocated pools ([coins]/[barriers]) that are
 * recycled by flipping [Entity.active]; the renderer binds one ModelInstance
 * per pool slot.
 */
class RunnerSystem(
    private val economy: EconomyManager,
    seed: Long = TimeUtils.nanoTime(),
) {

    enum class State { READY, RUNNING, DEAD }

    interface Listener {
        fun onCoinCollected(runCoins: Int)
        fun onPlayerDied(distanceMeters: Int, runCoins: Int)
    }

    /** Reusable pooled world object; never reallocated after construction. */
    class Entity {
        var lane = 0
        var z = 0f
        var active = false
    }

    val coins = Array(COIN_POOL_SIZE) { Entity() }
    val barriers = Array(BARRIER_POOL_SIZE) { Entity() }

    var state = State.READY
        private set
    var laneIndex = 1
        private set
    var playerX = 0f
        private set
    var playerY = 0f
        private set
    var speed = START_SPEED
        private set
    var runCoins = 0
        private set
    var reviveUsed = false
        private set

    /** Gems earned this run (1 per [METERS_PER_GEM] m) — the free path to chests. */
    var runGems = 0
        private set

    val distanceMeters: Int get() = distance.toInt()
    val canRevive: Boolean get() = state == State.DEAD && !reviveUsed

    /** Vertical velocity, for the renderer's cartoon air-tilt. */
    val airVelocityY: Float get() = velocityY

    var listener: Listener? = null

    private var distance = 0f
    private var velocityY = 0f
    private var distanceToNextRow = 0f
    private var bankedCoins = 0
    private var bankedMeters = 0
    private val random = RandomXS128(seed)

    fun startRun() {
        for (i in coins.indices) coins[i].active = false
        for (i in barriers.indices) barriers[i].active = false
        laneIndex = 1
        playerX = 0f
        playerY = 0f
        velocityY = 0f
        speed = START_SPEED
        distance = 0f
        runCoins = 0
        bankedCoins = 0
        bankedMeters = 0
        runGems = 0
        reviveUsed = false
        distanceToNextRow = FIRST_ROW_GRACE
        state = State.RUNNING
    }

    fun moveLeft() {
        if (state == State.RUNNING && laneIndex > 0) laneIndex--
    }

    fun moveRight() {
        if (state == State.RUNNING && laneIndex < LANE_X.size - 1) laneIndex++
    }

    fun jump() {
        if (state == State.RUNNING && playerY == 0f) velocityY = JUMP_VELOCITY
    }

    /**
     * One-per-run second chance. MUST only be called after the rewarded ad
     * confirms the reward (AdPlacement.REVIVE_RUN) — never sold for gems:
     * "pay to undo a loss" is off-limits for this game's under-16 audience.
     * Clears hazards around the player so revival isn't an instant re-death.
     */
    fun revive() {
        if (!canRevive) return
        reviveUsed = true
        for (i in barriers.indices) {
            val b = barriers[i]
            if (b.active && b.z > REVIVE_CLEAR_FROM_Z) b.active = false
        }
        playerY = 0f
        velocityY = 0f
        state = State.RUNNING
    }

    /**
     * Clears a finished (DEAD) run so menu/character-select screens show a
     * clean idle scene instead of the frozen crash. Banked rewards are kept —
     * [die] already granted them.
     */
    fun resetToIdle() {
        for (i in coins.indices) coins[i].active = false
        for (i in barriers.indices) barriers[i].active = false
        laneIndex = 1
        playerX = 0f
        playerY = 0f
        velocityY = 0f
        state = State.READY
    }

    /** Frame tick; no-op unless RUNNING. Zero allocation. */
    fun update(delta: Float) {
        if (state != State.RUNNING) return

        speed = (speed + SPEED_RAMP_PER_SEC * delta).coerceAtMost(MAX_SPEED)
        distance += speed * delta

        // Lane change: exponential ease toward the target lane's x.
        val targetX = LANE_X[laneIndex]
        playerX += (targetX - playerX) * (LANE_LERP_SPEED * delta).coerceAtMost(1f)

        // Jump physics.
        if (playerY > 0f || velocityY > 0f) {
            velocityY += GRAVITY * delta
            playerY += velocityY * delta
            if (playerY <= 0f) {
                playerY = 0f
                velocityY = 0f
            }
        }

        // Scroll + collide. Collision is lane-quantized with a z window at the
        // player's fixed z=0 — forgiving and cheap, right for a kids' runner.
        for (i in coins.indices) {
            val coin = coins[i]
            if (!coin.active) continue
            coin.z += speed * delta
            if (coin.z > DESPAWN_Z) {
                coin.active = false
            } else if (coin.lane == laneIndex &&
                coin.z > -COLLIDE_HALF_DEPTH && coin.z < COLLIDE_HALF_DEPTH &&
                playerY < COIN_GRAB_MAX_Y
            ) {
                coin.active = false
                runCoins++
                listener?.onCoinCollected(runCoins)
            }
        }
        for (i in barriers.indices) {
            val barrier = barriers[i]
            if (!barrier.active) continue
            barrier.z += speed * delta
            if (barrier.z > DESPAWN_Z) {
                barrier.active = false
            } else if (barrier.lane == laneIndex &&
                barrier.z > -COLLIDE_HALF_DEPTH && barrier.z < COLLIDE_HALF_DEPTH &&
                playerY < BARRIER_CLEAR_HEIGHT
            ) {
                die()
                return
            }
        }

        // Spawn the next obstacle/coin row once we've run far enough.
        distanceToNextRow -= speed * delta
        if (distanceToNextRow <= 0f) {
            spawnRow()
            distanceToNextRow = ROW_SPACING_MIN + random.nextFloat() * (ROW_SPACING_MAX - ROW_SPACING_MIN)
        }
    }

    // -----------------------------------------------------------------------

    private fun die() {
        state = State.DEAD
        // Bank only what isn't banked yet (a revived run dies twice).
        val unbankedCoins = runCoins - bankedCoins
        if (unbankedCoins > 0) economy.addGold(unbankedCoins * COIN_GOLD_VALUE)
        bankedCoins = runCoins
        val gems = (distanceMeters - bankedMeters) / METERS_PER_GEM
        if (gems > 0) {
            economy.addGems(gems.toLong())
            runGems += gems
        }
        bankedMeters = distanceMeters
        listener?.onPlayerDied(distanceMeters, runCoins)
    }

    /** Row patterns; a free lane is always guaranteed — no unwinnable spawns. */
    private fun spawnRow() {
        when (random.nextInt(10)) {
            0, 1, 2, 3 -> { // coin trail down one lane
                val lane = random.nextInt(3)
                val count = 3 + random.nextInt(3)
                for (k in 0 until count) spawnCoin(lane, SPAWN_Z - k * COIN_TRAIL_SPACING)
            }
            4, 5 -> { // coin row across all lanes
                for (lane in 0..2) spawnCoin(lane, SPAWN_Z)
            }
            6, 7, 8 -> { // single barrier, coin marks an escape lane
                val blocked = random.nextInt(3)
                spawnBarrier(blocked, SPAWN_Z)
                val free = (blocked + 1 + random.nextInt(2)) % 3
                spawnCoin(free, SPAWN_Z)
            }
            else -> { // double barrier, coin trail down the only free lane
                val free = random.nextInt(3)
                for (lane in 0..2) if (lane != free) spawnBarrier(lane, SPAWN_Z)
                spawnCoin(free, SPAWN_Z)
                spawnCoin(free, SPAWN_Z - COIN_TRAIL_SPACING)
            }
        }
    }

    private fun spawnCoin(lane: Int, z: Float) {
        for (i in coins.indices) {
            val e = coins[i]
            if (!e.active) {
                e.lane = lane
                e.z = z
                e.active = true
                return
            }
        }
        // Pool exhausted: silently skip — cosmetic-only under extreme spam.
    }

    private fun spawnBarrier(lane: Int, z: Float) {
        for (i in barriers.indices) {
            val e = barriers[i]
            if (!e.active) {
                e.lane = lane
                e.z = z
                e.active = true
                return
            }
        }
    }

    companion object {
        val LANE_X = floatArrayOf(-2f, 0f, 2f)
        const val COIN_POOL_SIZE = 48
        const val BARRIER_POOL_SIZE = 16
        const val COIN_GOLD_VALUE = 1L
        const val METERS_PER_GEM = 25

        // Tuned gentle for young players: slower cap, softer ramp.
        const val START_SPEED = 8f
        const val MAX_SPEED = 18f
        const val SPEED_RAMP_PER_SEC = 0.15f
        const val LANE_LERP_SPEED = 11f

        const val JUMP_VELOCITY = 8.5f
        const val GRAVITY = -22f
        const val BARRIER_CLEAR_HEIGHT = 0.85f // barrier is 0.8 tall; jump peak ~1.6
        const val COIN_GRAB_MAX_Y = 1.3f
        const val COLLIDE_HALF_DEPTH = 0.5f

        const val SPAWN_Z = -65f
        const val DESPAWN_Z = 8f
        const val REVIVE_CLEAR_FROM_Z = -16f
        const val FIRST_ROW_GRACE = 18f
        const val ROW_SPACING_MIN = 6f
        const val ROW_SPACING_MAX = 11f
        const val COIN_TRAIL_SPACING = 1.7f
    }
}
