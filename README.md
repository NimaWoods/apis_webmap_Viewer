# WebmapView Mod

This is a Fabric mod for Minecraft that allows players to view web map services (squaremap) through an in-game browser interface, supporting features like custom URLs.

## Features

- **Auto-Recovery System**: If you see a black screen when opening the browser, please wait a moment. The mod will automatically refresh the resource pack to fix the issue. You can also manually press F3+T to refresh the resource pack.
- **Custom URL Management**: Add, remove, and manage multiple map service URLs
- **Real-time Player Positioning**: Automatically centers the map on your current location
- **Multi-world Support**: Works in Overworld, Nether, and End dimensions
- **Engine Preloading**: The Chromium engine (MCEF Modern) is downloaded and unpacked
  in the background during game startup, so the browser opens instantly instead of
  waiting for the ~34 MB download on first use.

## Usage

Press the 'H' key to open the browser interface.

## Configuration

The map URL is **not** set via chat commands anymore. Configuration lives in

```
.minecraft/config/webmapview.json
```

```json
{
  "mapUrl": "squaremap-demo.jpenilla.xyz",
  "urls": [
    "squaremap-demo.jpenilla.xyz"
  ],
  "enabled": true,
  "preloadEngine": true
}
```

- `mapUrl` – the map that is opened (without `https://`)
- `urls` – all saved URLs, shown as a picker in the config screen
- `enabled` – `false` disables the mod
- `preloadEngine` – `true` (default) downloads/unpacks the Chromium engine at game
  startup; `false` defers it until the browser is opened for the first time

You can edit the file by hand (it is re-read every time the browser opens) or use
the built-in config screen:

- **ModMenu** (recommended): Mods → WebmapView → gear icon
- `/webmapview` opens the same screen in-game

The config screen lets you change the map URL, pick/remove saved URLs, add new ones
and toggle the mod on/off. Files from older versions (`urls.txt`, `default_url.txt`)
are migrated automatically on first start.

## Troubleshooting

- **Black Screen Issue**: If the browser shows a black screen, the mod will automatically attempt to fix this by refreshing the resource pack (up to 2 times). You can also manually press F3+T to refresh the resource pack.
- **Initialization Failed**: Check your MCEF configuration and ensure all dependencies are properly installed.

## License

MIT – see [LICENSE](LICENSE). Runtime dependency [MCEF Modern](https://modrinth.com/project/mcef-modern) is LGPL-2.1 and is not bundled.
Based on the [mcef-fabric-example-mod](https://github.com/CinemaMod/mcef-fabric-example-mod) by CinemaMod (CC0-1.0).
