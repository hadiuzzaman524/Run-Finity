package com.forgeflail.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.forgeflail.core.assets.VoxelModelFactory
import com.forgeflail.core.characters.CharacterCatalog
import com.forgeflail.core.characters.CharacterSpec
import com.forgeflail.core.economy.EconomyManager
import com.forgeflail.core.gacha.Blueprint
import com.forgeflail.core.gacha.GachaSystem
import com.forgeflail.core.gacha.Rarity
import com.forgeflail.core.monetization.AdHandler
import com.forgeflail.core.monetization.AdPlacement
import com.forgeflail.core.monetization.BillingHandler
import com.forgeflail.core.monetization.DebugAdHandler
import com.forgeflail.core.monetization.DebugBillingHandler
import com.forgeflail.core.monetization.GemStoreCatalog
import com.forgeflail.core.monetization.PurchaseCallback
import com.forgeflail.core.monetization.RewardedAdCallback
import com.forgeflail.core.runner.LevelCatalog
import com.forgeflail.core.runner.LevelSpec
import com.forgeflail.core.runner.LevelTheme
import com.forgeflail.core.runner.RunnerSystem
import com.forgeflail.core.save.SaveManager
import com.forgeflail.core.ui.GameHud

/**
 * Forge & Flail: Coin Run — root ApplicationListener for the voxel endless
 * runner. The blacksmith kid auto-runs down a 3-lane road; swipe left/right to
 * change lanes, swipe up to jump, grab coins, dodge barriers.
 *
 * Monetization loops (all opt-in, under-16 compliant):
 *  - coins -> gold banked at run end; "x2 coins" rewarded ad on the game-over panel
 *  - one "revive" rewarded ad per run (never sold for gems)
 *  - gacha chest (gems) unlocks the sword skin carried on the runner's back
 *  - gem packs through the injected [BillingHandler]
 *
 * PERFORMANCE CONTRACT: [render] performs ZERO heap allocation. World objects
 * live in [RunnerSystem]'s fixed pools with one pre-allocated [ModelInstance]
 * per slot; callbacks are single fields; transforms are recomputed in place.
 */
class ForgeFlailGame(
    private val billing: BillingHandler = DebugBillingHandler(),
    private val ads: AdHandler = DebugAdHandler(),
) : ApplicationAdapter() {

    // --- 3D scene ---------------------------------------------------------
    private lateinit var camera: PerspectiveCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var voxelFactory: VoxelModelFactory

    // One track/barrier instance set per level theme; render picks by
    // [selectedLevelIndex]. Tile z-positions are shared (same tile length).
    private lateinit var trackTilesByLevel: kotlin.Array<kotlin.Array<ModelInstance>>
    private val trackTileZ = floatArrayOf(-10f, -10f - VoxelModelFactory.TRACK_TILE_LENGTH)

    // Articulated player, one part-set per roster character; the selected
    // index picks which set renders. Limbs animate separately for the run cycle.
    private lateinit var bodies: kotlin.Array<ModelInstance>
    private lateinit var legsLeft: kotlin.Array<ModelInstance>
    private lateinit var legsRight: kotlin.Array<ModelInstance>
    private lateinit var armsLeft: kotlin.Array<ModelInstance>
    private lateinit var armsRight: kotlin.Array<ModelInstance>
    private lateinit var swordInstance: ModelInstance
    private lateinit var coinInstances: kotlin.Array<ModelInstance>
    private lateinit var powerGemInstances: kotlin.Array<ModelInstance>
    private lateinit var barrierInstancesByLevel: kotlin.Array<kotlin.Array<ModelInstance>>
    private lateinit var shadowInstance: ModelInstance

    // Coin-burst sparkles: fixed pool, plain float state, zero allocation.
    private lateinit var sparkleInstances: kotlin.Array<ModelInstance>
    private val sparkleX = FloatArray(MAX_SPARKLES)
    private val sparkleY = FloatArray(MAX_SPARKLES)
    private val sparkleZ = FloatArray(MAX_SPARKLES)
    private val sparkleVX = FloatArray(MAX_SPARKLES)
    private val sparkleVY = FloatArray(MAX_SPARKLES)
    private val sparkleVZ = FloatArray(MAX_SPARKLES)
    private val sparkleLife = FloatArray(MAX_SPARKLES)

    // --- systems ------------------------------------------------------------
    private lateinit var save: SaveManager
    lateinit var economy: EconomyManager
        private set
    lateinit var gacha: GachaSystem
        private set
    lateinit var runner: RunnerSystem
        private set
    private var hud: GameHud? = null
    private var coinSound: Sound? = null
    private var jumpSound: Sound? = null
    private var crashSound: Sound? = null

    // --- run/session state (plain fields; no per-frame objects) -------------
    private var created = false
    private var bestScore = 0
    private var equippedRarity = Rarity.COMMON
    private var coinsDoubledThisRun = false
    private var coinDingCooldown = 0f
    private var coinSpinDegrees = 0f
    private var runPhase = 0f
    private var playerYaw = 180f // faces the camera in the menu, -Z when running
    private var selectedCharacter = 0
    private var characterSelectOpen = false
    private var deathTilt = 0f
    private var boostTrailCooldown = 0f

    // Heroes screen: the roster index on the turntable. Browsing is free;
    // [selectedCharacter] (the equipped hero) only changes through
    // [equipOrUnlockCharacter], so locked heroes can be admired but not run.
    private var previewCharacter = 0

    // Bitmask of unlocked roster indices (bit i = ROSTER[i]); persisted.
    // Heroes are cosmetic-only and every cost is payable with run-earned gems.
    private var unlockedCharacters = 0

    // Character-select turntable: drags/flings spin the runner; the idle
    // showcase spin resumes after a short hands-off pause.
    private var selectDragCooldown = 0f
    private var selectYawVelocity = 0f

    // Camera rig eased between chase framing (menu/run) and the select
    // close-up that keeps the runner in the top section, above the panel.
    private var camY = CHASE_CAM_Y
    private var camZ = CHASE_CAM_Z
    private var lookY = CHASE_LOOK_Y
    private var lookZ = CHASE_LOOK_Z

    // Level progression: skill-unlocked only (see LevelCatalog). Read by the HUD.
    var selectedLevelIndex = 0
        private set
    var unlockedLevels = 1
        private set

    /** The equipped hero's spec — the Home screen greets the player with it. */
    val equippedCharacter: CharacterSpec
        get() = CharacterCatalog.ROSTER[selectedCharacter]

    // Toast strings precomputed at create(): onLevelCompleted fires mid-run
    // inside the render path, which must not build strings (hard rule 1).
    private lateinit var levelUnlockToasts: kotlin.Array<String>
    private lateinit var levelLockedToasts: kotlin.Array<String>

    // ========================================================================
    // Pre-allocated callbacks — one instance each for the app's lifetime.
    // ========================================================================

    private val runnerListener = object : RunnerSystem.Listener {
        override fun onCoinCollected(runCoins: Int) {
            // Instant feedback: ding (slight pitch variation), counter, sparkles.
            // The ding is rate-limited: fast coin trails otherwise spam the
            // sound pool, a frame-hitch source on budget phones.
            if (coinDingCooldown <= 0f) {
                coinSound?.play(0.6f, 0.95f + MathUtils.random(0.12f), 0f)
                coinDingCooldown = COIN_DING_MIN_INTERVAL
            }
            hud?.onCoinCollected(runCoins)
            spawnCoinBurst(runner.playerX, 1.0f, 0f)
        }

        override fun onPlayerDied(distanceMeters: Int, runCoins: Int) {
            crashSound?.play(0.9f)
            val newBest = distanceMeters > bestScore
            if (newBest) bestScore = distanceMeters
            hud?.onRunEnded(distanceMeters, runCoins, bestScore, newBest, runner.canRevive, !coinsDoubledThisRun && runCoins > 0)
        }

        override fun onLevelCompleted(level: LevelSpec) {
            coinSound?.play(0.8f, 1.3f, 0f) // triumphant high ding; run continues
            val next = selectedLevelIndex + 1
            if (next < LevelCatalog.LEVELS.size && unlockedLevels <= next) {
                unlockedLevels = next + 1
                hud?.toast(levelUnlockToasts[next])
            } else {
                hud?.toast(GOAL_REACHED_TOAST)
            }
        }

        override fun onBoostStarted() {
            coinSound?.play(0.9f, 1.5f, 0f)
            hud?.toast(BOOST_TOAST) // precomputed: fires inside the render path
        }

        override fun onBoostEnded() {
            boostTrailCooldown = 0f
        }

        override fun onBarrierSmashed(lane: Int) {
            crashSound?.play(0.25f, 1.7f, 0f) // light "crunch", not the death crash
            spawnCoinBurst(RunnerSystem.LANE_X[lane], 0.8f, 0f)
        }
    }

    private val gachaListener = object : GachaSystem.Listener {
        override fun onChestOpened(blueprint: Blueprint) {
            hud?.toast("${blueprint.rarity.displayName} sword unlocked: ${blueprint.displayName}!")
        }

        override fun onChestDenied(gemsShort: Long) {
            hud?.toast("Need $gemsShort more gems. Run to earn more!")
        }
    }

    private val reviveAdCallback = object : RewardedAdCallback {
        override fun onRewardEarned(placement: AdPlacement) {
            runner.revive()
            hud?.setRunning()
            hud?.toast("Back on your feet!")
        }

        override fun onRewardNotEarned(placement: AdPlacement) {
            hud?.toast("Ad not completed. Revive still available")
        }
    }

    private val doubleCoinsAdCallback = object : RewardedAdCallback {
        override fun onRewardEarned(placement: AdPlacement) {
            coinsDoubledThisRun = true
            economy.addGold(runner.runCoins * RunnerSystem.COIN_GOLD_VALUE)
            hud?.toast("Coins doubled: +${runner.runCoins} bonus gold!")
        }

        override fun onRewardNotEarned(placement: AdPlacement) {
            hud?.toast("Ad not completed. Coins kept at the normal amount")
        }
    }

    /** Grant path for gem IAPs — fires only after the store confirms payment. */
    private val purchaseCallback = object : PurchaseCallback {
        override fun onPurchaseGranted(productId: String) {
            economy.addGems(GemStoreCatalog.gemsFor(productId))
        }

        override fun onPurchaseFailed(productId: String, reason: String) {
            Gdx.app.log(TAG, "Purchase failed for $productId: $reason")
        }
    }

    /**
     * Swipe input: horizontal fling = lane change, upward fling = jump.
     * On the character-select screen the same gestures become a turntable:
     * drag to spin the runner, fling for momentum.
     */
    private val gestureListener = object : GestureDetector.GestureAdapter() {
        override fun fling(velocityX: Float, velocityY: Float, button: Int): Boolean {
            if (characterSelectOpen) {
                selectYawVelocity = velocityX * FLING_YAW_FACTOR
                selectDragCooldown = SELECT_SPIN_RESUME_SECONDS
                return true
            }
            if (runner.state != RunnerSystem.State.RUNNING) return false
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                if (velocityX > 0f) runner.moveRight() else runner.moveLeft()
            } else if (velocityY < 0f) { // screen y points down: negative = swipe up
                tryJump()
            }
            return true
        }

        override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
            if (!characterSelectOpen) return false
            playerYaw = (playerYaw + deltaX * DRAG_YAW_DEG_PER_PX) % 360f
            selectYawVelocity = 0f
            selectDragCooldown = SELECT_SPIN_RESUME_SECONDS
            return true
        }
    }

    /** Desktop keys: arrows/AD = lanes, space/up/W = jump, enter = start. */
    private val keyInput = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.LEFT, Input.Keys.A -> runner.moveLeft()
                Input.Keys.RIGHT, Input.Keys.D -> runner.moveRight()
                Input.Keys.SPACE, Input.Keys.UP, Input.Keys.W -> tryJump()
                Input.Keys.ENTER -> if (runner.state != RunnerSystem.State.RUNNING) startRun()
                Input.Keys.P, Input.Keys.ESCAPE -> when (runner.state) {
                    RunnerSystem.State.RUNNING -> pauseRun()
                    RunnerSystem.State.PAUSED -> resumeRun()
                    else -> return false
                }
                else -> return false
            }
            return true
        }
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override fun create() {
        // -- Systems
        save = SaveManager()
        economy = EconomyManager(save)
        gacha = GachaSystem(economy).also { it.listener = gachaListener }
        runner = RunnerSystem(economy).also { it.listener = runnerListener }
        bestScore = save.loadBestScore()
        unlockedLevels = save.loadUnlockedLevels(1).coerceIn(1, LevelCatalog.LEVELS.size)
        selectedLevelIndex = save.loadSelectedLevel().coerceIn(0, unlockedLevels - 1)
        levelUnlockToasts = kotlin.Array(LevelCatalog.LEVELS.size) {
            "Level passed! ${LevelCatalog.LEVELS[it].displayName} is now open from the menu!"
        }
        levelLockedToasts = kotlin.Array(LevelCatalog.LEVELS.size) {
            if (it == 0) "" else "Run ${LevelCatalog.LEVELS[it - 1].goalMeters} m on " +
                "${LevelCatalog.LEVELS[it - 1].displayName} to open this!"
        }
        equippedRarity = Rarity.entries[
            save.loadEquippedRarity(Rarity.COMMON.ordinal).coerceIn(0, Rarity.entries.size - 1)
        ]
        // Free starter heroes are always open; OR with the saved mask so a
        // roster reshuffle can never strand a player with zero heroes.
        var starterMask = 0
        for (i in CharacterCatalog.ROSTER.indices) {
            if (CharacterCatalog.ROSTER[i].costGems == 0L) starterMask = starterMask or (1 shl i)
        }
        unlockedCharacters = save.loadUnlockedCharacters(starterMask) or starterMask

        // -- Camera: low chase position for stronger perspective depth.
        camera = PerspectiveCamera(64f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(0f, CHASE_CAM_Y, CHASE_CAM_Z)
            lookAt(0f, CHASE_LOOK_Y, CHASE_LOOK_Z)
            near = 0.1f
            far = 85f
            update()
        }
        // Low ambient + strong warm key = visible face shading on every voxel;
        // fog matched to the sky fades the track into the horizon (depth cue).
        environment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.38f, 0.38f, 0.45f, 1f))
            set(ColorAttribute(ColorAttribute.Fog, 0.45f, 0.71f, 0.95f, 1f))
            add(DirectionalLight().set(1.05f, 0.98f, 0.88f, -0.4f, -0.85f, -0.5f))
            add(DirectionalLight().set(0.22f, 0.24f, 0.32f, 0.55f, -0.3f, 0.6f))
        }
        modelBatch = ModelBatch()

        // -- World content: models built once, one instance per pool slot.
        voxelFactory = VoxelModelFactory()
        trackTilesByLevel = kotlin.Array(LevelCatalog.LEVELS.size) { level ->
            val trackModel = when (LevelCatalog.LEVELS[level].theme) {
                LevelTheme.TOWN_ROAD -> voxelFactory.createTrackTile()
                LevelTheme.VILLAGE_ROAD -> voxelFactory.createVillageTrackTile()
                LevelTheme.MOUNTAIN_PASS -> voxelFactory.createMountainTrackTile()
            }
            kotlin.Array(2) { ModelInstance(trackModel) }
        }
        applyLevelTheme()
        val roster = CharacterCatalog.ROSTER
        bodies = kotlin.Array(roster.size) { ModelInstance(voxelFactory.createCharacterBody(roster[it])) }
        val legModels = kotlin.Array(roster.size) { voxelFactory.createCharacterLeg(roster[it]) }
        legsLeft = kotlin.Array(roster.size) { ModelInstance(legModels[it]) }
        legsRight = kotlin.Array(roster.size) { ModelInstance(legModels[it]) }
        val armModels = kotlin.Array(roster.size) { voxelFactory.createCharacterArm(roster[it]) }
        armsLeft = kotlin.Array(roster.size) { ModelInstance(armModels[it]) }
        armsRight = kotlin.Array(roster.size) { ModelInstance(armModels[it]) }
        swordInstance = ModelInstance(voxelFactory.createSword())
        voxelFactory.applyRarityTraits(swordInstance, equippedRarity)
        val coinModel = voxelFactory.createCoin()
        coinInstances = kotlin.Array(RunnerSystem.COIN_POOL_SIZE) { ModelInstance(coinModel) }
        val powerGemModel = voxelFactory.createPowerGem()
        powerGemInstances = kotlin.Array(RunnerSystem.POWER_GEM_POOL_SIZE) { ModelInstance(powerGemModel) }
        barrierInstancesByLevel = kotlin.Array(LevelCatalog.LEVELS.size) { level ->
            val barrierModel = when (LevelCatalog.LEVELS[level].theme) {
                LevelTheme.TOWN_ROAD -> voxelFactory.createBarrier()
                LevelTheme.VILLAGE_ROAD -> voxelFactory.createLogBarrier()
                LevelTheme.MOUNTAIN_PASS -> voxelFactory.createRockBarrier()
            }
            kotlin.Array(RunnerSystem.BARRIER_POOL_SIZE) { ModelInstance(barrierModel) }
        }

        shadowInstance = ModelInstance(voxelFactory.createBlobShadow())
        val sparkleModel = voxelFactory.createSparkle()
        sparkleInstances = kotlin.Array(MAX_SPARKLES) { ModelInstance(sparkleModel) }

        coinSound = Gdx.audio.newSound(Gdx.files.internal("sfx/coin.wav"))
        jumpSound = Gdx.audio.newSound(Gdx.files.internal("sfx/jump.wav"))
        crashSound = Gdx.audio.newSound(Gdx.files.internal("sfx/crash.wav"))

        // -- HUD + input (UI first, then swipes, then keys)
        val hud = GameHud(this, economy, runner)
        this.hud = hud
        Gdx.input.inputProcessor = InputMultiplexer(hud.stage, GestureDetector(gestureListener), keyInput)

        // First launch: quest intro first, then the hero picker. Returning
        // players land straight on Home.
        val savedCharacter = save.loadSelectedCharacter()
        if (savedCharacter < 0) {
            selectedCharacter = 0
            previewCharacter = 0
            hud.showOnboarding()
        } else {
            selectedCharacter = savedCharacter.coerceIn(0, CharacterCatalog.ROSTER.size - 1)
            previewCharacter = selectedCharacter
            hud.setHome(bestScore)
        }

        created = true
    }

    override fun render() {
        // Spike clamp: after a hitch (GC, app switch) the world slows for a
        // frame instead of teleporting — no popped barriers or missed grabs.
        val delta = Gdx.graphics.deltaTime.coerceAtMost(MAX_FRAME_DELTA)
        if (coinDingCooldown > 0f) coinDingCooldown -= delta
        runner.update(delta)

        // Track tiles scroll with the world (boost included) and leapfrog
        // behind the camera.
        if (runner.state == RunnerSystem.State.RUNNING) {
            for (i in trackTileZ.indices) {
                trackTileZ[i] += runner.scrollSpeed * delta
                if (trackTileZ[i] > TRACK_RESET_Z) trackTileZ[i] -= 2f * VoxelModelFactory.TRACK_TILE_LENGTH
            }
        }
        coinSpinDegrees = (coinSpinDegrees + COIN_SPIN_DEG_PER_SEC * delta) % 360f

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val sky = LevelCatalog.LEVELS[selectedLevelIndex].sky
        Gdx.gl.glClearColor(sky.r, sky.g, sky.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Player pose: yaw eases 180° (facing camera in menus) <-> 0° (running);
        // on the character-select screen he spins as a showcase — by touch
        // drag/fling, or slowly on his own once hands are off. Limbs swing
        // with speed, whole body bobs while grounded.
        val running = runner.state == RunnerSystem.State.RUNNING
        if (characterSelectOpen) {
            if (selectYawVelocity != 0f) {
                playerYaw = (playerYaw + selectYawVelocity * delta) % 360f
                selectYawVelocity *= (1f - SELECT_SPIN_DAMPING * delta).coerceAtLeast(0f)
                if (Math.abs(selectYawVelocity) < SELECT_SPIN_STOP_DEG_PER_SEC) selectYawVelocity = 0f
            }
            if (selectDragCooldown > 0f) selectDragCooldown -= delta
            else playerYaw = (playerYaw + SELECT_IDLE_SPIN_DEG_PER_SEC * delta) % 360f
        } else {
            val targetYaw = if (running) 0f else 180f
            playerYaw += (targetYaw - playerYaw) * (8f * delta).coerceAtMost(1f)
        }
        if (running && runner.playerY == 0f) runPhase += delta * runner.scrollSpeed * 1.4f
        // Boosted runs leave a sparkle trail at the heels — the "power mode is
        // on" cue kids see without reading (pooled sparks, zero allocation).
        if (running && runner.isBoosted) {
            boostTrailCooldown -= delta
            if (boostTrailCooldown <= 0f) {
                boostTrailCooldown = BOOST_TRAIL_INTERVAL
                spawnSparks(runner.playerX, 0.35f, 0.6f, BOOST_TRAIL_SPARKS)
            }
        }
        val bob = if (running && runner.playerY == 0f) Math.abs(MathUtils.sin(runPhase)) * 0.06f else 0f
        val swing = if (running) MathUtils.sin(runPhase) * 38f else 0f
        val px = runner.playerX
        val py = runner.playerY + bob
        // Cartoon posing: lean forward while running, tilt back/forward through a
        // jump arc, and tip over comically when the run ends.
        deathTilt = if (runner.state == RunnerSystem.State.DEAD)
            deathTilt + (76f - deathTilt) * (6f * delta).coerceAtMost(1f)
        else 0f
        val lean = when {
            runner.state == RunnerSystem.State.DEAD -> deathTilt
            running && runner.playerY > 0f -> (runner.airVelocityY * 1.4f).coerceIn(-12f, 12f)
            running -> -7f
            else -> 0f
        }

        // The Heroes turntable shows the browsed hero; everywhere else renders
        // the equipped one.
        val ci = if (characterSelectOpen) previewCharacter else selectedCharacter
        val armX = 0.33f + 0.14f * CharacterCatalog.ROSTER[ci].fat // shoulder tracks belly width
        bodies[ci].transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
        legsLeft[ci].transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(-0.15f, 0.62f, 0f).rotate(Vector3.X, swing)
        legsRight[ci].transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(0.15f, 0.62f, 0f).rotate(Vector3.X, -swing)
        armsLeft[ci].transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(-armX, 1.34f, 0f).rotate(Vector3.X, -swing * 0.8f)
        armsRight[ci].transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(armX, 1.34f, 0f).rotate(Vector3.X, swing * 0.8f)
        // Sword strapped diagonally across the back (blade is +Y in model space).
        swordInstance.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw)
            .translate(0.18f, 0.5f, 0.3f).rotate(Vector3.Z, 35f)
            .scale(0.55f, 0.55f, 0.55f)

        // Camera rig: eases between the chase framing and the select close-up
        // that frames the runner in the top section of the screen, clear of
        // the menu panel below. Soft x-follow keeps side lanes framed; look-at
        // shares the x-offset so it stays a pure translation, no view drift.
        val camEase = (6f * delta).coerceAtMost(1f)
        camY += ((if (characterSelectOpen) SELECT_CAM_Y else CHASE_CAM_Y) - camY) * camEase
        camZ += ((if (characterSelectOpen) SELECT_CAM_Z else CHASE_CAM_Z) - camZ) * camEase
        lookY += ((if (characterSelectOpen) SELECT_LOOK_Y else CHASE_LOOK_Y) - lookY) * camEase
        lookZ += ((if (characterSelectOpen) SELECT_LOOK_Z else CHASE_LOOK_Z) - lookZ) * camEase
        camera.position.set(px * 0.45f, camY, camZ)
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(px * 0.45f, lookY, lookZ)
        camera.update()

        // Blob shadow shrinks as he jumps — the landing cue kids read naturally.
        val shadowScale = (1f - runner.playerY * 0.28f).coerceAtLeast(0.45f)
        shadowInstance.transform
            .setToTranslation(px, 0.015f, 0f)
            .scale(shadowScale, 1f, shadowScale)

        // Sparkle particle tick (fixed pool, float math only).
        for (i in 0 until MAX_SPARKLES) {
            if (sparkleLife[i] <= 0f) continue
            sparkleLife[i] -= delta
            sparkleVY[i] -= 12f * delta
            sparkleX[i] += sparkleVX[i] * delta
            sparkleY[i] += sparkleVY[i] * delta
            sparkleZ[i] += sparkleVZ[i] * delta
        }

        val trackTiles = trackTilesByLevel[selectedLevelIndex]
        val barrierInstances = barrierInstancesByLevel[selectedLevelIndex]
        modelBatch.begin(camera)
        modelBatch.render(trackTiles[0].also { it.transform.setToTranslation(0f, 0f, trackTileZ[0]) }, environment)
        modelBatch.render(trackTiles[1].also { it.transform.setToTranslation(0f, 0f, trackTileZ[1]) }, environment)
        modelBatch.render(bodies[ci], environment)
        modelBatch.render(legsLeft[ci], environment)
        modelBatch.render(legsRight[ci], environment)
        modelBatch.render(armsLeft[ci], environment)
        modelBatch.render(armsRight[ci], environment)
        modelBatch.render(swordInstance, environment)
        for (i in runner.coins.indices) { // indexed loops: no iterator allocation
            val coin = runner.coins[i]
            if (!coin.active) continue
            // Cylinder axis is Y: stand the disc upright, then spin around world Y.
            coinInstances[i].transform
                .setToTranslation(RunnerSystem.LANE_X[coin.lane], COIN_BASE_Y, coin.z)
                .rotate(Vector3.Y, coinSpinDegrees)
                .rotate(Vector3.X, 90f)
            modelBatch.render(coinInstances[i], environment)
        }
        for (i in runner.powerGems.indices) {
            val gem = runner.powerGems[i]
            if (!gem.active) continue
            powerGemInstances[i].transform
                .setToTranslation(RunnerSystem.LANE_X[gem.lane], POWER_GEM_BASE_Y, gem.z)
                .rotate(Vector3.Y, coinSpinDegrees)
            modelBatch.render(powerGemInstances[i], environment)
        }
        for (i in runner.barriers.indices) {
            val barrier = runner.barriers[i]
            if (!barrier.active) continue
            barrierInstances[i].transform.setToTranslation(RunnerSystem.LANE_X[barrier.lane], 0f, barrier.z)
            modelBatch.render(barrierInstances[i], environment)
        }
        for (i in 0 until MAX_SPARKLES) {
            if (sparkleLife[i] <= 0f) continue
            val s = (sparkleLife[i] * 2.5f).coerceAtMost(1f)
            sparkleInstances[i].transform
                .setToTranslation(sparkleX[i], sparkleY[i], sparkleZ[i])
                .rotate(Vector3.Y, coinSpinDegrees)
                .scale(s, s, s)
            modelBatch.render(sparkleInstances[i], environment)
        }
        modelBatch.render(shadowInstance, environment) // blended: batch sorts it last
        modelBatch.end()

        hud?.render(delta)
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
        hud?.resize(width, height)
    }

    override fun pause() {
        if (!created) return
        // Backgrounding mid-run freezes it: coming back lands on the paused
        // panel instead of an instant barrier face-plant.
        if (runner.state == RunnerSystem.State.RUNNING) pauseRun()
        save.persistRunner(
            economy.gold, economy.gems, bestScore, equippedRarity.ordinal, selectedCharacter,
            unlockedCharacters, unlockedLevels, selectedLevelIndex
        )
    }

    override fun dispose() {
        pause() // final save
        modelBatch.dispose()
        voxelFactory.dispose()
        coinSound?.dispose()
        jumpSound?.dispose()
        crashSound?.dispose()
        hud?.dispose()
    }

    private fun tryJump() {
        if (runner.state == RunnerSystem.State.RUNNING && runner.playerY == 0f) {
            jumpSound?.play(0.45f)
        }
        runner.jump()
    }

    /** Six sparks from the pool at the pickup point; skips silently when full. */
    private fun spawnCoinBurst(x: Float, y: Float, z: Float) {
        spawnSparks(x, y, z, SPARKS_PER_COIN)
    }

    private fun spawnSparks(x: Float, y: Float, z: Float, count: Int) {
        var spawned = 0
        for (i in 0 until MAX_SPARKLES) {
            if (sparkleLife[i] > 0f) continue
            sparkleX[i] = x
            sparkleY[i] = y
            sparkleZ[i] = z
            sparkleVX[i] = MathUtils.random(-2.2f, 2.2f)
            sparkleVY[i] = MathUtils.random(2.5f, 5f)
            sparkleVZ[i] = MathUtils.random(-0.5f, 2f)
            sparkleLife[i] = MathUtils.random(0.3f, 0.45f)
            if (++spawned == count) return
        }
    }

    // ========================================================================
    // Entry points for HUD buttons
    // ========================================================================

    /** "RUN!" / "Run again" buttons. */
    fun startRun() {
        characterSelectOpen = false
        coinsDoubledThisRun = false
        deathTilt = 0f
        runner.startRun(LevelCatalog.LEVELS[selectedLevelIndex])
        hud?.setRunning()
    }

    /** Pause button on the run screen (also fired when the app backgrounds). */
    fun pauseRun() {
        if (runner.state != RunnerSystem.State.RUNNING) return
        runner.pauseRun()
        hud?.showPaused()
    }

    /** RESUME on the paused panel. */
    fun resumeRun() {
        if (runner.state != RunnerSystem.State.PAUSED) return
        runner.resumeRun()
        hud?.setRunning()
    }

    /** Level node on the map. Locked picks explain their unlock goal instead. */
    fun selectLevel(index: Int) {
        if (runner.state == RunnerSystem.State.RUNNING) return
        if (index >= unlockedLevels) {
            hud?.toast(levelLockedToasts[index])
            return
        }
        selectedLevelIndex = index
        applyLevelTheme()
        hud?.refreshMap()
    }

    /** Fog must match the level's sky or the horizon seams. Not per-frame. */
    private fun applyLevelTheme() {
        (environment.get(ColorAttribute.Fog) as? ColorAttribute)
            ?.color?.set(LevelCatalog.LEVELS[selectedLevelIndex].sky)
    }

    /** HOME nav tab (also the game-over panel's Home button). */
    fun backToMenu() {
        characterSelectOpen = false
        clearCrashedRun()
        hud?.setHome(bestScore)
    }

    /** MAP nav tab: the delivery route with every level and its unlock goal. */
    fun openMap() {
        characterSelectOpen = false
        clearCrashedRun()
        hud?.showMap()
    }

    /** SHOP nav tab: sword chest (odds disclosed) + gem packs. */
    fun openShop() {
        characterSelectOpen = false
        clearCrashedRun()
        hud?.showShop()
    }

    /** "LET'S GO!" on the first-launch quest intro — straight to the hero picker. */
    fun finishOnboarding() {
        openCharacterSelect()
    }

    /** HEROES nav tab; also the onboarding hero pick on first launch. */
    fun openCharacterSelect() {
        characterSelectOpen = true
        selectDragCooldown = 0f
        selectYawVelocity = 0f
        previewCharacter = selectedCharacter
        clearCrashedRun()
        refreshHeroesScreen()
    }

    /** Leaving the game-over/paused screen: stand back up, clear debris. */
    private fun clearCrashedRun() {
        if (runner.state == RunnerSystem.State.DEAD) runner.resetToIdle()
        if (runner.state == RunnerSystem.State.PAUSED) runner.quitRun() // banks the run so far
        deathTilt = 0f
    }

    /** Prev/next arrows on the Heroes screen — browses without equipping. */
    fun cycleCharacter(direction: Int) {
        val n = CharacterCatalog.ROSTER.size
        previewCharacter = (previewCharacter + direction + n) % n
        refreshHeroesScreen()
    }

    /**
     * The Heroes screen action button: equips the previewed hero, or unlocks
     * it first when it still costs gems (earned free at 1 per 25 m — heroes
     * are cosmetic, so this is expression, never progression).
     */
    fun equipOrUnlockCharacter() {
        val spec = CharacterCatalog.ROSTER[previewCharacter]
        val bit = 1 shl previewCharacter
        if (unlockedCharacters and bit == 0) {
            if (!economy.spendGems(spec.costGems)) {
                hud?.toast("Need ${spec.costGems - economy.gems} more gems. Run to earn more!")
                return
            }
            unlockedCharacters = unlockedCharacters or bit
            hud?.toast("${spec.displayName} joined your team!")
        }
        selectedCharacter = previewCharacter
        refreshHeroesScreen()
    }

    private fun refreshHeroesScreen() {
        hud?.showHeroes(
            CharacterCatalog.ROSTER[previewCharacter],
            unlocked = unlockedCharacters and (1 shl previewCharacter) != 0,
            equipped = previewCharacter == selectedCharacter,
        )
    }

    /** "Revive (watch ad)" — once per run, ad-only by design (see RunnerSystem.revive). */
    fun requestRevive() {
        if (!runner.canRevive) return
        if (ads.isRewardedAdReady(AdPlacement.REVIVE_RUN)) {
            ads.showRewardedAd(AdPlacement.REVIVE_RUN, reviveAdCallback)
        } else {
            hud?.toast("No ad available right now")
        }
    }

    /** "x2 coins (watch ad)" on the game-over panel. */
    fun requestDoubleRunCoins() {
        if (coinsDoubledThisRun || runner.runCoins <= 0) return
        if (ads.isRewardedAdReady(AdPlacement.DOUBLE_RUN_COINS)) {
            ads.showRewardedAd(AdPlacement.DOUBLE_RUN_COINS, doubleCoinsAdCallback)
        } else {
            hud?.toast("No ad available right now")
        }
    }

    /** "Open Chest" — gacha roll that equips a sword skin of the rolled rarity. */
    fun onChestButtonClicked() {
        if (runner.state == RunnerSystem.State.RUNNING) return
        val blueprint = gacha.openChest() ?: return
        equippedRarity = blueprint.rarity
        voxelFactory.applyRarityTraits(swordInstance, equippedRarity)
    }

    /** Gem store buttons. Android impl shows a parental gate before the sheet. */
    fun requestGemPackPurchase(productId: String) {
        billing.launchPurchase(productId, purchaseCallback)
    }

    companion object {
        private const val TAG = "ForgeFlail"
        private const val GOAL_REACHED_TOAST = "Level goal reached - keep running for a new best!"
        private const val BOOST_TOAST = "POWER BOOST! Smash through everything!"
        private const val MAX_FRAME_DELTA = 1f / 30f
        private const val COIN_DING_MIN_INTERVAL = 0.08f
        private const val COIN_BASE_Y = 0.68f
        private const val POWER_GEM_BASE_Y = 0.3f
        private const val BOOST_TRAIL_INTERVAL = 0.07f
        private const val BOOST_TRAIL_SPARKS = 2
        private const val COIN_SPIN_DEG_PER_SEC = 220f
        private const val TRACK_RESET_Z = 30f
        private const val MAX_SPARKLES = 24
        private const val SPARKS_PER_COIN = 6

        // Chase framing: low behind the runner, looking down the track.
        private const val CHASE_CAM_Y = 3.4f
        private const val CHASE_CAM_Z = 6f
        private const val CHASE_LOOK_Y = 1f
        private const val CHASE_LOOK_Z = -8f
        // Select framing: near eye level aiming below the chest so the whole
        // runner sits in the top section of the screen, above the menu panel.
        private const val SELECT_CAM_Y = 1f
        private const val SELECT_CAM_Z = 4.7f
        private const val SELECT_LOOK_Y = -0.05f
        private const val SELECT_LOOK_Z = 0f

        // Select-screen turntable feel.
        private const val SELECT_IDLE_SPIN_DEG_PER_SEC = 40f
        private const val DRAG_YAW_DEG_PER_PX = 0.45f
        private const val FLING_YAW_FACTOR = 0.12f // px/s of fling -> deg/s of spin
        private const val SELECT_SPIN_DAMPING = 3f
        private const val SELECT_SPIN_STOP_DEG_PER_SEC = 5f
        private const val SELECT_SPIN_RESUME_SECONDS = 2.5f
    }
}
