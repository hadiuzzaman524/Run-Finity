# Forge & Flail: Coin Run

Small-scope 3D voxel endless runner (Subway-style; libGDX + Kotlin) for mobile,
audience under 16: auto-run down a 3-lane road, swipe to change lanes, swipe up
to jump, grab coins, dodge barriers. It shares this repo with a pre-existing
Jetpack Compose template in `:app` (see README.md) — the two do not depend on
each other. (The game pivoted from an idle-tycoon prototype; see "Parked
systems" below.)

## Modules

| Module | What it is |
|---|---|
| `:game-core` | All game code. Pure Kotlin/JVM + libGDX. **No Android/SDK imports, ever.** |
| `:desktop` | LWJGL3 dev launcher with debug (fake) monetization handlers. |
| `android-integration/` | Reference-only Play Billing + AdMob implementations. **Not in the build.** |
| `:app` | Unrelated Compose clean-architecture template (legacy of this repo). |

## Commands

```bash
./gradlew :desktop:run                                   # run the game on desktop
./gradlew :game-core:compileKotlin :desktop:compileKotlin # quick compile check
```

Controls: swipe left/right = change lane, swipe up = jump (desktop: arrows/AD,
space, Enter to start, P/Esc to pause). The HUD (`ui/GameHud`) has four states —
READY (level picker with locked levels showing their unlock goal, RUN button,
sword-skin gacha chest with the odds disclosure, gem shop), RUNNING (live score
bar + pause chip), PAUSED (resume / quit-to-home card; also entered
automatically when the app backgrounds mid-run), DEAD (revive-by-ad,
x2-coins-by-ad, run again, menu). Every panel's height is capped
(`updatePanelCap`) so no card can cover the currency pills; the map/paused
cards center on screen and tall content scrolls inside its panel.

## Architecture (game-core, package `com.forgeflail.core`)

- `ForgeFlailGame` — root ApplicationListener; owns the 3D scene (fixed chase
  camera, scrolling track tiles) and wires systems. HUD buttons call its
  `startRun` / `requestRevive` / `requestDoubleRunCoins` / chest / purchase methods.
- `runner/RunnerSystem` — the whole simulation: 3-lane world scrolls toward +Z
  (player fixed at z=0), jump physics, row spawner (always leaves a free lane),
  lane-quantized collision, coin banking (delta-banked so revive can't
  double-count), one ad-revive per run. Rare "power gem" pickups trigger a
  ~60 m auto-boost: 1.5x scroll with a shield that smashes barriers instead of
  dying (and clears anything too close when it expires — no unfair deaths).
  Boost is pickup-earned only, never sold. `pauseRun`/`resumeRun`/`quitRun`
  freeze or abandon a run (quit banks everything earned so far).
  Coins/barriers/power gems are fixed pre-allocated pools recycled via an
  `active` flag; renderer binds one ModelInstance per slot. All difficulty
  tuning comes from the `LevelSpec` passed to `startRun`.
- `runner/LevelCatalog` — the level list; `LevelSpec` is pure data (speeds, row
  spacing, spawn d10 odds, `LevelTheme` art set, sky/fog color, `goalMeters`).
  Reaching the goal in one run fires `onLevelCompleted` and unlocks the next
  level: Level 1 "Town Road" -> Level 2 "Village Road" (dirt track, huts,
  fences, hay, log barriers; faster ramp, tighter rows) -> Level 3 "Mountain
  Pass" (stone road, cliff wall, snowy peaks, boulder barriers; fastest ramp,
  tightest rows, flatter per-level `jumpVelocity`). Progression is
  skill-only — never sold or ad-gated; unlock + selection persist in saves.
- `characters/CharacterCatalog` — the 5 selectable runners (`CharacterSpec`:
  colors, `HeadStyle`, proportions). Purely cosmetic; identical gameplay stats
  by design (no pay-to-win for the under-16 audience). First launch shows the
  HUD's onboarding select screen (`showCharacterSelect`); choice persists.
- `assets/VoxelModelFactory` — procedural voxel Models (parametrized character
  body/leg/arm from a CharacterSpec, coin, barrier + log barrier, town + village
  track tiles, sword; legacy anvil/counter/ground) via ModelBuilder;
  `applyRarityTraits` recolors the sword's `"blade"` material for gacha rarity
  tint + glow — the sword rides on the runner's back as the cosmetic skin.
- `economy/EconomyManager` — gold + gems; the only mutation path for balances.
- `gacha/` — `Rarity` (weights ARE the disclosed percentages, sum enforced to
  100), `Blueprint`/`BlueprintCatalog`, `GachaSystem` (weighted roll). Chest now
  awards sword skins.
- `ui/GameHud` — skin-less Scene2D HUD; dynamic labels refresh at 4 Hz via
  reused StringBuilders, never per frame.
- `monetization/` — `BillingHandler` / `AdHandler` platform seams, `AdPlacement`
  (`DOUBLE_RUN_COINS`, `REVIVE_RUN`), `GemStoreCatalog` (all SKUs), `Debug*`
  fakes for desktop.
- `save/SaveManager` — Preferences persistence (`persistRunner`: balances, best
  score, equipped rarity, unlocked/selected level); flushed in `pause()`, not
  per frame.

### Parked systems (from the tycoon prototype, not wired into the game)

`crafting/CraftingManager` and `economy/OfflineEarningsManager` still compile
and keep their tests-of-time (gem/ad skip pricing, 6 h-capped idle earnings)
in case an idle meta-layer returns. Nothing references them at runtime.

## Hard rules

1. **Zero allocation in the render path.** `render()` and everything it calls
   (`RunnerSystem.update`, listeners it may fire) must not allocate: no
   lambdas, no string building, no iterators (use indexed loops), no
   `Vector3()`/`Matrix4()` locals — use preallocated fields and the fixed
   entity pools. Callbacks are allocated once as fields in `ForgeFlailGame`.
2. **game-core stays platform-pure.** Play Billing / AdMob / Android types only
   ever appear behind `BillingHandler` / `AdHandler` in launcher modules.
3. **Platform SDK callbacks must hop to the GL thread** via
   `Gdx.app.postRunnable {}` before touching game state.
4. **Grant purchases only after store confirmation** (PURCHASED + consumed),
   never on flow launch. Gems for a product id come from `GemStoreCatalog.gemsFor`.

## Under-16 compliance (blocking, not optional)

The audience puts this under COPPA / GDPR-K / Google Play Families policy:

- Gacha odds must be shown in-game before purchase — render `Rarity.entries`
  / `dropChancePercent`; never hard-code a second copy of the numbers.
- AdMob must run child-directed: `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE`,
  `MAX_AD_CONTENT_RATING_G`, non-personalized only (see
  `android-integration/AndroidAdHandler.kt`). Rewarded ads are always
  player-initiated via labeled buttons; no interstitials or auto-play.
- Parental gate before the purchase sheet (`AndroidBillingHandler.launchPurchase`).
- Every paid accelerator keeps a free path: gems are earned by running
  (1 per 25 m, `RunnerSystem.METERS_PER_GEM`) and the chest is cosmetic-only.
  Revive is rewarded-ad ONLY, once per run — never sold for gems ("pay to undo
  a loss" is off-limits). No countdown-pressure offers.
- No PII or behavioral tracking in saves/analytics.
- Paid loot boxes are restricted in some regions (e.g. Belgium) — region-gate
  chest purchases at config level before launch there.

Full store checklist: `android-integration/README.md`.
