package com.forgeflail.core.runner

import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.TimeUtils
import com.forgeflail.core.economy.EconomyManager

/**
 * Subway-style endless-runner simulation: the player auto-runs down a 3-lane
 * track; swipes change lane or jump; coins bank into the shared gold economy;
 * hitting a barrier ends the run (one rewarded-ad revive per run).
 *
 * Difficulty tuning (speeds, row spacing, spawn odds) comes from the
 * [LevelSpec] passed to [startRun]; reaching its goal distance fires
 * [Listener.onLevelCompleted] once per run (the level-unlock hook).
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

    enum class State { READY, RUNNING, PAUSED, DEAD }

    interface Listener {
        fun onCoinCollected(runCoins: Int)
        fun onPlayerDied(distanceMeters: Int, runCoins: Int)

        /** Goal distance reached (fires at most once per run, mid-run). */
        fun onLevelCompleted(level: LevelSpec)

        /** Power gem grabbed: boost (faster + shielded) is now active. */
        fun onBoostStarted()

        /** Boost distance spent; normal speed and mortality are back. */
        fun onBoostEnded()

        /** A shielded run smashed through a barrier in [lane] (mid-run FX hook). */
        fun onBarrierSmashed(lane: Int)
    }

    /** Reusable pooled world object; never reallocated after construction. */
    class Entity {
        var lane = 0
        var z = 0f
        var active = false
    }

    val coins = Array(COIN_POOL_SIZE) { Entity() }
    val barriers = Array(BARRIER_POOL_SIZE) { Entity() }

    /** Rare boost pickups ("power gems"); spawned every [POWER_GEM_MIN_ROWS]+ rows. */
    val powerGems = Array(POWER_GEM_POOL_SIZE) { Entity() }

    var state = State.READY
        private set
    var level: LevelSpec = LevelCatalog.LEVELS.first()
        private set
    var levelCompleted = false
        private set
    var laneIndex = 1
        private set
    var playerX = 0f
        private set
    var playerY = 0f
        private set
    var speed = level.startSpeed
        private set
    var runCoins = 0
        private set
    var reviveUsed = false
        private set

    /** Gems earned this run (1 per [METERS_PER_GEM] m) — the free path to chests. */
    var runGems = 0
        private set

    /** Boost meters remaining; while > 0 the run is faster and barrier-proof. */
    var boostMetersLeft = 0f
        private set
    val isBoosted: Boolean get() = boostMetersLeft > 0f

    /** 0..1 fraction of boost remaining — drives the aura's shrink/fade. */
    val boostFraction: Float get() = boostMetersLeft / BOOST_DISTANCE_METERS

    /** World scroll speed this frame — the renderer's counterpart of [speed]. */
    val scrollSpeed: Float
        get() = if (boostMetersLeft > 0f) speed * BOOST_SPEED_MULTIPLIER else speed

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
    private var rowsUntilPowerGem = 0
    private val random = RandomXS128(seed)

    fun startRun(level: LevelSpec) {
        this.level = level
        levelCompleted = false
        for (i in coins.indices) coins[i].active = false
        for (i in barriers.indices) barriers[i].active = false
        for (i in powerGems.indices) powerGems[i].active = false
        laneIndex = 1
        playerX = 0f
        playerY = 0f
        velocityY = 0f
        speed = level.startSpeed
        distance = 0f
        runCoins = 0
        bankedCoins = 0
        bankedMeters = 0
        runGems = 0
        boostMetersLeft = 0f
        rowsUntilPowerGem = nextPowerGemInterval()
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
        if (state == State.RUNNING && playerY == 0f) velocityY = level.jumpVelocity
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

    /** Freezes the run in place (pause button / app going to background). */
    fun pauseRun() {
        if (state == State.RUNNING) state = State.PAUSED
    }

    fun resumeRun() {
        if (state == State.PAUSED) state = State.RUNNING
    }

    /**
     * Leaving a paused run for the menu: everything earned so far is banked
     * (never punish quitting — under-16 kindness), then back to idle.
     */
    fun quitRun() {
        if (state != State.PAUSED) return
        bankRewards()
        resetToIdle()
    }

    /**
     * Clears a finished (DEAD) run so menu/character-select screens show a
     * clean idle scene instead of the frozen crash. Banked rewards are kept —
     * [die] already granted them.
     */
    fun resetToIdle() {
        for (i in coins.indices) coins[i].active = false
        for (i in barriers.indices) barriers[i].active = false
        for (i in powerGems.indices) powerGems[i].active = false
        laneIndex = 1
        playerX = 0f
        playerY = 0f
        velocityY = 0f
        boostMetersLeft = 0f
        state = State.READY
    }

    /** Frame tick; no-op unless RUNNING. Zero allocation. */
    fun update(delta: Float) {
        if (state != State.RUNNING) return

        speed = (speed + level.speedRampPerSec * delta).coerceAtMost(level.maxSpeed)
        // Boost: the world scrolls faster and the shield below smashes barriers.
        val moveSpeed = scrollSpeed
        distance += moveSpeed * delta
        if (boostMetersLeft > 0f) {
            boostMetersLeft -= moveSpeed * delta
            if (boostMetersLeft <= 0f) {
                boostMetersLeft = 0f
                // Fair-play: a boost may never expire INTO a barrier — clear
                // anything already too close to react to at these speeds.
                for (i in barriers.indices) {
                    val b = barriers[i]
                    if (b.active && b.z > BOOST_END_CLEAR_Z) b.active = false
                }
                listener?.onBoostEnded()
            }
        }

        if (!levelCompleted && level.goalMeters > 0 && distance >= level.goalMeters) {
            levelCompleted = true
            listener?.onLevelCompleted(level)
        }

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
            coin.z += moveSpeed * delta
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
        for (i in powerGems.indices) {
            val gem = powerGems[i]
            if (!gem.active) continue
            gem.z += moveSpeed * delta
            if (gem.z > DESPAWN_Z) {
                gem.active = false
            } else if (gem.lane == laneIndex &&
                gem.z > -COLLIDE_HALF_DEPTH && gem.z < COLLIDE_HALF_DEPTH &&
                playerY < COIN_GRAB_MAX_Y
            ) {
                gem.active = false
                boostMetersLeft = BOOST_DISTANCE_METERS
                listener?.onBoostStarted()
            }
        }
        for (i in barriers.indices) {
            val barrier = barriers[i]
            if (!barrier.active) continue
            barrier.z += moveSpeed * delta
            if (barrier.z > DESPAWN_Z) {
                barrier.active = false
            } else if (barrier.lane == laneIndex &&
                barrier.z > -COLLIDE_HALF_DEPTH && barrier.z < COLLIDE_HALF_DEPTH &&
                playerY < BARRIER_CLEAR_HEIGHT
            ) {
                if (boostMetersLeft > 0f) {
                    // Shielded: smash through and keep running from this spot.
                    barrier.active = false
                    listener?.onBarrierSmashed(barrier.lane)
                } else {
                    die()
                    return
                }
            }
        }

        // Spawn the next obstacle/coin row once we've run far enough.
        distanceToNextRow -= moveSpeed * delta
        if (distanceToNextRow <= 0f) {
            spawnRow()
            distanceToNextRow = level.rowSpacingMin +
                random.nextFloat() * (level.rowSpacingMax - level.rowSpacingMin)
        }
    }

    // -----------------------------------------------------------------------

    private fun die() {
        state = State.DEAD
        bankRewards()
        listener?.onPlayerDied(distanceMeters, runCoins)
    }

    /** Banks only what isn't banked yet (a revived run dies twice). */
    private fun bankRewards() {
        val unbankedCoins = runCoins - bankedCoins
        if (unbankedCoins > 0) economy.addGold(unbankedCoins * COIN_GOLD_VALUE)
        bankedCoins = runCoins
        val gems = (distanceMeters - bankedMeters) / METERS_PER_GEM
        if (gems > 0) {
            economy.addGems(gems.toLong())
            runGems += gems
        }
        bankedMeters = distanceMeters
    }

    /**
     * Row patterns; a free lane is always guaranteed — no unwinnable spawns.
     * Pattern odds are the level's d10 thresholds (see [LevelSpec]).
     */
    private fun spawnRow() {
        val roll = random.nextInt(10)
        val safeLane: Int // a lane this row leaves barrier-free
        when {
            roll <= level.coinTrailRoll -> { // coin trail down one lane
                val lane = random.nextInt(3)
                val count = 3 + random.nextInt(3)
                for (k in 0 until count) spawnCoin(lane, SPAWN_Z - k * COIN_TRAIL_SPACING)
                safeLane = lane
            }
            roll <= level.coinRowRoll -> { // coin row across all lanes
                for (lane in 0..2) spawnCoin(lane, SPAWN_Z)
                safeLane = random.nextInt(3)
            }
            roll <= level.singleBarrierRoll -> { // single barrier, coin marks an escape lane
                val blocked = random.nextInt(3)
                spawnBarrier(blocked, SPAWN_Z)
                val free = (blocked + 1 + random.nextInt(2)) % 3
                spawnCoin(free, SPAWN_Z)
                safeLane = free
            }
            else -> { // double barrier, coin trail down the only free lane
                val free = random.nextInt(3)
                for (lane in 0..2) if (lane != free) spawnBarrier(lane, SPAWN_Z)
                spawnCoin(free, SPAWN_Z)
                spawnCoin(free, SPAWN_Z - COIN_TRAIL_SPACING)
                safeLane = free
            }
        }
        // The occasional boost pickup rides just behind a row, always in a
        // lane that row leaves runnable.
        if (--rowsUntilPowerGem <= 0) {
            rowsUntilPowerGem = nextPowerGemInterval()
            spawnPowerGem(safeLane, SPAWN_Z - POWER_GEM_ROW_OFFSET)
        }
    }

    private fun nextPowerGemInterval() =
        POWER_GEM_MIN_ROWS + random.nextInt(POWER_GEM_ROW_JITTER)

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

    private fun spawnPowerGem(lane: Int, z: Float) {
        for (i in powerGems.indices) {
            val e = powerGems[i]
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
        const val POWER_GEM_POOL_SIZE = 3
        const val COIN_GOLD_VALUE = 1L
        const val METERS_PER_GEM = 25

        // Power-gem boost: auto-sprint with a barrier-smashing shield. Earned
        // in-run only (a pickup, never sold) so it stays Families-compliant.
        const val BOOST_SPEED_MULTIPLIER = 1.5f
        const val BOOST_DISTANCE_METERS = 60f
        const val BOOST_END_CLEAR_Z = -6f
        const val POWER_GEM_MIN_ROWS = 12
        const val POWER_GEM_ROW_JITTER = 10 // 12..21 rows between pickups
        const val POWER_GEM_ROW_OFFSET = 2.2f

        // Speeds/row spacing live in LevelSpec — per-level difficulty is data.
        const val LANE_LERP_SPEED = 11f

        const val JUMP_VELOCITY = 8.5f // default arc; LevelSpec.jumpVelocity can flatten it
        const val GRAVITY = -22f
        const val BARRIER_CLEAR_HEIGHT = 0.85f // barrier is 0.8 tall; default jump peak ~1.6
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
