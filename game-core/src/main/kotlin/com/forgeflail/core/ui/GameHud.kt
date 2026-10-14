package com.forgeflail.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
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
import com.forgeflail.core.runner.LevelCatalog
import com.forgeflail.core.runner.RunnerSystem

/**
 * Skin-less Scene2D HUD, laid out after the Star Voyage prototype: rounded
 * cards on deep purple, currency pills up top, and a four-tab bottom nav.
 *
 * Screens:
 *  - ONBOARDING: first-launch quest intro + swipe controls, then the hero picker.
 *  - HOME:       equipped hero front and center (live 3D behind), best run,
 *                next-goal chip and the big RUN! button.
 *  - MAP:        the delivery road as a winding world map — square level tiles
 *                zig-zag up a dotted path; locked tiles carry a padlock badge
 *                and name the level that opens them, the selected tile gets a
 *                play badge + PLAY pill (progression is skill-only, never sold
 *                or ad-gated).
 *  - HEROES:     3D turntable (drag/fling spins the hero), prev/next browsing,
 *                gem unlocks (gems are earned free while running).
 *  - SHOP:       sword-skin chest with the mandatory odds disclosure + gem packs.
 *  - RUNNING:    currency pills, coin counter, distance chip with a goal
 *                progress bar.
 *  - DEAD:       result card with [Revive (ad)], [x2 gold (ad)], [Run again], [Home].
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
    private val titleFont = generateFont(dp = 26, borderDp = 2f)
    private val smallFont = generateFont(dp = 12, borderDp = 1.2f)
    private val captionFont = generateFont(dp = 10, borderDp = 1f)
    private val whitePixel: Texture = Pixmap(1, 1, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.WHITE)
        pm.fill()
        val tex = Texture(pm)
        pm.dispose()
        tex
    }

    /** Every procedurally drawn texture, disposed together. */
    private val ownedTextures = com.badlogic.gdx.utils.Array<Texture>(16).also { it.add(whitePixel) }

    /** Round gold coin icon, drawn once procedurally. */
    private val coinIcon: Texture = Pixmap(28, 28, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("B8860A"))
        pm.fillCircle(14, 14, 13)
        pm.setColor(Color.valueOf("FFD24C"))
        pm.fillCircle(14, 14, 11)
        pm.setColor(Color.valueOf("FFF3C4"))
        pm.fillCircle(11, 11, 4)
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        tex
    }

    /** Faceted gem icon for the premium-currency pill. */
    private val gemIcon: Texture = Pixmap(28, 28, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("4B2FBF"))
        pm.fillTriangle(2, 10, 26, 10, 14, 26)
        pm.fillTriangle(5, 10, 23, 10, 14, 2)
        pm.setColor(Color.valueOf("7B61FF"))
        pm.fillTriangle(4, 10, 24, 10, 14, 24)
        pm.fillTriangle(7, 10, 21, 10, 14, 4)
        pm.setColor(Color.valueOf("BBAAFF"))
        pm.fillTriangle(9, 9, 15, 9, 12, 5)
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        tex
    }

    /** Round padlock badge riding the corner of locked map tiles. */
    private val lockBadgeIcon: Texture = Pixmap(28, 28, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("0F0826"))
        pm.fillCircle(14, 14, 13)
        pm.setColor(Color.valueOf("3A2470"))
        pm.fillCircle(14, 14, 11)
        pm.setColor(Color.valueOf("B7A9E8"))
        pm.fillCircle(14, 10, 5)
        pm.setColor(Color.valueOf("3A2470"))
        pm.fillCircle(14, 10, 3)
        pm.setColor(Color.valueOf("B7A9E8"))
        pm.fillRectangle(8, 12, 12, 9)
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        tex
    }

    /** Pink play badge riding the corner of the selected map tile. */
    private val playBadgeIcon: Texture = Pixmap(32, 32, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("0F0826"))
        pm.fillCircle(16, 16, 15)
        pm.setColor(Color.valueOf("FF4D8D"))
        pm.fillCircle(16, 16, 12)
        pm.setColor(Color.WHITE)
        pm.fillTriangle(12, 10, 12, 22, 23, 16)
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        tex
    }

    /** Gear icon for the settings chip: 8 teeth, ring body, punched hub. */
    private val gearIcon: Texture = Pixmap(28, 28, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("B7A9E8"))
        for (i in 0 until 8) {
            val a = i * (Math.PI / 4.0)
            pm.fillCircle(14 + (Math.cos(a) * 9.5).toInt(), 14 + (Math.sin(a) * 9.5).toInt(), 3)
        }
        pm.fillCircle(14, 14, 9)
        pm.blending = Pixmap.Blending.None
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fillCircle(14, 14, 4)
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        tex
    }

    /** One diagonal stretch of the dotted road between two map nodes. */
    private val pathDotsIcon: Texture = Pixmap(96, 44, Pixmap.Format.RGBA8888).let { pm ->
        pm.setColor(Color.valueOf("7B61FF"))
        for (i in 0 until 5) {
            val t = i / 4f
            pm.fillCircle((12 + t * 72).toInt(), (36 - t * 28).toInt(), 4)
        }
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        tex
    }

    /**
     * The bottom screens are mutually exclusive and swap through this holder.
     * Tables lay out INVISIBLE actors too, so merely hiding panels left them
     * stacked in the layout — tall enough on phone screens to push the top
     * bar (gold/gems/distance) off the top edge.
     *
     * The holder's maxHeight (set in [updatePanelCap]) keeps every panel BELOW
     * the currency pills; panels taller than that scroll inside instead of
     * covering the top bar. Most screens sit at the bottom; the map (and the
     * paused card) center in the free space — see [showPanel].
     */
    private val panelHolder = Container<Table>()

    /** Nav bar swaps through its own holder so hiding it frees the space. */
    private val navHolder = Container<Table>()
    private val navBar: Table
    private val navButtons: kotlin.Array<TextButton>

    private val goldLabel: Label
    private val gemsLabel: Label
    private val coinCountLabel: Label
    private val coinIconImage: Image
    private val scoreLabel: Label
    private val runCenterGroup: Table
    private val goalBarCell: Cell<Stack>
    private val goalBar: Stack
    private val goalFill: Image
    private var runGoalMeters = 0
    private val toastLabel: Label
    private val toastRow: Table
    private var shownRunHint = false

    private val onboardingPanel: Table
    private val homePanel: Table
    private val homeHeroLabel: Label
    private val homeBestLabel: Label
    private val homeLevelLabel: Label
    private val mapPanel: Table
    private val mapNodes: kotlin.Array<Table>
    private val mapNodeNames: kotlin.Array<Label>
    private val mapNodeStatus: kotlin.Array<Label>
    private val mapTileBgs: kotlin.Array<Image>
    private val mapTileNumbers: kotlin.Array<Label>
    private val mapTileBadges: kotlin.Array<Image>
    private val mapTileCells: kotlin.Array<Cell<Stack>>
    private val mapStatusHolders: kotlin.Array<Container<Actor>>
    private val mapPlayButton: TextButton
    private val heroesPanel: Table
    private val heroNameLabel: Label
    private val heroIntroLabel: Label
    private val heroStatusLabel: Label
    private val heroActionButton: TextButton
    private val heroActionTeal: TextButton.TextButtonStyle
    private val heroActionPurple: TextButton.TextButtonStyle
    private val shopPanel: Table
    private val settingsPanel: Table
    private val settingsChip: Table
    private val settingsChipCell: Cell<Table>
    private val coinSoundToggle: TextButton
    private val footstepSoundToggle: TextButton
    private val actionSoundToggle: TextButton
    private val toggleOnStyle: TextButton.TextButtonStyle
    private val toggleOffStyle: TextButton.TextButtonStyle
    private val pausePanel: Table
    private val pauseChip: TextButton
    private val pauseChipCell: Cell<TextButton>
    private val panelHolderCell: Cell<Container<Table>>
    private val deadPanel: Table
    private val resultLabel: Label
    private val reviveButton: TextButton
    private val doubleButton: TextButton
    private val reviveCell: Cell<TextButton>
    private val doubleCell: Cell<TextButton>

    private val goldText = GdxStringBuilder(24)
    private val gemsText = GdxStringBuilder(24)
    private val coinText = GdxStringBuilder(12)
    private val scoreText = GdxStringBuilder(16)
    private val eventText = GdxStringBuilder(96)
    private var refreshAccumulator = 0f

    // Rolling currency counters: the labels ease toward the real balances each
    // frame, so gold banking at run end counts up instead of snapping. -1 means
    // "paint immediately" (first frame after boot, no roll-up from zero).
    private var shownGold = -1L
    private var shownGems = -1L

    /** Coin-icon pop, decayed per frame — no per-pickup action allocation. */
    private var coinPopScale = 1f

    init {
        val labelStyle = Label.LabelStyle(font, TEXT)
        val mutedSmall = Label.LabelStyle(smallFont, MUTED)
        val cardBg = rounded(PANEL, 14, BORDER_SOFT, 2)
        val pillBg = rounded(PANEL, 16, BORDER_SOFT, 2)
        val darkChip = rounded(BG_DEEP_TRANSLUCENT, 10)

        // Top bar and bottom panels live in separate stage-sized tables: the
        // bar is pinned to the top and can never be displaced by panel growth.
        val uiDensity = Gdx.graphics.density.coerceAtLeast(1f)
        val root = Table()
        root.setFillParent(true)
        // Keep the bar below any status-bar/camera-cutout inset (0 on desktop).
        root.top().pad(8f).padTop(8f + Gdx.graphics.safeInsetTop / uiDensity)
        stage.addActor(root)

        val panelRoot = Table()
        panelRoot.setFillParent(true)
        panelRoot.bottom()
        stage.addActor(panelRoot)
        // fillX only: panels keep their preferred height, so the holder's
        // vertical alignment (bottom for menus, center for map/pause) works.
        panelHolder.fillX()
        panelHolder.bottom()
        // padTop (set in updatePanelCap) walls off the top-bar zone so centered
        // panels can never rise underneath the currency pills.
        panelHolderCell = panelRoot.add(panelHolder).grow().pad(0f, 8f, 6f, 8f)
        panelRoot.row()
        updatePanelCap()

        // --- top bar: GOLD pill | (run: coins + distance) | GEMS pill --------
        goldLabel = Label("", labelStyle)
        gemsLabel = Label("", labelStyle)
        coinCountLabel = Label("0", Label.LabelStyle(font, GOLD))
        scoreLabel = Label("", Label.LabelStyle(font, GOLD))
        coinIconImage = Image(coinIcon)

        runCenterGroup = Table()
        runCenterGroup.background = darkChip
        runCenterGroup.pad(4f, 12f, 4f, 12f)
        runCenterGroup.add(coinIconImage).size(20f, 20f).padRight(5f)
        runCenterGroup.add(coinCountLabel)
        runCenterGroup.add(scoreLabel).padLeft(14f)
        runCenterGroup.isVisible = false

        // Pause chip: run-screen only (see showPanel), tucked past the gems pill.
        pauseChip = button("| |", PANEL, TEXT) { game.pauseRun() }
        pauseChip.isVisible = false

        // Settings gear: menu screens only (see setNav), same top-bar slot.
        settingsChip = Table()
        settingsChip.background = pillBg
        settingsChip.add(Image(gearIcon)).size(22f, 22f)
        settingsChip.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = game.openSettings()
        })
        settingsChip.isVisible = false

        val topBar = Table()
        topBar.add(currencyPill(coinIcon, "GOLD", goldLabel, pillBg)).left()
        topBar.add(runCenterGroup).expandX().center()
        topBar.add(currencyPill(gemIcon, "GEMS", gemsLabel, pillBg)).right()
        // Collapsed while hidden: an invisible actor still takes layout space.
        pauseChipCell = topBar.add(pauseChip).size(0f, 44f)
        settingsChipCell = topBar.add(settingsChip).size(0f, 44f)
        root.add(topBar).growX()
        root.row()

        // Goal progress bar under the distance chip — fill is one preallocated
        // Image whose scaleX is set at the 4 Hz refresh (no per-frame layout).
        goalFill = Image(solid(TEAL.r, TEAL.g, TEAL.b, 1f))
        goalFill.setScaleX(0f)
        goalBar = Stack()
        goalBar.add(Image(rounded(BG_DEEP_TRANSLUCENT, 4)))
        goalBar.add(Container(goalFill).fill().pad(2f))
        goalBarCell = root.add(goalBar).size(GOAL_BAR_WIDTH, 10f).padTop(5f)
        goalBar.isVisible = false

        // --- button styles shared by the Heroes action button -----------------
        heroActionTeal = buttonStyle(TEAL, TEAL_TEXT)
        heroActionPurple = buttonStyle(PURPLE, Color.WHITE)

        // --- ONBOARDING: quest intro + controls --------------------------------
        onboardingPanel = Table()
        onboardingPanel.background = rounded(BG_DEEP, 18, GOLD_DARK, 3)
        onboardingPanel.pad(16f)
        onboardingPanel.add(Label("The Great Sword Delivery!", Label.LabelStyle(titleFont, GOLD)).apply {
            setAlignment(Align.center)
        }).growX().padBottom(8f)
        onboardingPanel.row()
        onboardingPanel.add(Label(
            "The forge is overflowing! Run the delivery road, dodge the barriers, " +
                "grab Gold & Gems, and open the route to the old village.",
            labelStyle
        ).apply {
            setWrap(true)
            setAlignment(Align.center)
        }).growX().padBottom(10f)
        onboardingPanel.row()
        onboardingPanel.add(controlRow(cardBg, "< >", "Swipe left / right", "Steer between the lanes")).growX().padBottom(6f)
        onboardingPanel.row()
        onboardingPanel.add(controlRow(cardBg, "/\\", "Swipe up", "Jump over barriers")).growX().padBottom(6f)
        onboardingPanel.row()
        onboardingPanel.add(controlRow(cardBg, "$", "Grab everything!", "Gold & Gems along the path")).growX().padBottom(12f)
        onboardingPanel.row()
        onboardingPanel.add(pulse(button("LET'S GO!", TEAL, TEAL_TEXT) { game.finishOnboarding() }))
            .height(58f).growX()

        // --- HOME: hero + best run + next goal + big RUN -----------------------
        homeHeroLabel = Label("", Label.LabelStyle(titleFont, GOLD)).apply { setAlignment(Align.center) }
        homeBestLabel = Label("", mutedSmall).apply { setAlignment(Align.center) }
        homeLevelLabel = Label("", Label.LabelStyle(smallFont, TEXT)).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        homePanel = Table()
        // Name + best run ride on their own chip: the 3D hero stands right
        // behind this panel, and bare text over his face reads as a glitch.
        val homeHeroCard = Table()
        homeHeroCard.background = darkChip
        homeHeroCard.pad(6f, 16f, 6f, 16f)
        homeHeroCard.add(homeHeroLabel).growX()
        homeHeroCard.row()
        homeHeroCard.add(homeBestLabel).growX()
        homePanel.add(homeHeroCard).padBottom(6f)
        homePanel.row()
        val levelChip = Table()
        levelChip.background = cardBg
        levelChip.pad(8f, 12f, 8f, 12f)
        levelChip.add(homeLevelLabel).growX()
        levelChip.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = game.openMap()
        })
        homePanel.add(levelChip).growX().padBottom(10f)
        homePanel.row()
        homePanel.add(pulse(button("RUN!\nTap to start your journey", TEAL, TEAL_TEXT) { game.startRun() }))
            .height(74f).growX()

        // --- MAP: the winding delivery road, world-map style --------------------
        mapPanel = Table()
        mapPanel.background = rounded(BG_DEEP, 18, BORDER_SOFT, 2)
        mapPanel.pad(14f, 14f, 12f, 14f)
        mapPanel.add(Label("The Delivery Road", Label.LabelStyle(titleFont, GOLD)).apply {
            setAlignment(Align.center)
        }).growX().padBottom(6f)
        mapPanel.row()
        // The visualized destination, gold-framed like the prototype's goal card.
        val goalCard = Table()
        goalCard.background = rounded(PANEL, 14, GOLD_DARK, 3)
        goalCard.pad(6f, 12f, 6f, 12f)
        goalCard.add(Label(
            "THE GOAL: ${LevelCatalog.LEVELS.last().displayName}!",
            Label.LabelStyle(smallFont, GOLD)
        )).left().growX()
        goalCard.row()
        goalCard.add(Label(
            "Beat each stretch of the road to open the next one.",
            Label.LabelStyle(captionFont, MUTED)
        ).apply { setWrap(true) }).left().growX()
        mapPanel.add(goalCard).growX().padBottom(8f)
        mapPanel.row()

        val levelCount = LevelCatalog.LEVELS.size
        mapNodeNames = kotlin.Array(levelCount) { Label("", Label.LabelStyle(captionFont, GOLD)) }
        mapNodeStatus = kotlin.Array(levelCount) {
            Label("", Label.LabelStyle(captionFont, MUTED)).apply { setAlignment(Align.center) }
        }
        mapTileBgs = kotlin.Array(levelCount) { Image() }
        mapTileNumbers = kotlin.Array(levelCount) { i ->
            Label("${i + 1}", Label.LabelStyle(titleFont, TEXT)).apply { setAlignment(Align.center) }
        }
        mapTileBadges = kotlin.Array(levelCount) { Image() }
        mapStatusHolders = kotlin.Array(levelCount) { Container<Actor>() }
        mapPlayButton = pulse(button("", TEAL, TEAL_TEXT) { game.startRun() })
        val tileCells = ArrayList<Cell<Stack>>(levelCount)
        mapNodes = kotlin.Array(levelCount) { i ->
            // Square tile (number + corner badge) over a name plate + status.
            val tile = Stack()
            tile.add(mapTileBgs[i])
            tile.add(mapTileNumbers[i])
            tile.add(Container(mapTileBadges[i]).align(Align.bottomRight).padRight(-5f).padBottom(-5f))
            val plate = Table()
            plate.background = darkChip
            plate.pad(2f, 8f, 2f, 8f)
            plate.add(mapNodeNames[i])
            val node = Table()
            tileCells.add(node.add(tile).size(NODE_TILE_SIZE))
            node.row()
            node.add(plate).padTop(5f)
            node.row()
            node.add(mapStatusHolders[i]).padTop(3f)
            node.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) = game.selectLevel(i)
            })
            node
        }
        mapTileCells = tileCells.toTypedArray()
        // Final stretch on top, first at the bottom — reads like a climb. The
        // nodes zig-zag left/right with dotted connectors angled to match.
        // The trail lives in a ScrollPane: when the level list outgrows the
        // panel's height cap it scrolls instead of covering the top bar.
        val mapTrail = Table()
        for (i in levelCount - 1 downTo 0) {
            val onLeft = i % 2 == 0
            mapTrail.add(mapNodes[i]).expandX()
                .align(if (onLeft) Align.left else Align.right)
                .padLeft(if (onLeft) 30f else 0f)
                .padRight(if (onLeft) 0f else 30f)
            mapTrail.row()
            if (i > 0) {
                val dots = TextureRegion(pathDotsIcon)
                if ((i - 1) % 2 != 0) dots.flip(true, false) // lower node on the right
                mapTrail.add(Image(dots)).size(96f, 44f).pad(-4f, 0f, -4f, 0f)
                mapTrail.row()
            }
        }
        mapPanel.add(ScrollPane(mapTrail).apply {
            setScrollingDisabled(true, false)
            fadeScrollBars = true
        }).grow()

        // --- HEROES: 3D turntable behind, browse + unlock + equip --------------
        heroNameLabel = Label("", Label.LabelStyle(titleFont, GOLD)).apply { setAlignment(Align.center) }
        heroIntroLabel = Label("", labelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        heroStatusLabel = Label("", mutedSmall).apply { setAlignment(Align.center) }
        heroActionButton = button("", TEAL, TEAL_TEXT) { game.equipOrUnlockCharacter() }
        heroesPanel = Table()
        heroesPanel.background = rounded(BG_DEEP_TRANSLUCENT, 18, BORDER_SOFT, 2)
        heroesPanel.pad(14f)
        heroesPanel.add(Label("Choose Your Hero", labelStyle).apply { setAlignment(Align.center) })
            .growX().colspan(3)
        heroesPanel.row()
        heroesPanel.add(Label("Drag the screen to spin your hero!", mutedSmall).apply {
            setAlignment(Align.center)
        }).growX().colspan(3).padBottom(6f)
        heroesPanel.row()
        heroesPanel.add(button("  <  ", PANEL, TEXT) { game.cycleCharacter(-1) }).height(84f).width(58f)
        val heroCard = Table()
        heroCard.add(heroNameLabel).growX()
        heroCard.row()
        heroCard.add(heroIntroLabel).growX()
        heroCard.row()
        heroCard.add(heroStatusLabel).growX()
        heroesPanel.add(heroCard).growX().pad(0f, 10f, 0f, 10f)
        heroesPanel.add(button("  >  ", PANEL, TEXT) { game.cycleCharacter(1) }).height(84f).width(58f)
        heroesPanel.row()
        heroesPanel.add(heroActionButton).height(56f).growX().colspan(3).padTop(10f)

        // --- SHOP: chest (odds disclosed) + gem packs + free-path note ---------
        shopPanel = Table()
        shopPanel.background = rounded(BG_DEEP, 18, BORDER_SOFT, 2)
        shopPanel.pad(14f)
        shopPanel.add(Label("Shop", Label.LabelStyle(titleFont, GOLD)).apply {
            setAlignment(Align.center)
        }).growX().padBottom(8f)
        shopPanel.row()
        val chestCard = Table()
        chestCard.background = cardBg
        chestCard.pad(10f, 14f, 10f, 14f)
        chestCard.add(Label("Sword Skin Chest", labelStyle)).left().growX()
        chestCard.row()
        // COMPLIANCE: odds rendered straight from Rarity.entries — the weights
        // ARE the disclosed percentages; never hard-code a second copy.
        val oddsText = GdxStringBuilder(96).also { sb ->
            sb.append("Drop rates: ")
            for (rarity in Rarity.entries) {
                sb.append(rarity.displayName).append(' ').append(rarity.dropChancePercent).append("%  ")
            }
        }
        chestCard.add(Label(oddsText, mutedSmall).apply { setWrap(true) }).left().growX().padBottom(6f)
        chestCard.row()
        chestCard.add(button("OPEN CHEST - ${GachaSystem.CHEST_COST_GEMS} GEMS", GOLD, GOLD_TEXT) {
            game.onChestButtonClicked()
        }).height(48f).growX()
        shopPanel.add(chestCard).growX().padBottom(10f)
        shopPanel.row()
        shopPanel.add(Label("Gem Packs", labelStyle)).left().padBottom(4f)
        shopPanel.row()
        for (pack in GemStoreCatalog.PACKS) {
            val packRow = Table()
            packRow.background = cardBg
            packRow.pad(6f, 14f, 6f, 6f)
            packRow.add(Label("${pack.gems} GEMS", Label.LabelStyle(font, PURPLE_LIGHT))).left().growX()
            packRow.add(button(pack.priceHint, PURPLE, Color.WHITE) {
                game.requestGemPackPurchase(pack.productId)
            }).height(42f).width(96f)
            shopPanel.add(packRow).growX().padBottom(6f)
            shopPanel.row()
        }
        shopPanel.add(Label(
            "Gems are FREE while you run: 1 gem every ${RunnerSystem.METERS_PER_GEM} m!",
            mutedSmall
        ).apply {
            setWrap(true)
            setAlignment(Align.center)
        }).growX().padTop(4f)

        // --- SETTINGS: sound toggles (gear chip in the top bar) -----------------
        toggleOnStyle = buttonStyle(TEAL, TEAL_TEXT)
        toggleOffStyle = buttonStyle(PANEL, MUTED)
        coinSoundToggle = TextButton("", toggleOnStyle).also { btn ->
            btn.label.setAlignment(Align.center)
            btn.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.toggleCoinSounds()
                    refreshSettings()
                }
            })
        }
        footstepSoundToggle = TextButton("", toggleOnStyle).also { btn ->
            btn.label.setAlignment(Align.center)
            btn.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.toggleFootstepSounds()
                    refreshSettings()
                }
            })
        }
        actionSoundToggle = TextButton("", toggleOnStyle).also { btn ->
            btn.label.setAlignment(Align.center)
            btn.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.toggleActionSounds()
                    refreshSettings()
                }
            })
        }
        settingsPanel = Table()
        settingsPanel.background = rounded(BG_DEEP, 18, BORDER_SOFT, 2)
        settingsPanel.pad(14f)
        settingsPanel.add(Label("Settings", Label.LabelStyle(titleFont, GOLD)).apply {
            setAlignment(Align.center)
        }).growX().padBottom(8f)
        settingsPanel.row()
        settingsPanel.add(Label("Sounds", labelStyle)).left().padBottom(4f)
        settingsPanel.row()
        settingsPanel.add(settingRow(cardBg, "Coins & Pickups", "Coin dings, gems and boost jingles", coinSoundToggle))
            .growX().padBottom(6f)
        settingsPanel.row()
        settingsPanel.add(settingRow(cardBg, "Running Footsteps", "Your hero's steps on the road", footstepSoundToggle))
            .growX().padBottom(6f)
        settingsPanel.row()
        settingsPanel.add(settingRow(cardBg, "Jump & Crash", "Jumps, tumbles and barrier smashes", actionSoundToggle))
            .growX().padBottom(10f)
        settingsPanel.row()
        settingsPanel.add(button("DONE", TEAL, TEAL_TEXT) { game.backToMenu() }).height(52f).growX()

        // --- DEAD: game-over panel ---------------------------------------------
        resultLabel = Label("", labelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        // Ad-first hierarchy: Revive is the hero (full width, tallest, pulsing),
        // x2 gold next. The free path (Run again) stays clearly visible below —
        // required for Families policy; only the emphasis changes.
        reviveButton = pulse(button("REVIVE - WATCH AD", GOLD, GOLD_TEXT) { game.requestRevive() })
        doubleButton = button("x2 GOLD - WATCH AD", GOLD, GOLD_TEXT) {
            doubleButton.isVisible = false
            doubleCell.height(0f)
            deadPanel.invalidateHierarchy()
            game.requestDoubleRunCoins()
        }
        deadPanel = Table()
        deadPanel.background = rounded(BG_DEEP, 18, PINK_DARK, 3)
        deadPanel.pad(14f)
        deadPanel.add(Label("RUN OVER!", Label.LabelStyle(titleFont, PINK)).apply {
            setAlignment(Align.center)
        }).growX().colspan(2).padBottom(4f)
        deadPanel.row()
        deadPanel.add(resultLabel).growX().colspan(2).padBottom(10f)
        deadPanel.row()
        reviveCell = deadPanel.add(reviveButton).height(62f).growX().colspan(2).pad(3f)
        deadPanel.row()
        doubleCell = deadPanel.add(doubleButton).height(54f).growX().colspan(2).pad(3f)
        deadPanel.row()
        deadPanel.add(button("RUN AGAIN", TEAL, TEAL_TEXT) { game.startRun() })
            .height(48f).growX().pad(3f).padTop(8f)
        deadPanel.add(button("HOME", PANEL, TEXT) { game.backToMenu() })
            .height(48f).growX().pad(3f).padTop(8f)

        // --- PAUSED: freeze card (pause chip, or the app going to background) ---
        pausePanel = Table()
        pausePanel.background = rounded(BG_DEEP, 18, TEAL_DARK, 3)
        pausePanel.pad(16f)
        pausePanel.add(Label("PAUSED", Label.LabelStyle(titleFont, TEAL)).apply {
            setAlignment(Align.center)
        }).growX().padBottom(4f)
        pausePanel.row()
        pausePanel.add(Label("Take a breath - your run is waiting!", mutedSmall).apply {
            setWrap(true)
            setAlignment(Align.center)
        }).growX().padBottom(12f)
        pausePanel.row()
        pausePanel.add(pulse(button("RESUME", TEAL, TEAL_TEXT) { game.resumeRun() }))
            .height(62f).growX().padBottom(6f)
        pausePanel.row()
        // HOME banks everything earned so far (RunnerSystem.quitRun) — quitting
        // is never punished.
        pausePanel.add(button("QUIT TO HOME", PANEL, TEXT) { game.backToMenu() })
            .height(48f).growX()

        // --- toast: last game event ---------------------------------------------
        // The whole row fades (chip included), so no empty lozenge lingers
        // between toasts. Hidden until the first message.
        toastLabel = Label("", Label.LabelStyle(font, GOLD))
        toastLabel.setWrap(true)
        toastLabel.setAlignment(Align.center)
        toastRow = Table()
        toastRow.background = darkChip
        toastRow.pad(8f)
        toastRow.add(toastLabel).growX()
        toastRow.color.a = 0f
        panelRoot.add(toastRow).growX().pad(0f, 8f, 6f, 8f)
        panelRoot.row()

        // --- bottom nav: MAP | HOME | HEROES | SHOP ------------------------------
        navButtons = kotlin.Array(NAV_LABELS.size) { i ->
            val btn = TextButton(NAV_LABELS[i], TextButton.TextButtonStyle().also {
                it.font = font
                it.fontColor = TEXT
            })
            btn.label.color.set(MUTED)
            btn.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    when (i) {
                        TAB_MAP -> game.openMap()
                        TAB_HOME -> game.backToMenu()
                        TAB_HEROES -> game.openCharacterSelect()
                        else -> game.openShop()
                    }
                }
            })
            btn
        }
        navBar = Table()
        navBar.background = rounded(BG_DEEP, 16, BORDER_SOFT, 2)
        for (i in navButtons.indices) navBar.add(navButtons[i]).height(54f).growX()
        navHolder.fill()
        panelRoot.add(navHolder).growX().pad(0f, 8f, 8f, 8f)

        fontGenerator.dispose() // fonts own their textures; the generator is done
    }

    // =========================================================================
    // Screen switches (all user-driven — allocation here is fine)
    // =========================================================================

    /** First-launch quest intro; LET'S GO leads into the hero picker. */
    fun showOnboarding() {
        showPanel(onboardingPanel)
        setNav(NAV_HIDDEN)
    }

    /** HOME: equipped hero, best run, the selected level's goal, RUN!. */
    fun setHome(bestScore: Int) {
        showPanel(homePanel)
        setNav(TAB_HOME)
        homeHeroLabel.setText(game.equippedCharacter.displayName)
        homeBestLabel.setText(if (bestScore > 0) "Best Run: $bestScore m" else "First run - good luck!")
        val spec = LevelCatalog.LEVELS[game.selectedLevelIndex]
        val next = game.selectedLevelIndex + 1
        eventText.setLength(0)
        eventText.append("NEXT UP: ").append(spec.displayName)
        if (next < LevelCatalog.LEVELS.size && next >= game.unlockedLevels) {
            eventText.append(" - run ").append(spec.goalMeters).append(" m to open ")
                .append(LevelCatalog.LEVELS[next].displayName).append('!')
        }
        homeLevelLabel.setText(eventText)
        scoreLabel.setText("")
        coinCountLabel.setText("0")
    }

    /** MAP: level nodes styled by state (selected / open / locked + goal). */
    fun showMap() {
        showPanel(mapPanel)
        setNav(TAB_MAP)
        refreshMap()
    }

    /** Syncs the map nodes with the game's selection/unlock state. */
    fun refreshMap() {
        val selected = game.selectedLevelIndex
        for (i in mapNodes.indices) {
            val spec = LevelCatalog.LEVELS[i]
            val unlocked = i < game.unlockedLevels
            mapNodeNames[i].setText(spec.displayName.uppercase())
            mapNodeNames[i].color.set(if (unlocked) GOLD else MUTED)
            mapTileBgs[i].drawable = when {
                i == selected -> mapTileSelectedBg
                unlocked -> mapTileOpenBg
                else -> mapTileLockedBg
            }
            mapTileNumbers[i].color.set(
                when {
                    i == selected -> TEAL_TEXT
                    unlocked -> GOLD
                    else -> MUTED
                }
            )
            mapTileBadges[i].drawable = when {
                i == selected -> mapPlayBadge
                !unlocked -> mapLockBadge
                else -> null
            }
            mapTileCells[i].size(if (i == selected) NODE_TILE_SIZE_SELECTED else NODE_TILE_SIZE)
            if (i == selected) {
                // The active node carries the go-action, prototype-style.
                mapPlayButton.setText("PLAY ${spec.displayName.uppercase()}")
                mapStatusHolders[i].actor = mapPlayButton
            } else {
                mapStatusHolders[i].actor = mapNodeStatus[i]
                val prev = if (i > 0) LevelCatalog.LEVELS[i - 1] else null
                when {
                    !unlocked && i == game.unlockedLevels -> {
                        // The very next stretch: an encouraging goal, not a wall.
                        mapNodeStatus[i].setText("Beat ${prev!!.displayName} - run ${prev.goalMeters} m!")
                        mapNodeStatus[i].color.set(TEAL)
                    }
                    !unlocked -> {
                        mapNodeStatus[i].setText("LOCKED - beat ${prev!!.displayName} first")
                        mapNodeStatus[i].color.set(MUTED)
                    }
                    spec.goalMeters > 0 -> {
                        mapNodeStatus[i].setText("Tap to select - goal: ${spec.goalMeters} m")
                        mapNodeStatus[i].color.set(TEXT)
                    }
                    else -> {
                        mapNodeStatus[i].setText("Tap to select - endless finale!")
                        mapNodeStatus[i].color.set(TEXT)
                    }
                }
            }
        }
        mapPanel.invalidateHierarchy()
    }

    /** HEROES: name/quote/unlock state for the hero on the 3D turntable. */
    fun showHeroes(spec: CharacterSpec, unlocked: Boolean, equipped: Boolean) {
        showPanel(heroesPanel)
        setNav(TAB_HEROES)
        heroNameLabel.setText(spec.displayName)
        heroIntroLabel.setText(spec.intro)
        heroStatusLabel.setText(
            when {
                spec.costGems == 0L -> "STARTER HERO"
                unlocked -> "UNLOCKED"
                else -> "Costs ${spec.costGems} gems - earn them by running!"
            }
        )
        when {
            equipped -> {
                heroActionButton.style = heroActionTeal
                heroActionButton.setText("EQUIPPED - LET'S RUN!")
            }
            unlocked -> {
                heroActionButton.style = heroActionTeal
                heroActionButton.setText("EQUIP")
            }
            else -> {
                heroActionButton.style = heroActionPurple
                heroActionButton.setText("UNLOCK - ${spec.costGems} GEMS")
            }
        }
        // Name slides in on every cycle so tapping < > feels alive.
        heroNameLabel.clearActions()
        heroNameLabel.color.a = 0f
        heroNameLabel.addAction(Actions.fadeIn(0.2f))
    }

    /** SHOP: static layout; balances live in the always-on pills. */
    fun showShop() {
        showPanel(shopPanel)
        setNav(TAB_SHOP)
    }

    /** SETTINGS: sound toggles; the nav stays up so any tab leads back out. */
    fun showSettings() {
        showPanel(settingsPanel)
        setNav(TAB_NONE)
        refreshSettings()
    }

    /** Syncs the toggle buttons with the game's persisted sound switches. */
    private fun refreshSettings() {
        applyToggle(coinSoundToggle, game.coinSoundsOn)
        applyToggle(footstepSoundToggle, game.footstepSoundsOn)
        applyToggle(actionSoundToggle, game.actionSoundsOn)
    }

    private fun applyToggle(btn: TextButton, on: Boolean) {
        btn.style = if (on) toggleOnStyle else toggleOffStyle
        btn.setText(if (on) "ON" else "OFF")
    }

    fun setRunning() {
        showPanel(null) // clear view while running: top bar + toast only
        setNav(NAV_HIDDEN)
        runCenterGroup.isVisible = true
        runGoalMeters = LevelCatalog.LEVELS[game.selectedLevelIndex].goalMeters
        goalBar.isVisible = runGoalMeters > 0
        goalFill.setScaleX(0f)
        coinText.setLength(0)
        coinText.append(runner.runCoins)
        coinCountLabel.setText(coinText)
        refreshScore()
        // Controls reminder on the session's first run only.
        if (!shownRunHint) {
            shownRunHint = true
            toast("Swipe to steer, swipe up to jump. Grab the coins!")
        }
    }

    /** PAUSED: bottom-docked freeze card (runner stays in view above it); score chip stays up so nothing feels lost. */
    fun showPaused() {
        showPanel(pausePanel)
        setNav(NAV_HIDDEN)
        runCenterGroup.isVisible = true
        goalBar.isVisible = runGoalMeters > 0
    }

    // =========================================================================
    // Live-run events
    // =========================================================================

    /** Instant top-bar update the moment a coin is grabbed (with the ding). */
    fun onCoinCollected(runCoins: Int) {
        coinText.setLength(0)
        coinText.append(runCoins)
        coinCountLabel.setText(coinText)
        // The coin icon pops — juicy confirmation kids notice without reading.
        // Just a float; render() decays it. Rapid coin trails stay smooth.
        coinPopScale = COIN_POP_SCALE
    }

    fun onRunEnded(
        distanceMeters: Int,
        runCoins: Int,
        bestScore: Int,
        newBest: Boolean,
        canRevive: Boolean,
        canDouble: Boolean,
    ) {
        showPanel(deadPanel)
        setNav(NAV_HIDDEN)
        runCenterGroup.isVisible = false
        goalBar.isVisible = false
        // Collapse unavailable ad offers so no dead space pushes the panel around.
        reviveButton.isVisible = canRevive
        reviveCell.height(if (canRevive) 62f else 0f)
        doubleButton.isVisible = canDouble
        doubleCell.height(if (canDouble) 54f else 0f)
        deadPanel.invalidateHierarchy()
        resultLabel.setText(
            if (newBest)
                "NEW BEST! You ran $distanceMeters m: +$runCoins gold, +${runner.runGems} gems!"
            else
                "You ran $distanceMeters m: +$runCoins gold, +${runner.runGems} gems!  Best: $bestScore m"
        )
    }

    /**
     * One-line event feedback ("Epic sword unlocked!", "Need 12 more gems").
     * Fades itself out after a few seconds so stale messages never linger
     * into the next screen or run.
     */
    fun toast(message: String) {
        toastLabel.setText(message)
        toastRow.clearActions()
        toastRow.color.a = 0f
        toastRow.addAction(Actions.sequence(
            Actions.fadeIn(0.25f),
            Actions.delay(TOAST_HOLD_SECONDS),
            Actions.fadeOut(0.6f)
        ))
    }

    // =========================================================================
    // Frame loop
    // =========================================================================

    fun render(delta: Float) {
        refreshAccumulator += delta
        if (refreshAccumulator >= REFRESH_INTERVAL) {
            refreshAccumulator = 0f
            if (runner.state == RunnerSystem.State.RUNNING) refreshScore()
        }
        rollBalances(delta)
        if (coinPopScale > 1f) {
            coinPopScale = (coinPopScale - COIN_POP_DECAY_PER_SEC * delta).coerceAtLeast(1f)
            coinIconImage.setOrigin(coinIconImage.width / 2f, coinIconImage.height / 2f)
            coinIconImage.setScale(coinPopScale)
        }
        stage.act(delta)
        stage.viewport.apply(true)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        updatePanelCap()
    }

    override fun dispose() {
        stage.dispose()
        font.dispose()
        titleFont.dispose()
        smallFont.dispose()
        captionFont.dispose()
        for (i in 0 until ownedTextures.size) ownedTextures[i].dispose()
    }

    // =========================================================================
    // Internals
    // =========================================================================

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

    /** [icon] over-caption currency pill, prototype-style. */
    private fun currencyPill(icon: Texture, caption: String, value: Label, bg: Drawable): Table {
        val pill = Table()
        pill.background = bg
        pill.pad(3f, 10f, 3f, 12f)
        pill.add(Image(icon)).size(20f, 20f).padRight(6f)
        val textCol = Table()
        textCol.add(Label(caption, Label.LabelStyle(captionFont, MUTED))).left()
        textCol.row()
        textCol.add(value).left()
        pill.add(textCol)
        return pill
    }

    /** One settings row: title + hint on the left, ON/OFF toggle on the right. */
    private fun settingRow(bg: Drawable, title: String, hint: String, toggle: TextButton): Table {
        val row = Table()
        row.background = bg
        row.pad(6f, 12f, 6f, 6f)
        val textCol = Table()
        textCol.add(Label(title, Label.LabelStyle(font, TEXT))).left()
        textCol.row()
        textCol.add(Label(hint, Label.LabelStyle(smallFont, MUTED))).left()
        row.add(textCol).left().growX()
        row.add(toggle).height(42f).width(84f)
        return row
    }

    /** One onboarding control row: badge + title + hint, card-styled. */
    private fun controlRow(bg: Drawable, badge: String, title: String, hint: String): Table {
        val row = Table()
        row.background = bg
        row.pad(6f, 12f, 6f, 12f)
        row.add(Label(badge, Label.LabelStyle(font, PINK))).width(40f)
        val textCol = Table()
        textCol.add(Label(title, Label.LabelStyle(font, TEXT))).left()
        textCol.row()
        textCol.add(Label(hint, Label.LabelStyle(smallFont, MUTED))).left()
        row.add(textCol).left().growX()
        return row
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

    /**
     * Swaps which bottom screen occupies the holder (null = none, while
     * running). No-op when already showing, so cycle taps don't re-fade.
     */
    private fun showPanel(panel: Table?) {
        if (panelHolder.actor === panel) return
        panelHolder.actor = panel
        // The map card floats centered in the free space between the top bar
        // and the bottom edge; every other screen docks at the bottom — the
        // paused card included, so the frozen runner stays visible above it.
        if (panel === mapPanel) panelHolder.center() else panelHolder.bottom()
        // The pause chip only exists on the live run screen (panel == null).
        pauseChip.isVisible = panel == null
        pauseChipCell.size(if (panel == null) 44f else 0f, 44f)
            .padLeft(if (panel == null) 8f else 0f)
        pauseChip.invalidateHierarchy()
        if (panel != null) reveal(panel)
        if (panel !== null && panel !== deadPanel && panel !== pausePanel) {
            runCenterGroup.isVisible = false
            goalBar.isVisible = false
        }
    }

    /**
     * Caps every panel's height to the space under the top bar (currency
     * pills + goal bar) so no screen — the map card especially — can ever
     * cover them; taller content scrolls inside its panel instead.
     */
    private fun updatePanelCap() {
        val safeTop = Gdx.graphics.safeInsetTop / Gdx.graphics.density.coerceAtLeast(1f)
        panelHolderCell.padTop(TOP_BAR_RESERVE + safeTop)
        panelHolder.maxHeight(
            (stage.viewport.worldHeight - PANEL_HEIGHT_RESERVE - safeTop).coerceAtLeast(220f)
        )
        panelHolder.invalidateHierarchy()
    }

    /**
     * Shows the nav with one active tab ([TAB_NONE] = none highlighted, used
     * by settings), or hides it ([NAV_HIDDEN]). The settings gear shares the
     * nav's visibility: it belongs to the menu screens only.
     */
    private fun setNav(activeTab: Int) {
        navHolder.actor = if (activeTab == NAV_HIDDEN) null else navBar
        for (i in navButtons.indices) {
            navButtons[i].label.color.set(if (i == activeTab) PINK else MUTED)
        }
        val onMenus = activeTab != NAV_HIDDEN
        settingsChip.isVisible = onMenus
        settingsChipCell.size(if (onMenus) 44f else 0f, 44f)
            .padLeft(if (onMenus) 8f else 0f)
        settingsChip.invalidateHierarchy()
    }

    /** Fade a panel in when it appears instead of snapping. */
    private fun reveal(panel: Table) {
        panel.clearActions()
        panel.color.a = 0f
        panel.addAction(Actions.fadeIn(0.25f, Interpolation.smooth))
    }

    /**
     * Eases the displayed balances toward the real ones (ease-out, min 1/frame
     * so they always land exactly). Repaints only while a value is moving;
     * reused StringBuilders keep this allocation-free.
     */
    private fun rollBalances(delta: Float) {
        val targetGold = economy.gold
        if (shownGold != targetGold) {
            shownGold = if (shownGold < 0L) targetGold else step(shownGold, targetGold, delta)
            goldText.setLength(0)
            goldText.append(shownGold)
            goldLabel.setText(goldText)
        }
        val targetGems = economy.gems
        if (shownGems != targetGems) {
            shownGems = if (shownGems < 0L) targetGems else step(shownGems, targetGems, delta)
            gemsText.setLength(0)
            gemsText.append(shownGems)
            gemsLabel.setText(gemsText)
        }
    }

    private fun step(shown: Long, target: Long, delta: Float): Long {
        val diff = target - shown
        val move = (Math.abs(diff.toDouble()) * (COUNT_SPEED * delta).coerceAtMost(1f))
            .toLong().coerceAtLeast(1L)
        return if (diff > 0) (shown + move).coerceAtMost(target) else (shown - move).coerceAtLeast(target)
    }

    private fun refreshScore() {
        scoreText.setLength(0)
        scoreText.append(runner.distanceMeters).append(" m")
        scoreLabel.setText(scoreText)
        if (runGoalMeters > 0) {
            goalFill.setScaleX((runner.distanceMeters.toFloat() / runGoalMeters).coerceAtMost(1f))
        }
    }

    /**
     * Color language kids read at a glance: TEAL = go/play, GOLD = watch an
     * ad (always optional), PURPLE = spend gems, PANEL = everything else.
     */
    private fun button(text: String, base: Color, textColor: Color = Color.WHITE, onClick: () -> Unit): TextButton {
        val btn = TextButton(text, buttonStyle(base, textColor))
        btn.label.setAlignment(Align.center)
        btn.pad(4f, 10f, 4f, 10f)
        btn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) = onClick()
        })
        return btn
    }

    private fun buttonStyle(base: Color, textColor: Color): TextButton.TextButtonStyle {
        val border = Color(base.r * 0.55f, base.g * 0.55f, base.b * 0.55f, 1f)
        return TextButton.TextButtonStyle().also {
            it.font = font
            it.fontColor = textColor
            it.up = rounded(base, 14, border, 3)
            it.down = rounded(border, 14)
        }
    }

    /**
     * Rounded-rect NinePatch drawable, drawn once into a small Pixmap
     * (optionally with a border ring) — the whole prototype look without a
     * single asset file.
     */
    private fun rounded(fill: Color, radius: Int, border: Color? = null, borderWidth: Int = 0): NinePatchDrawable {
        val size = radius * 2 + 4 + borderWidth * 2
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        if (border != null && borderWidth > 0) {
            pm.setColor(border)
            fillRoundRect(pm, 0, 0, size, size, radius + borderWidth)
            pm.setColor(fill)
            fillRoundRect(pm, borderWidth, borderWidth, size - borderWidth * 2, size - borderWidth * 2, radius)
        } else {
            pm.setColor(fill)
            fillRoundRect(pm, 0, 0, size, size, radius)
        }
        val tex = Texture(pm)
        pm.dispose()
        ownedTextures.add(tex)
        val split = radius + borderWidth + 1
        return NinePatchDrawable(NinePatch(TextureRegion(tex), split, split, split, split))
    }

    private fun fillRoundRect(pm: Pixmap, x: Int, y: Int, w: Int, h: Int, r: Int) {
        pm.fillRectangle(x + r, y, w - 2 * r, h)
        pm.fillRectangle(x, y + r, w, h - 2 * r)
        pm.fillCircle(x + r, y + r, r)
        pm.fillCircle(x + w - r - 1, y + r, r)
        pm.fillCircle(x + r, y + h - r - 1, r)
        pm.fillCircle(x + w - r - 1, y + h - r - 1, r)
    }

    private fun solid(r: Float, g: Float, b: Float, a: Float): Drawable =
        TextureRegionDrawable(TextureRegion(whitePixel)).tint(Color(r, g, b, a))

    // Map tile faces + badges, created once (refreshMap runs on every tap).
    private val mapTileSelectedBg = rounded(TEAL, 16, TEAL_DARK, 4)
    private val mapTileOpenBg = rounded(PURPLE, 16, GOLD_DARK, 3)
    private val mapTileLockedBg = rounded(PANEL, 16, BORDER_FAINT, 3)
    private val mapLockBadge = TextureRegionDrawable(TextureRegion(lockBadgeIcon))
    private val mapPlayBadge = TextureRegionDrawable(TextureRegion(playBadgeIcon))

    private companion object {
        const val REFRESH_INTERVAL = 0.25f
        const val TOAST_HOLD_SECONDS = 3.5f
        const val COIN_POP_SCALE = 1.5f
        const val COIN_POP_DECAY_PER_SEC = 2.6f // 1.5 -> 1.0 in ~0.19 s
        const val COUNT_SPEED = 6f // rolling counters cover ~99% in ~0.75 s
        const val GOAL_BAR_WIDTH = 170f
        const val NODE_TILE_SIZE = 72f
        const val NODE_TILE_SIZE_SELECTED = 84f

        // Vertical space reserved around panels: top bar + goal bar above
        // (TOP_BAR_RESERVE, also the holder's padTop) plus toast row + nav
        // bar below (all in dp).
        const val TOP_BAR_RESERVE = 84f
        const val PANEL_HEIGHT_RESERVE = 210f

        const val NAV_HIDDEN = -1
        const val TAB_MAP = 0
        const val TAB_HOME = 1
        const val TAB_HEROES = 2
        const val TAB_SHOP = 3
        const val TAB_NONE = 4 // nav visible, nothing highlighted (settings)
        val NAV_LABELS = arrayOf("MAP", "HOME", "HEROES", "SHOP")

        // Star Voyage palette: deep purple night, teal go-actions, gold ads,
        // purple gem spends, pink accents.
        val BG_DEEP: Color = Color.valueOf("0F0826")
        val BG_DEEP_TRANSLUCENT: Color = Color(0.059f, 0.031f, 0.149f, 0.85f)
        val PANEL: Color = Color.valueOf("2A1B52")
        val TEAL: Color = Color.valueOf("22D3B8")
        val TEAL_DARK: Color = Color.valueOf("0E7A69")
        val TEAL_TEXT: Color = Color.valueOf("0E1F1B")
        val GOLD: Color = Color.valueOf("FFD24C")
        val GOLD_DARK: Color = Color.valueOf("B8860A")
        val GOLD_TEXT: Color = Color.valueOf("5C3D00")
        val PINK: Color = Color.valueOf("FF4D8D")
        val PINK_DARK: Color = Color.valueOf("A31E56")
        val PURPLE: Color = Color.valueOf("7B61FF")
        val PURPLE_LIGHT: Color = Color.valueOf("BBAAFF")
        val TEXT: Color = Color.valueOf("F5F1FF")
        val MUTED: Color = Color.valueOf("B7A9E8")
        val OUTLINE: Color = Color(0.06f, 0.03f, 0.15f, 1f)
        val BORDER_SOFT: Color = Color(1f, 1f, 1f, 0.22f)
        val BORDER_FAINT: Color = Color(1f, 1f, 1f, 0.12f)
    }
}
