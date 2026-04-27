# Grey's Express

Grey's Express (`gexpress`) is a Fabric 1.21.1 addon for Wathe and Wathe Extended. It adds host-focused tools for running The Harpy Express: Extended games, including map and train presets, ready-area train previews, RTP slot management, host permissions, voice mutes, custom map weather, and extra role/modifier content.

## Features

- `/g map` commands for creating, editing, listing, applying, and snapshotting map presets stored in each world save.
- `/g train preset` commands for saving train reset templates and RTP slots.
- `/g rtp`, `/g host`, `/g voice`, and `/g c4` utility commands.
- A G'Express YACL options screen that combines Wathe Extended settings with G'Express game and map preset settings.
- Bomb Specialist killer role with configurable C4 and grenade prices.
- EOD Specialist modifier with configurable Pliers misfire chance.
- Short-sighted modifier with server-authoritative client fog sync.
- Map-specific snow/sandstorm visuals and fog color overrides.

## Development

Build the mod:

```powershell
.\gradlew.bat build
```

Copy the remapped jar into the configured local Modrinth profile after building:

```powershell
.\gradlew.bat build -PdeployToModrinth=true
```

The output jar is written to `build/libs/Gexpress-<version>.jar`.

## Notes

Map presets are saved under `<world>/gexpress/*.json`; train presets are saved under `<world>/gexpress/trains/*.json`. The server is authoritative for economy/config values and broadcasts accepted values to clients so shop displays match server-side purchases.

## License

Grey's Express is licensed under the GNU General Public License v3.0 or later (`GPL-3.0-or-later`). See [LICENSE](LICENSE).
