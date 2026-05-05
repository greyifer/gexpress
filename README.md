# G'Express

**G'Express is a community-made addon for Wathe and Wathe: Extended, designed specifically for The Harpy Express: Extended server.**

This mod was created for our community Harpy Express server. The server is not run by doctor4t, and some features are built around our own custom server setup rather than general-purpose Wathe servers.

G'Express adds new roles, modifiers, items, map tools, train tools, host settings, and gameplay systems for custom murder mystery rounds aboard the Harpy Express.

Using this mod outside of **The Harpy Express: Extended** may work, but it is not the intended environment.

---

## Commands

All G'Express commands are under `/g`.

| Command | Purpose |
|---|---|
| `/g game start <mode> <effect> [minutes] [seconds]` | Starts a G'Express round. Modes include `modded`, `amnesia`, `discovery`, `loose_ends`, and `murder`; effects include `generic`, `day`, `lobby`, `night`, and `sundown`. |
| `/g game end` | Ends the current round. |
| `/g game end force` | Force-finalizes the current round. |
| `/g setup map create <corner1> <corner2> <name>` | Creates a saved map preset from two corners. |
| `/g setup map delete <name>` | Deletes a saved map preset. |
| `/g setup map edit <name> corners <corner1> <corner2>` | Updates a map preset's overall corners. |
| `/g setup map edit <name> rename <newName>` | Renames a map preset. |
| `/g setup map edit <name> weather <type>` | Sets the map preset weather override. |
| `/g setup map edit <name> fogcolor <hex>` | Sets or clears the map preset fog color. |
| `/g setup map edit <name> wholemap <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the full map bounds. |
| `/g setup map edit <name> playarea <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the playable area bounds. |
| `/g setup map edit <name> template <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the reset template bounds. |
| `/g setup map edit <name> freshair <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Marks an area as valid for Wathe's fresh-air task. |
| `/g setup map edit <name> freshair clear` | Clears the saved fresh-air task area. |
| `/g setup map edit <name> offset <x> <y> <z>` | Sets the play area paste offset. |
| `/g setup map edit <name> spectator <x> <y> <z> <yaw> <pitch>` | Sets the spectator spawn position. |
| `/g setup map edit <name> readytrain here` | Sets the ready train preview position to your current position. |
| `/g setup map edit <name> readytrain <corner>` | Sets the ready train preview corner. |
| `/g setup map edit <name> readytrain clear` | Clears the ready train preview position. |
| `/g setup map edit <name> randomspawns snapshot` | Copies current RTP slots into the map preset as random spawns. |
| `/g setup map edit <name> randomspawns clear` | Clears saved random spawns from the map preset. |
| `/g setup map edit <name> rooms <count>` | Sets the highest Wathe room key number this map can assign, such as `15` for `Room 1` through `Room 15`. |
| `/g setup map list` | Lists saved map presets. |
| `/g setup map show <name>` | Shows a map preset's saved values, including fresh-air area and room key count. |
| `/g setup map set <name> [trainPreset]` | Applies a map preset, optionally with a train preset. |
| `/g setup map default <name> <trainPreset>` | Sets the default train preset for a map preset. |
| `/g setup map snapshot <name>` | Snapshots active Wathe areas, spawns, and RTP slots into a preset. |
| `/g setup train preset save <name>` | Saves the current train reset template as a train preset. |
| `/g setup train preset delete <name>` | Deletes a train preset. |
| `/g setup train preset list` | Lists train presets. |
| `/g setup train preset show <name>` | Shows train preset bounds, offset, RTP slots, and cart count. |
| `/g setup rtp add [x] [y] [z]` | Adds an RTP slot at your position or explicit coordinates. |
| `/g setup rtp remove <id>` | Removes an RTP slot by id. |
| `/g setup rtp removenearest` | Removes the nearest RTP slot. |
| `/g setup rtp list` | Lists RTP slots. |
| `/g setup rtp clear` | Clears all RTP slots. |
| `/g setup rtp enable` / `/g setup rtp disable` | Enables or disables RTP. |
| `/g roles c4 attach <players>` | Admin-attaches C4 to players. |
| `/g roles c4 remove <players>` | Removes attached C4 from players. |
| `/g roles c4 clear` | Clears all attached C4. |
| `/g roles tuning role <id> chance <value>` | Sets a role assignment chance. |
| `/g roles tuning role <id> max <value>` | Sets a role assignment maximum. |
| `/g roles tuning role <id> amount <value>` | Alias for role max. |
| `/g roles tuning modifier <id> chance <value>` | Sets a modifier assignment chance. |
| `/g roles tuning modifier <id> max <value>` | Sets a modifier assignment maximum. |
| `/g roles tuning modifier <id> amount <value>` | Alias for modifier max. |
| `/g roles test role <role> [players]` | Assigns a test role. |
| `/g roles test role clear [players]` | Clears test roles. |
| `/g roles test modifier add <modifier> [players]` / `/g roles test modifier add <players> <modifier>` | Adds a test modifier. Suggestions hide modifiers the selected player already has. |
| `/g roles test modifier remove <modifier> [players]` / `/g roles test modifier remove <players> <modifier>` | Removes a test modifier. Suggestions show only modifiers the selected player has. |
| `/g roles test modifier clear [players]` | Clears test modifiers. |
| `/g roles pelican leave` | Lets a swallowed player climb out of the Pelican. |
| `/g roles pelican release <players>` | Host command that releases selected players from Pelicans. |
| `/g group host add <players>` | Adds host permission to players. |
| `/g group host remove <players>` | Removes host permission from players. |
| `/g group host list` | Lists saved hosts. |
| `/g group trusted add <players>` | Adds the Trusted tag to players. |
| `/g group trusted remove <players>` | Removes the Trusted tag from players. |
| `/g group trusted list` | Lists saved Trusted players. |
| `/g group voice mute <players>` / `/g group voice mute all` | Voice-mutes players or all non-privileged players. |
| `/g group voice unmute <players>` / `/g group voice unmute all` | Removes voice mutes. |
| `/g group voice list` | Lists voice-muted players. |
| `/g admin dev c4back offset x/y/z <value>` | Tunes the C4 back model offset. |
| `/g admin dev c4back rotation x/y/z <value>` | Tunes the C4 back model rotation. |
| `/g admin dev c4back slant <value>` | Tunes the C4 back model diagonal slant. |
| `/g admin dev c4back scale <value>` | Tunes the C4 back model size. |
| `/g admin dev c4preset add [values]` | Adds a C4 placement preset from current settings or explicit values. |
| `/g admin dev c4preset list` | Lists C4 placement presets. |
| `/g admin dev c4preset remove <index>` | Removes a C4 placement preset. |
| `/g admin dev c4preset clear` | Clears C4 placement presets. |
| `/g admin dev roledesc <role> set <description>` | Overrides a G'Express role guidebook description. |
| `/g admin dev roledesc <role> clear` | Clears a role description override. |
| `/g admin dev shortsighted range <value>` | Tunes Short-sighted visibility range. |
| `/g admin dev medicshield blockFlashTicks <value>` | Tunes Medic shield block flash duration. |
| `/g admin dev medicshield breakFlashTicks <value>` | Tunes Medic shield break flash duration. |
| `/g admin dev medicshield blockFlashAlpha <value>` | Tunes Medic shield block flash opacity. |
| `/g admin dev medicshield breakFlashAlpha <value>` | Tunes Medic shield break flash opacity. |
| `/g admin dev silentshadow alpha <value>` | Tunes The Silent shadow opacity. |
| `/g admin dev traincart <preset> <corner1> <corner2>` | Adds a Dancing Carts region to a train preset. |
| `/g admin dev traincart <preset> list` | Lists Dancing Carts regions for a train preset. |
| `/g admin dev traincart <preset> remove <index>` | Removes a Dancing Carts region from a train preset. |
| `/g admin dev traincart <preset> clear` | Clears Dancing Carts regions from a train preset. |

---

## Roles

| Name | Side | Description |
|---|---|---|
| **Bomb Specialist** | Killer | A killer role that can attach C4 to players. Can be defused by eligible players. |
| **The Silent** | Killer | Can move without footsteps during Shadow March, then returns to their original position when the ability ends. |
| **Warlock** | Killer | Marks a player, then uses that mark to kill another player standing close to them. |
| **Harlequin** | Killer | Can swap player skins or train carts to create confusion during rounds. |
| **Puppetmaster** | Killer | Takes control of another living player while leaving their own body vulnerable. |
| **Bounty Hunter** | Killer | Receives a timed bounty target. Killing the bounty pays bonus gold; missing the timer applies weapon cooldowns. |
| **Scatter Brain** | Killer | Scatters every living player, including themself, to safe random positions around the active map. |
| **Skincrawler** | Killer | Steals the skin of a fresh dead body and leaves their previous skin on the corpse. The first gunshot stuns them; a second gunshot while stunned kills them. |
| **Medic** | Civilian | Shields another player from danger and receives visual feedback when that shield is hit or broken. |
| **Snitch** | Civilian | Completes tasks to expose the killers. Progress stays visible beside the mood HUD, and killers are warned/glow the Snitch once they are within the configured task threshold. |
| **Seer** | Civilian | Receives a visual warning whenever someone dies. |
| **Time Master** | Civilian | Can rewind the round, track remaining rewinds, and freeze a looked-at player in place. |
| **Tracker** | Civilian | Can track multiple players at once, making those players glow for the Tracker. |
| **Spy** | Civilian | Plants a paid bug on a looked-at player and receives feed updates when the target completes tasks or interacts with players. |
| **Altruist** | Civilian | Sacrifices themself to revive a dead body. |
| **Godfather** | Neutral | Recruits a Mafioso and Janitor, manages loaded bullets, and uses family instinct through the instinct key. |
| **Mafioso** | Mafia | A recruited family role that fights for the Godfather with mafia weapon rules. |
| **Janitor** | Mafia | A recruited family role that can clean looked-at bodies and use poison vials. |
| **Juggernaut** | Neutral | Starts with a Knife and Revolver. Weapon cooldowns become shorter after kills. Wins by being the last player alive. |
| **Pelican** | Neutral | Can swallow living players. Eaten players see through the Pelican, communicate through a dedicated belly voice channel, and the Pelican sees a top-corner swallow counter. |

---

## Modifiers

| Name | Side | Description |
|---|---|---|
| **EOD Specialist** | Any | Starts with Pliers and can defuse C4 attached to players. Defusing includes a chance of failure if the wrong wire is clipped. |
| **Short-sighted** | Any | Hides players, items, and bodies beyond a configurable distance. |
| **Night Vision** | Any | Improves visibility in dark areas. |
| **Hungry** | Civilian | Can carry a configurable number of food items from platters. |
| **Thirsty** | Civilian | Can carry a configurable number of drink items from trays. |
| **Muted** | Any | Can hear voice chat but cannot speak. |
| **Paranoid** | Civilian | Always sees players as if they are holding suspicious items. |
| **Squeaker** | Any | Raises the player's voice pitch while speaking. |

---

## Items & Blocks

### C4

- Can be attached to players.
- Can be defused by EOD Specialists using Pliers.

### C4 Detonator

- Used for C4-related gameplay.

### Pliers

- Used to defuse C4.
- Defuse attempts can fail if the wrong wire is clipped.

### Bullet

- Bought by the Godfather and loaded into the Godfather revolver.
- Bullet count is shown above the hotbar.

### Poison Vial

- Bought by the Janitor.
- Used for tray-poisoning gameplay.

### Greyifer Plush

- A decorative plush item.
- Plays a sound when used.

### _Iwy Plush

- A decorative plush item using the _Iwy design.
- Plays the plush sound when used.

### Map Blocks

- Sand Layer
- Red Sand Layer
- Fake Suspicious Sand
- Fake Suspicious Gravel
- Pebble Block

---

## Features

### G'Express Settings Screen

A host-focused settings screen for configuring roles, modifiers, maps, and gameplay values.

Includes support for:

- Role chances and role amounts.
- Modifier chances and modifier amounts.
- Fixed maximum killer/Vigilante counts or scaled players-per-killer/Vigilante counts.
- Role-specific values.
- Bounty Hunter timer, reward, and failed-bounty cooldown.
- Mafia lobby-size gate, role-specific starting gold, bullet limits, bullet price, and recruit replacement timing.
- Snitch reveal-task count and killer-warning task threshold.
- Skincrawler body age, cooldown, stun duration, and skin-steal range.
- Spy bug cost, duration, and range.
- Puppetmaster range and self-body kill behavior.
- Time Master Freeze duration, cooldown, range, and per-round uses.
- Scatter Brain cooldown, Tracker limits, Altruist range, Hungry/Thirsty carry limits, Squeaker/Masquerade pitch, Pelican swallow percentage, and first-death shield.

### Ability HUD

G'Express adds an ability HUD for role abilities.

- Cooldown bars appear near the hotbar.
- Roles with multiple active abilities can show multiple bars.
- Ability icons appear beside the bars.
- Time Master shows remaining rewinds beside the rewind bar.
- Scatter Brain, Skincrawler, Spy, Tracker, Altruist, Pelican, Harlequin, Juggernaut, Puppetmaster, Warlock, The Silent, Medic, and Time Master abilities are represented when their role needs a bar.
- Pelican swallow progress renders as a dedicated top-corner counter.

### Tags & Custom Skins

G'Express adds server-side tags for community presentation and custom cosmetics.

- Host and Trusted tags can be managed through `/g group host` and `/g group trusted`.
- Owner, Staff, Designer, Builder, and Passenger tags can be managed through `/g group tag`.
- Tag hierarchy is Owner, Dev, Staff, Host, Trusted, then Passenger.
- The Trusted tag is gold-yellow and gives Trusted players custom Coca-Cola Revolver and Chicken Stick Knife models.
- The Owner tag is dark teal and has full built-in G'Express permissions.
- The Staff tag is teal and can access most host/setup tools without granting creative mode.
- Dev-tagged players can access supporter-gated skin options.

### Amnesia Mode

Amnesia is a G'Express game mode based on modded murder.

- Killer roles are still assigned from the modded role pool.
- Killers do not see other killers as killer teammates through killer identity features.
- Other killers appear as civilian-like targets to killer instinct.
- Killers can kill other killers in this mode.

### Map Tools

G'Express includes map tools for creating and managing custom Harpy Express maps.

- `/g setup map` tools for creating, editing, listing, applying, and snapshotting map presets.
- Map-specific weather and fog overrides.
- Multiple fresh-air task areas for custom outdoor/interior-open sections, each with its own sanity reward percentage.
- Sandstorm and snow visual support.
- Ready-area train preview support.
- Active Wathe area/spawn import helpers.
- Per-map Wathe room key count support for longer custom trains.

### Train & Host Tools

Tools for hosts and custom game setup.

- Train preset support.
- RTP slot management.
- Host management commands.
- Utility commands for G'Express gameplay setup.

---

## Requirements

G'Express is made for **Minecraft 1.21.1** on **Fabric**.

It should be installed on both the client and server.

---

## Credits

Special thanks to [RAT / doctor4t](https://modrinth.com/user/RAT) for creating [The Last Voyage of the Harpy Express](https://modrinth.com/modpack/harpy-express), the original Harpy Express concept, **Wathe: Murder Mystery**, and the videos that inspired this project.

Special thanks to [Rezelyn](https://modrinth.com/user/Rezelyn) for [The Harpy Express: Extended](https://modrinth.com/modpack/the-harpy-express-extended) and [Wathe: Extended](https://modrinth.com/mod/wathe-extended), which this addon was made for.

G'Express is an addon for that ecosystem, not a replacement for it.

---

## License

G'Express is licensed under the GNU General Public License v3.0 or later (`GPL-3.0-or-later`). See [LICENSE](LICENSE).
