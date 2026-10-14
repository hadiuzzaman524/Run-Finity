---
name: verify
description: Launch and observe the Forge & Flail desktop game on macOS to verify changes (screenshots, save-state driving).
---

# Verifying Forge & Flail on desktop (macOS)

## Build & run

```bash
./gradlew :game-core:compileKotlin :desktop:compileKotlin   # quick compile check
./gradlew :desktop:run                                       # launches LWJGL3 window "Forge & Flail: Voxel Tycoon (dev)"
```

Run it in the background; wait for the window with `pgrep -f DesktopLauncherKt`.
Kill with `pkill -f DesktopLauncherKt` (note: SIGTERM does NOT flush the save —
`SaveManager.persistRunner` only runs in `pause()`).

## Capturing evidence

Full-screen `screencapture -x` works (screen recording is granted), but the
game window may be behind other windows and **synthetic clicks/keystrokes via
AppleScript fail** — VSCode has no macOS accessibility permission, and the
attempt pops a system permission dialog. So:

- Capture the game window by window ID (composites even when obscured):
  find the ID with a tiny Swift script over `CGWindowListCopyWindowInfo`
  matching window name "Forge", then `screencapture -x -o -l <id> out.png`.
- **Drive state through the save file, not the UI.** Desktop prefs live at
  `~/.prefs/forge_flail_save` (java-properties XML). Back it up first, restore
  after. Useful entries: `selected_character` (roster index; delete file for
  fresh-install onboarding), `unlocked_characters` (bitmask), `gold`, `gems`,
  `initialized`.
- The Home screen shows the equipped hero in a front-facing close-up — good
  for verifying character/model changes without any input.

## Known gaps

- Anything requiring a click/keypress (starting a run, opening the Heroes
  turntable, dismissing the onboarding card) can't be driven headlessly until
  the host app is granted Accessibility in System Settings.
- Back-of-character views (cape, sword-on-back) need either the Heroes
  turntable or a running chase cam — both input-gated (see above).
