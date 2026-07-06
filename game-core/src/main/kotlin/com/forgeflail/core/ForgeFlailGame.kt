package com.forgeflail.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
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
    private lateinit var trackTiles: kotlin.Array<ModelInstance>
    private val trackTileZ = floatArrayOf(-10f, -10f - VoxelModelFactory.TRACK_TILE_LENGTH)

    // Articulated player: body + limbs animated separately for the run cycle.
    private lateinit var bodyInstance: ModelInstance
    private lateinit var legLeft: ModelInstance
    private lateinit var legRight: ModelInstance
    private lateinit var armLeft: ModelInstance
    private lateinit var armRight: ModelInstance
    private lateinit var swordInstance: ModelInstance
    private lateinit var coinInstances: kotlin.Array<ModelInstance>
    private lateinit var barrierInstances: kotlin.Array<ModelInstance>

    // --- systems ------------------------------------------------------------
    private lateinit var save: SaveManager
    lateinit var economy: EconomyManager
        private set
    lateinit var gacha: GachaSystem
        private set
    lateinit var runner: RunnerSystem
        private set
    private var hud: GameHud? = null

    // --- run/session state (plain fields; no per-frame objects) -------------
    private var created = false
    private var bestScore = 0
    private var equippedRarity = Rarity.COMMON
    private var coinsDoubledThisRun = false
    private var coinSpinDegrees = 0f
    private var runPhase = 0f
    private var playerYaw = 180f // faces the camera in the menu, -Z when running

    // ========================================================================
    // Pre-allocated callbacks — one instance each for the app's lifetime.
    // ========================================================================

    private val runnerListener = object : RunnerSystem.Listener {
        override fun onCoinCollected(runCoins: Int) {
            // HUD polls run stats at 4 Hz; nothing allocated here.
        }

        override fun onPlayerDied(distanceMeters: Int, runCoins: Int) {
            if (distanceMeters > bestScore) bestScore = distanceMeters
            hud?.onRunEnded(distanceMeters, runCoins, bestScore, runner.canRevive, !coinsDoubledThisRun && runCoins > 0)
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

    /** Swipe input: horizontal fling = lane change, upward fling = jump. */
    private val gestureListener = object : GestureDetector.GestureAdapter() {
        override fun fling(velocityX: Float, velocityY: Float, button: Int): Boolean {
            if (runner.state != RunnerSystem.State.RUNNING) return false
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                if (velocityX > 0f) runner.moveRight() else runner.moveLeft()
            } else if (velocityY < 0f) { // screen y points down: negative = swipe up
                runner.jump()
            }
            return true
        }
    }

    /** Desktop keys: arrows/AD = lanes, space/up/W = jump, enter = start. */
    private val keyInput = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.LEFT, Input.Keys.A -> runner.moveLeft()
                Input.Keys.RIGHT, Input.Keys.D -> runner.moveRight()
                Input.Keys.SPACE, Input.Keys.UP, Input.Keys.W -> runner.jump()
                Input.Keys.ENTER -> if (runner.state != RunnerSystem.State.RUNNING) startRun()
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
        equippedRarity = Rarity.entries[
            save.loadEquippedRarity(Rarity.COMMON.ordinal).coerceIn(0, Rarity.entries.size - 1)
        ]

        // -- Camera: low chase position for stronger perspective depth.
        camera = PerspectiveCamera(64f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(0f, 3.4f, 6f)
            lookAt(0f, 1f, -8f)
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
        val trackModel = voxelFactory.createTrackTile()
        trackTiles = kotlin.Array(2) { ModelInstance(trackModel) }
        bodyInstance = ModelInstance(voxelFactory.createGopalBody())
        val legModel = voxelFactory.createGopalLeg()
        legLeft = ModelInstance(legModel)
        legRight = ModelInstance(legModel)
        val armModel = voxelFactory.createGopalArm()
        armLeft = ModelInstance(armModel)
        armRight = ModelInstance(armModel)
        swordInstance = ModelInstance(voxelFactory.createSword())
        voxelFactory.applyRarityTraits(swordInstance, equippedRarity)
        val coinModel = voxelFactory.createCoin()
        coinInstances = kotlin.Array(RunnerSystem.COIN_POOL_SIZE) { ModelInstance(coinModel) }
        val barrierModel = voxelFactory.createBarrier()
        barrierInstances = kotlin.Array(RunnerSystem.BARRIER_POOL_SIZE) { ModelInstance(barrierModel) }

        // -- HUD + input (UI first, then swipes, then keys)
        val hud = GameHud(this, economy, runner)
        this.hud = hud
        hud.setReady(bestScore)
        Gdx.input.inputProcessor = InputMultiplexer(hud.stage, GestureDetector(gestureListener), keyInput)

        created = true
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime
        runner.update(delta)

        // Track tiles scroll with the world and leapfrog behind the camera.
        if (runner.state == RunnerSystem.State.RUNNING) {
            for (i in trackTileZ.indices) {
                trackTileZ[i] += runner.speed * delta
                if (trackTileZ[i] > TRACK_RESET_Z) trackTileZ[i] -= 2f * VoxelModelFactory.TRACK_TILE_LENGTH
            }
        }
        coinSpinDegrees = (coinSpinDegrees + COIN_SPIN_DEG_PER_SEC * delta) % 360f

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0.45f, 0.71f, 0.95f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Player pose: yaw eases 180° (facing camera in menus) <-> 0° (running),
        // limbs swing with speed, whole body bobs while grounded.
        val running = runner.state == RunnerSystem.State.RUNNING
        val targetYaw = if (running) 0f else 180f
        playerYaw += (targetYaw - playerYaw) * (8f * delta).coerceAtMost(1f)
        if (running && runner.playerY == 0f) runPhase += delta * runner.speed * 1.4f
        val bob = if (running && runner.playerY == 0f) Math.abs(MathUtils.sin(runPhase)) * 0.06f else 0f
        val swing = if (running) MathUtils.sin(runPhase) * 38f else 0f
        val px = runner.playerX
        val py = runner.playerY + bob
        val lean = if (running) -7f else 0f

        bodyInstance.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
        legLeft.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(-0.15f, 0.62f, 0f).rotate(Vector3.X, swing)
        legRight.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(0.15f, 0.62f, 0f).rotate(Vector3.X, -swing)
        armLeft.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(-0.4f, 1.32f, 0f).rotate(Vector3.X, -swing * 0.8f)
        armRight.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw).rotate(Vector3.X, lean)
            .translate(0.4f, 1.32f, 0f).rotate(Vector3.X, swing * 0.8f)
        // Sword strapped diagonally across the back (blade is +Y in model space).
        swordInstance.transform
            .setToTranslation(px, py, 0f).rotate(Vector3.Y, playerYaw)
            .translate(0.18f, 0.5f, 0.3f).rotate(Vector3.Z, 35f)
            .scale(0.55f, 0.55f, 0.55f)

        // Soft x-follow keeps side lanes framed; pure translation, no view drift.
        camera.position.x = px * 0.45f
        camera.update()

        modelBatch.begin(camera)
        modelBatch.render(trackTiles[0].also { it.transform.setToTranslation(0f, 0f, trackTileZ[0]) }, environment)
        modelBatch.render(trackTiles[1].also { it.transform.setToTranslation(0f, 0f, trackTileZ[1]) }, environment)
        modelBatch.render(bodyInstance, environment)
        modelBatch.render(legLeft, environment)
        modelBatch.render(legRight, environment)
        modelBatch.render(armLeft, environment)
        modelBatch.render(armRight, environment)
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
        for (i in runner.barriers.indices) {
            val barrier = runner.barriers[i]
            if (!barrier.active) continue
            barrierInstances[i].transform.setToTranslation(RunnerSystem.LANE_X[barrier.lane], 0f, barrier.z)
            modelBatch.render(barrierInstances[i], environment)
        }
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
        save.persistRunner(economy.gold, economy.gems, bestScore, equippedRarity.ordinal)
    }

    override fun dispose() {
        pause() // final save
        modelBatch.dispose()
        voxelFactory.dispose()
        hud?.dispose()
    }

    // ========================================================================
    // Entry points for HUD buttons
    // ========================================================================

    /** "RUN!" / "Run again" buttons. */
    fun startRun() {
        coinsDoubledThisRun = false
        runner.startRun()
        hud?.setRunning()
    }

    /** "Menu" button on the game-over panel. */
    fun backToMenu() {
        hud?.setReady(bestScore)
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
        private const val COIN_BASE_Y = 0.68f
        private const val COIN_SPIN_DEG_PER_SEC = 220f
        private const val TRACK_RESET_Z = 30f
    }
}
