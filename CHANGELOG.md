# Changelog

All notable changes to this project are documented in this file.

## [1.3.0-26.3] – 2026-10-19

First release for **Minecraft 26.3**. The browser backend was replaced and the
whole configuration workflow was reworked — please read the *Breaking changes*.

### Added

- **Minecraft 26.3 support.** Ported to the unobfuscated 26.3 client
  (`GuiGraphicsExtractor`, `KeyMapping.Category`, `minecraft.gui.setScreen`).
- **Config file `config/webmapview.json`** with `mapUrl`, `urls`, `enabled` and
  `preloadEngine`. Re-read every time the browser opens, so edits apply without
  restarting the game.
- **ModMenu config screen** to change the map URL, pick/remove saved URLs and
  toggle the mod. Reachable via Mods → WebmapView → gear icon, or `/webmapview`.
- **Engine preloading at startup.** The Chromium engine is downloaded and
  unpacked in the background while the game boots, so pressing **H** opens the
  map immediately. Can be disabled with `"preloadEngine": false`.
- **Visible MCEF progress.** The browser screen now shows the current
  MCEF/Chromium stage (downloading, extracting, installing, initializing) plus a
  percentage while the engine is being prepared.
- **German translation** (`de_de`), alongside the existing `en_us` and `zh_cn`.
- Automatic migration of `urls.txt` and `default_url.txt` into the new config file.

### Changed

- **Browser backend: MCEF Modern** (`net.dimaskama:mcef-modern`) instead of
  `com.cinemamod:mcef`. Existing MCEF installs are no longer used.
- URL management is now config-driven; `UrlManager` is kept as an API facade so
  the rest of the code (default URL, local HTTP server, JS injection) is unchanged.
- `fabric.mod.json`: invalid root-level `donate` entry moved to `custom.donate`,
  `modmenu` entrypoint and `mcef-modern` dependency declared.
- License changed to **MIT**; contributors of the MCEF example mod (CC0-1.0) and
  MCEF Modern (LGPL-2.1) are credited.

### Removed

- **All chat commands**: `/urladd`, `/urlremove`, `/urllist`, `/urlset`,
  `/webmapviewoption` and `/webmapview help`. `/webmapview` now only opens the
  config screen.

### Fixed

- Download progress no longer reports impossible values (previously up to
  “5000 %”); `getPercentage()` already returns 0–100.
- Fabric Loader warning caused by the unknown root key in `fabric.mod.json`.
- Broken control flow in `BasicBrowser` / `WebmapviewClient` that prevented the
  browser from opening after the port to 26.3.

### Breaking changes

- Chat commands were removed. Set the map URL in `config/webmapview.json` or via
  the ModMenu screen instead. Old `urls.txt` / `default_url.txt` are migrated
  automatically from `configs/`.
- Requires **MCEF Modern ≥ 0.3.3+mc26.3** (separate mod, not bundled) instead of
  the classic MCEF.

### Unchanged

- Local HTTP server on port `8080` serving `/local-players.json`.
- JavaScript injection that merges local player data into the squaremap view
  (local players preferred, de-duplicated by player name).
- Default URL, auto-recovery (max. 2 retries + resource pack reload), keybind `H`,
  mod id `webmapview`.

[1.3.0-26.3]: https://github.com/NimaWoods/webmapview/releases/tag/v1.3.0-26.3
