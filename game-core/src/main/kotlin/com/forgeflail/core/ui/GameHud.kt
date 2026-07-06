package com.forgeflail.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
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

    // Crisp text at any screen density: glyphs are rasterized at dp*density
    // pixels from a rounded kid-friendly TTF (Baloo 2, OFL) with a dark
    // outline, then drawn 1:1 — no bitmap-font scaling blur.
    private val fontGenerator = FreeTypeFontGenerator(Gdx.files.internal("fonts/game.ttf"))
    private val font = generateFont(dp = 17)
    private val titleFont = generateFont(dp = 27, borderDp = 2f)
    private val smallFont = generateFont(dp = 12, borderDp = 1.2f)
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
    private val coinIconImage: Image
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
    private val reviveCell: Cell<TextButton>
    private val doubleCell: Cell<TextButton>

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
        coinIconImage = Image(coinIcon)
        val centerGroup = Table()
        centerGroup.add(coinIconImage).size(22f, 22f).padRight(5f)
        centerGroup.add(coinCountLabel)
        centerGroup.add(scoreLabel).padLeft(14f)
        val topBar = Table()
        topBar.background = panelBg
        topBar.pad(10f, 12f, 10f, 12f)
        topBar.add(goldLabel).left()
        topBar.add(centerGroup).expandX().center()
        topBar.add(gemsLabel).right()
        root.add(topBar).growX().top()
        root.row()

        root.add().expand() // spacer: panels hug the bottom
        root.row()

        // --- SELECT: onboarding / character choice ---------------------------
        // The chosen character spins center-stage in the 3D scene behind this.
        charNameLabel = Label("", Label.LabelStyle(titleFont, Color.valueOf("FFD54F"))).apply {
            setAlignment(Align.center)
        }
        charIntroLabel = Label("", labelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        selectPanel = Table()
        selectPanel.background = solid(0.05f, 0.1f, 0.22f, 0.85f)
        selectPanel.pad(14f)
        selectPanel.add(Label("Choose your runner!", labelStyle).apply { setAlignment(Align.center) })
            .growX().colspan(3).padBottom(8f)
        selectPanel.row()
        selectPanel.add(charNameLabel).growX().colspan(3)
        selectPanel.row()
        selectPanel.add(charIntroLabel).growX().colspan(3).padBottom(12f)
        selectPanel.row()
        selectPanel.add(button("  <  ") { game.cycleCharacter(-1) }).height(56f).width(72f)
        selectPanel.add(pulse(button("Start Running!", GREEN) { game.startRun() }))
            .height(56f).growX().pad(0f, 10f, 0f, 10f)
        selectPanel.add(button("  >  ") { game.cycleCharacter(1) }).height(56f).width(72f)
        selectPanel.row()
        selectPanel.add(button("Menu") { game.backToMenu() }).height(44f).colspan(3).padTop(8f)
        selectPanel.isVisible = false
        root.add(selectPanel).growX().padBottom(4f)
        root.row()

        // --- DEAD: game-over panel ------------------------------------------
        resultLabel = Label("", labelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        // Ad-first hierarchy: Revive is the hero (full width, tallest, pulsing),
        // x2 coins next. The free path (Run again) stays clearly visible below —
        // required for Families policy; only the emphasis changes.
        reviveButton = pulse(button("Revive (watch ad)", AMBER) { game.requestRevive() })
        doubleButton = button("x2 coins (watch ad)", AMBER) {
            doubleButton.isVisible = false
            doubleCell.height(0f)
            deadPanel.invalidateHierarchy()
            game.requestDoubleRunCoins()
        }
        deadPanel = Table()
        deadPanel.background = solid(0.25f, 0.08f, 0.1f, 0.85f)
        deadPanel.pad(14f)
        deadPanel.add(resultLabel).growX().colspan(2).padBottom(10f)
        deadPanel.row()
        reviveCell = deadPanel.add(reviveButton).height(62f).growX().colspan(2).pad(3f)
        deadPanel.row()
        doubleCell = deadPanel.add(doubleButton).height(54f).growX().colspan(2).pad(3f)
        deadPanel.row()
        deadPanel.add(button("Run again", GREEN) { game.startRun() }).height(44f).growX().pad(3f).padTop(8f)
        deadPanel.add(button("Menu") { game.backToMenu() }).height(44f).growX().pad(3f).padTop(8f)
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
        readyPanel.pad(14f)
        readyPanel.add(bestLabel).growX().padBottom(8f)
        readyPanel.row()
        readyPanel.add(pulse(button("RUN!", GREEN) { game.startRun() })).height(62f).growX().padBottom(10f)
        readyPanel.row()
        readyPanel.add(button("Sword Chest (${GachaSystem.CHEST_COST_GEMS} gems)") {
            game.onChestButtonClicked()
        }).height(50f).growX()
        readyPanel.row()
        readyPanel.add(Label(oddsText, Label.LabelStyle(smallFont, Color.valueOf("C8CFDA"))))
            .padTop(5f).padBottom(10f)
        readyPanel.row()
        readyPanel.add(button("Characters") { game.openCharacterSelect() }).height(46f).growX().padBottom(10f)
        readyPanel.row()
        val shopRow = Table()
        for (pack in GemStoreCatalog.PACKS) {
            shopRow.add(button("${pack.gems} gems\n${pack.priceHint}") {
                game.requestGemPackPurchase(pack.productId)
            }).height(54f).growX().pad(3f)
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
        toastRow.pad(8f)
        toastRow.add(toastLabel).growX()
        root.add(toastRow).growX()

        economy.listener = EconomyManager.BalanceListener { _, _ -> refreshCurrencies() }
        refreshCurrencies()
        fontGenerator.dispose() // fonts own their textures; the generator is done
    }

    /** One-line event feedback ("Epic sword unlocked!", "Need 12 more gems"). */
    fun toast(message: String) {
        toastLabel.setText(message)
        toastLabel.clearActions()
        toastLabel.color.a = 0f
        toastLabel.addAction(Actions.fadeIn(0.25f))
    }

    /** Onboarding / character-select screen; also updates on prev/next cycling. */
    fun showCharacterSelect(spec: CharacterSpec) {
        if (!selectPanel.isVisible) reveal(selectPanel)
        readyPanel.isVisible = false
        deadPanel.isVisible = false
        charNameLabel.setText(spec.displayName)
        charIntroLabel.setText(spec.intro)
        // Name slides in on every cycle so tapping < > feels alive.
        charNameLabel.clearActions()
        charNameLabel.color.a = 0f
        charNameLabel.addAction(Actions.fadeIn(0.2f))
    }

    fun setReady(bestScore: Int) {
        selectPanel.isVisible = false
        if (!readyPanel.isVisible) reveal(readyPanel)
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
        // The coin icon pops — juicy confirmation kids notice without reading.
        coinIconImage.clearActions()
        coinIconImage.setOrigin(coinIconImage.width / 2f, coinIconImage.height / 2f)
        coinIconImage.setScale(1f)
        coinIconImage.addAction(Actions.sequence(
            Actions.scaleTo(1.5f, 1.5f, 0.07f, Interpolation.smooth),
            Actions.scaleTo(1f, 1f, 0.15f, Interpolation.smooth)
        ))
    }

    fun onRunEnded(distanceMeters: Int, runCoins: Int, bestScore: Int, canRevive: Boolean, canDouble: Boolean) {
        selectPanel.isVisible = false
        readyPanel.isVisible = false
        reveal(deadPanel)
        // Collapse unavailable ad offers so no dead space pushes the panel around.
        reviveButton.isVisible = canRevive
        reviveCell.height(if (canRevive) 62f else 0f)
        doubleButton.isVisible = canDouble
        doubleCell.height(if (canDouble) 54f else 0f)
        deadPanel.invalidateHierarchy()
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
        titleFont.dispose()
        smallFont.dispose()
        whitePixel.dispose()
        coinIcon.dispose()
    }

    /** Density-crisp outlined font: rasterized at dp*density px, drawn 1:1. */
    private fun generateFont(dp: Int, borderDp: Float = 1.6f): BitmapFont {
        val scale = Gdx.graphics.density.coerceAtLeast(1f)
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = (dp * scale).toInt()
            borderWidth = borderDp * scale
            borderColor = OUTLINE
            color = Color.WHITE
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
        }
        return fontGenerator.generateFont(parameter).also { it.data.setScale(1f / scale) }
    }

    /** Gentle heartbeat on the primary "go" button so kids' eyes land on it. */
    private fun pulse(btn: TextButton): TextButton {
        btn.isTransform = true
        btn.addAction(Actions.forever(Actions.sequence(
            Actions.run { btn.setOrigin(btn.width / 2f, btn.height / 2f) },
            Actions.scaleTo(1.04f, 1.04f, 0.5f, Interpolation.sine),
            Actions.scaleTo(1f, 1f, 0.5f, Interpolation.sine)
        )))
        return btn
    }

    /** Fade a panel in when it appears instead of snapping. */
    private fun reveal(panel: Table) {
        panel.isVisible = true
        panel.clearActions()
        panel.color.a = 0f
        panel.addAction(Actions.fadeIn(0.25f, Interpolation.smooth))
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

    /**
     * Color language kids read at a glance: GREEN = go/play, AMBER = watch an
     * ad (always optional), BLUE = everything else.
     */
    private fun button(text: String, base: Color = BLUE, onClick: () -> Unit): TextButton {
        val style = TextButton.TextButtonStyle().also {
            it.font = font
            it.fontColor = Color.WHITE
            it.up = solid(base.r, base.g, base.b, 0.95f)
            it.down = solid(base.r * 0.6f, base.g * 0.6f, base.b * 0.6f, 0.95f)
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
        val BLUE = Color(0.16f, 0.32f, 0.55f, 1f)
        val GREEN = Color(0.16f, 0.52f, 0.26f, 1f)
        val AMBER = Color(0.72f, 0.48f, 0.1f, 1f)
        val OUTLINE = Color(0.08f, 0.08f, 0.12f, 1f)
    }
}
