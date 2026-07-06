package com.forgeflail.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.StringBuilder as GdxStringBuilder
import com.forgeflail.core.ForgeFlailGame
import com.forgeflail.core.characters.CharacterSpec
import com.forgeflail.core.economy.EconomyManager
import com.forgeflail.core.gacha.GachaSystem
import com.forgeflail.core.gacha.Rarity
import com.forgeflail.core.monetization.GemStoreCatalog
import com.forgeflail.core.runner.RunnerSystem

/**
 * Skin-less Scene2D HUD for the runner, three states:
 *  - READY:   big RUN button, gacha chest (sword skins) with the mandatory odds
 *             disclosure, gem shop.
 *  - RUNNING: just the top bar (score/coins live at 4 Hz).
 *  - DEAD:    result panel with [Revive (ad)], [x2 coins (ad)], [Run again], [Menu].
 *
 * Performance: dynamic labels refresh at 4 Hz (not per frame) through reusable
 * [GdxStringBuilder]s. Event toasts may allocate — rare, user-driven moments.
 */
class GameHud(
    private val game: ForgeFlailGame,
    private val economy: EconomyManager,
    private val runner: RunnerSystem,
) : Disposable {

    val stage = Stage(ScreenViewport().apply {
        unitsPerPixel = 1f / Gdx.graphics.density.coerceAtLeast(1f)
    })

    private val font = BitmapFont()
    private val whitePixel: Texture = Pixmap(1, 1, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.WHITE)
        pm.fill()
        val tex = Texture(pm)
        pm.dispose()
        tex
    }

    /** Round gold coin icon for the top-bar counter, drawn once procedurally. */
    private val coinIcon: Texture = Pixmap(28, 28, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("B8860B"))
        pm.fillCircle(14, 14, 13)
        pm.setColor(Color.valueOf("FFD54F"))
        pm.fillCircle(14, 14, 11)
        pm.setColor(Color.valueOf("FFF3C4"))
        pm.fillCircle(11, 11, 4)
        val tex = Texture(pm)
        pm.dispose()
        tex
    }

    private val goldLabel: Label
    private val gemsLabel: Label
    private val coinCountLabel: Label
    private val scoreLabel: Label
    private val toastLabel: Label
    private val readyPanel: Table
    private val bestLabel: Label
    private val deadPanel: Table
    private val selectPanel: Table
    private val charNameLabel: Label
    private val charIntroLabel: Label
    private val resultLabel: Label
    private val reviveButton: TextButton
    private val doubleButton: TextButton

    private val goldText = GdxStringBuilder(24)
    private val gemsText = GdxStringBuilder(24)
    private val coinText = GdxStringBuilder(12)
    private val scoreText = GdxStringBuilder(16)
    private var refreshAccumulator = 0f

    init {
        val labelStyle = Label.LabelStyle(font, Color.WHITE)
        val panelBg = solid(0f, 0f, 0f, 0.45f)

        val root = Table()
        root.setFillParent(true)
        root.pad(8f)
        stage.addActor(root)

        // --- top bar: gold | [coin icon] run-coins + distance | gems ---------
        goldLabel = Label("", labelStyle)
        coinCountLabel = Label("", Label.LabelStyle(font, Color.valueOf("FFD54F")))
        scoreLabel = Label("", Label.LabelStyle(font, Color.valueOf("FFE082")))
        gemsLabel = Label("", Label.LabelStyle(font, Color.valueOf("7FE7F5")))
        val centerGroup = Table()
        centerGroup.add(Image(coinIcon)).size(18f, 18f).padRight(4f)
        centerGroup.add(coinCountLabel)
        centerGroup.add(scoreLabel).padLeft(12f)
        val topBar = Table()
        topBar.background = panelBg
        topBar.pad(8f)
        topBar.add(goldLabel).left()
        topBar.add(centerGroup).expandX().center()
        topBar.add(gemsLabel).right()
        root.add(topBar).growX().top()
        root.row()

        root.add().expand() // spacer: panels hug the bottom
        root.row()

        // --- SELECT: onboarding / character choice ---------------------------
        // The chosen character spins center-stage in the 3D scene behind this.
        charNameLabel = Label("", Label.LabelStyle(font, Color.valueOf("FFD54F"))).apply {
            setAlignment(Align.center)
            setFontScale(1.5f)
        }
        charIntroLabel = Label("", labelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        selectPanel = Table()
        selectPanel.background = solid(0.05f, 0.1f, 0.22f, 0.85f)
        selectPanel.pad(10f)
        selectPanel.add(Label("Choose your runner!", labelStyle).apply { setAlignment(Align.center) })
            .growX().colspan(3).padBottom(6f)
        selectPanel.row()
        selectPanel.add(charNameLabel).growX().colspan(3)
        selectPanel.row()
        selectPanel.add(charIntroLabel).growX().colspan(3).padBottom(8f)
        selectPanel.row()
        selectPanel.add(button("  <  ") { game.cycleCharacter(-1) }).height(52f).width(64f)
        selectPanel.add(button("Start Running!") { game.startRun() }).height(52f).growX().pad(0f, 8f, 0f, 8f)
        selectPanel.add(button("  >  ") { game.cycleCharacter(1) }).height(52f).width(64f)
        selectPanel.row()
        selectPanel.add(button("Menu") { game.backToMenu() }).height(40f).colspan(3).padTop(6f)
        selectPanel.isVisible = false
        root.add(selectPanel).growX().padBottom(4f)
        root.row()

        // --- DEAD: game-over panel ------------------------------------------
        resultLabel = Label("", labelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        reviveButton = button("Revive (watch ad)") { game.requestRevive() }
        doubleButton = button("x2 coins (watch ad)") {
            doubleButton.isVisible = false
            game.requestDoubleRunCoins()
        }
        deadPanel = Table()
        deadPanel.background = solid(0.25f, 0.08f, 0.1f, 0.85f)
        deadPanel.pad(10f)
        deadPanel.add(resultLabel).growX().colspan(2).padBottom(8f)
        deadPanel.row()
        deadPanel.add(reviveButton).height(48f).growX().pad(2f)
        deadPanel.add(doubleButton).height(48f).growX().pad(2f)
        deadPanel.row()
        deadPanel.add(button("Run again") { game.startRun() }).height(48f).growX().pad(2f)
        deadPanel.add(button("Menu") { game.backToMenu() }).height(48f).growX().pad(2f)
        deadPanel.isVisible = false
        root.add(deadPanel).growX().padBottom(4f)
        root.row()

        // --- READY: run + chest (skins) + shop -------------------------------
        bestLabel = Label("", labelStyle).apply { setAlignment(Align.center) }
        val oddsText = GdxStringBuilder(96).also { sb ->
            sb.append("Drop rates: ")
            for (rarity in Rarity.entries) {
                sb.append(rarity.displayName).append(' ').append(rarity.dropChancePercent).append("%  ")
            }
        }
        readyPanel = Table()
        readyPanel.background = panelBg
        readyPanel.pad(10f)
        readyPanel.add(bestLabel).growX().padBottom(6f)
        readyPanel.row()
        readyPanel.add(button("RUN!") { game.startRun() }).height(56f).growX().padBottom(8f)
        readyPanel.row()
        readyPanel.add(button("Sword Chest (${GachaSystem.CHEST_COST_GEMS} gems)") {
            game.onChestButtonClicked()
        }).height(48f).growX()
        readyPanel.row()
        readyPanel.add(Label(oddsText, Label.LabelStyle(font, Color.valueOf("C8CFDA"))))
            .padTop(4f).padBottom(8f)
        readyPanel.row()
        readyPanel.add(button("Characters") { game.openCharacterSelect() }).height(44f).growX().padBottom(8f)
        readyPanel.row()
        val shopRow = Table()
        for (pack in GemStoreCatalog.PACKS) {
            shopRow.add(button("${pack.gems} gems\n${pack.priceHint}") {
                game.requestGemPackPurchase(pack.productId)
            }).height(48f).growX().pad(2f)
        }
        readyPanel.add(shopRow).growX()
        root.add(readyPanel).growX().padBottom(4f)
        root.row()

        // --- toast: last game event ------------------------------------------
        toastLabel = Label("Swipe to steer, swipe up to jump. Grab the coins!", Label.LabelStyle(font, Color.valueOf("FFE082")))
        toastLabel.setWrap(true)
        toastLabel.setAlignment(Align.center)
        val toastRow = Table()
        toastRow.background = panelBg
        toastRow.pad(6f)
        toastRow.add(toastLabel).growX()
        root.add(toastRow).growX()

        economy.listener = EconomyManager.BalanceListener { _, _ -> refreshCurrencies() }
        refreshCurrencies()
    }

    /** One-line event feedback ("Epic sword unlocked!", "Need 12 more gems"). */
    fun toast(message: String) {
        toastLabel.setText(message)
    }

    /** Onboarding / character-select screen; also updates on prev/next cycling. */
    fun showCharacterSelect(spec: CharacterSpec) {
        selectPanel.isVisible = true
        readyPanel.isVisible = false
        deadPanel.isVisible = false
        charNameLabel.setText(spec.displayName)
        charIntroLabel.setText(spec.intro)
    }

    fun setReady(bestScore: Int) {
        selectPanel.isVisible = false
        readyPanel.isVisible = true
        deadPanel.isVisible = false
        // Note: the default BitmapFont lacks em-dash and similar glyphs — ASCII only.
        bestLabel.setText(if (bestScore > 0) "Best run: $bestScore m" else "First run - good luck!")
        scoreLabel.setText("")
        coinCountLabel.setText("0")
    }

    fun setRunning() {
        selectPanel.isVisible = false
        readyPanel.isVisible = false
        deadPanel.isVisible = false
        coinCountLabel.setText("0")
    }

    /** Instant top-bar update the moment a coin is grabbed (with the ding). */
    fun onCoinCollected(runCoins: Int) {
        coinText.setLength(0)
        coinText.append(runCoins)
        coinCountLabel.setText(coinText)
    }

    fun onRunEnded(distanceMeters: Int, runCoins: Int, bestScore: Int, canRevive: Boolean, canDouble: Boolean) {
        selectPanel.isVisible = false
        readyPanel.isVisible = false
        deadPanel.isVisible = true
        reviveButton.isVisible = canRevive
        doubleButton.isVisible = canDouble
        resultLabel.setText(
            "You ran $distanceMeters m: +$runCoins gold, +${runner.runGems} gems!  Best: $bestScore m"
        )
    }

    fun render(delta: Float) {
        refreshAccumulator += delta
        if (refreshAccumulator >= REFRESH_INTERVAL) {
            refreshAccumulator = 0f
            if (runner.state == RunnerSystem.State.RUNNING) refreshScore()
        }
        stage.act(delta)
        stage.viewport.apply(true)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        font.dispose()
        whitePixel.dispose()
        coinIcon.dispose()
    }

    // -----------------------------------------------------------------------

    private fun refreshCurrencies() {
        goldText.setLength(0)
        goldText.append("Gold: ").append(economy.gold)
        goldLabel.setText(goldText)
        gemsText.setLength(0)
        gemsText.append("Gems: ").append(economy.gems)
        gemsLabel.setText(gemsText)
    }

    private fun refreshScore() {
        scoreText.setLength(0)
        scoreText.append(runner.distanceMeters).append(" m")
        scoreLabel.setText(scoreText)
    }

    private fun button(text: String, onClick: () -> Unit): TextButton {
        val style = TextButton.TextButtonStyle().also {
            it.font = font
            it.fontColor = Color.WHITE
            it.up = solid(0.16f, 0.32f, 0.55f, 0.95f)
            it.down = solid(0.10f, 0.20f, 0.38f, 0.95f)
        }
        val btn = TextButton(text, style)
        btn.pad(4f, 10f, 4f, 10f)
        btn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = onClick()
        })
        return btn
    }

    private fun solid(r: Float, g: Float, b: Float, a: Float): Drawable =
        TextureRegionDrawable(TextureRegion(whitePixel)).tint(Color(r, g, b, a))

    private companion object {
        const val REFRESH_INTERVAL = 0.25f
    }
}
