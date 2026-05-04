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
| `/g start <mode> <effect> [minutes] [seconds]` | Starts a G'Express round. Modes include `modded`, `amnesia`, `discovery`, `loose_ends`, and `murder`; effects include `generic`, `day`, `lobby`, `night`, and `sundown`. |
| `/g end` | Ends the current round. |
| `/g end force` | Force-finalizes the current round. |
| `/g map create <corner1> <corner2> <name>` | Creates a saved map preset from two corners. |
| `/g map delete <name>` | Deletes a saved map preset. |
| `/g map edit <name> corners <corner1> <corner2>` | Updates a map preset's overall corners. |
| `/g map edit <name> rename <newName>` | Renames a map preset. |
| `/g map edit <name> weather <type>` | Sets the map preset weather override. |
| `/g map edit <name> fogcolor <hex>` | Sets or clears the map preset fog color. |
| `/g map edit <name> wholemap <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the full map bounds. |
| `/g map edit <name> playarea <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the playable area bounds. |
| `/g map edit <name> template <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the reset template bounds. |
| `/g map edit <name> offset <x> <y> <z>` | Sets the play area paste offset. |
| `/g map edit <name> spectator <x> <y> <z> <yaw> <pitch>` | Sets the spectator spawn position. |
| `/g map edit <name> readytrain here` | Sets the ready train preview position to your current position. |
| `/g map edit <name> readytrain <corner>` | Sets the ready train preview corner. |
| `/g map edit <name> readytrain clear` | Clears the ready train preview position. |
| `/g map edit <name> randomspawns snapshot` | Copies current RTP slots into the map preset as random spawns. |
| `/g map edit <name> randomspawns clear` | Clears saved random spawns from the map preset. |
| `/g map list` | Lists saved map presets. |
| `/g map show <name>` | Shows a map preset's saved values. |
| `/g map set <name> [trainPreset]` | Applies a map preset, optionally with a train preset. |
| `/g map default <name> <trainPreset>` | Sets the default train preset for a map preset. |
| `/g map snapshot <name>` | Snapshots active map values into a preset. |
| `/g train preset save <name>` | Saves the current train reset template as a train preset. |
| `/g train preset delete <name>` | Deletes a train preset. |
| `/g train preset list` | Lists train presets. |
| `/g train preset show <name>` | Shows train preset bounds, offset, RTP slots, and cart count. |
| `/g rtp add [x] [y] [z]` | Adds an RTP slot at your position or explicit coordinates. |
| `/g rtp remove <id>` | Removes an RTP slot by id. |
| `/g rtp removenearest` | Removes the nearest RTP slot. |
| `/g rtp list` | Lists RTP slots. |
| `/g rtp clear` | Clears all RTP slots. |
| `/g rtp enable` / `/g rtp disable` | Enables or disables RTP. |
| `/g host add <players>` | Adds host permission to players. |
| `/g host remove <players>` | Removes host permission from players. |
| `/g host list` | Lists saved hosts. |
| `/g trusted add <players>` | Adds the Trusted tag to players. |
| `/g trusted remove <players>` | Removes the Trusted tag from players. |
| `/g trusted list` | Lists saved Trusted players. |
| `/g voice mute <players>` / `/g voice mute all` | Voice-mutes players or all non-privileged players. |
| `/g voice unmute <players>` / `/g voice unmute all` | Removes voice mutes. |
| `/g voice list` | Lists voice-muted players. |
| `/g c4 attach <players>` | Admin-attaches C4 to players. |
| `/g c4 remove <players>` | Removes attached C4 from players. |
| `/g c4 clear` | Clears all attached C4. |
| `/g tuning role <id> chance <value>` | Sets a role assignment chance. |
| `/g tuning role <id> max <value>` | Sets a role assignment maximum. |
| `/g tuning role <id> amount <value>` | Alias for role max. |
| `/g tuning modifier <id> chance <value>` | Sets a modifier assignment chance. |
| `/g tuning modifier <id> max <value>` | Sets a modifier assignment maximum. |
| `/g tuning modifier <id> amount <value>` | Alias for modifier max. |
| `/g test role <role> [players]` | Assigns a test role. |
| `/g test role clear [players]` | Clears test roles. |
| `/g test modifier add <modifier> [players]` | Adds a test modifier. |
| `/g test modifier remove <modifier> [players]` | Removes a test modifier. |
| `/g test modifier clear [players]` | Clears test modifiers. |
| `/g dev c4back offset x/y/z <value>` | Tunes the C4 back model offset. |
| `/g dev c4back rotation x/y/z <value>` | Tunes the C4 back model rotation. |
| `/g dev c4back slant <value>` | Tunes the C4 back model diagonal slant. |
| `/g dev c4back scale <value>` | Tunes the C4 back model size. |
| `/g dev c4preset add [values]` | Adds a C4 placement preset from current settings or explicit values. |
| `/g dev c4preset list` | Lists C4 placement presets. |
| `/g dev c4preset remove <index>` | Removes a C4 placement preset. |
| `/g dev c4preset clear` | Clears C4 placement presets. |
| `/g dev roledesc <role> set <description>` | Overrides a G'Express role guidebook description. |
| `/g dev roledesc <role> clear` | Clears a role description override. |
| `/g dev shortsighted range <value>` | Tunes Short-sighted visibility range. |
| `/g dev medicshield blockFlashTicks <value>` | Tunes Medic shield block flash duration. |
| `/g dev medicshield breakFlashTicks <value>` | Tunes Medic shield break flash duration. |
| `/g dev medicshield blockFlashAlpha <value>` | Tunes Medic shield block flash opacity. |
| `/g dev medicshield breakFlashAlpha <value>` | Tunes Medic shield break flash opacity. |
| `/g dev silentshadow alpha <value>` | Tunes The Silent shadow opacity. |
| `/g dev traincart <preset> <corner1> <corner2>` | Adds a Dancing Carts region to a train preset. |
| `/g dev traincart <preset> list` | Lists Dancing Carts regions for a train preset. |
| `/g dev traincart <preset> remove <index>` | Removes a Dancing Carts region from a train preset. |
| `/g dev traincart <preset> clear` | Clears Dancing Carts regions from a train preset. |

---

## Roles

| Name | Side | Description |
|---|---|---|
| **Bomb Specialist** | Killer | A killer role that can attach C4 to players. Can be defused by eligible players. |
| **The Silent** | Killer | Can move without footsteps during Shadow March, then returns to their original position when the ability ends. |
| **Warlock** | Killer | Marks a player, then uses that mark to kill another player standing close to them. |
| **Harlequin** | Killer | Can swap player skins or train carts to create confusion during rounds. |
| **Puppetmaster** | Killer | Takes control of another living player while leaving their own body vulnerable. |
| **Bounty Hunter** | Killer | Receives a timed bounty target. Killing the bounty pays configurable gold; missing the timer applies configurable weapon cooldowns. |
| **Scatter Brain** | Killer | Scatters every living player, including themself, to safe random positions around the active map. |
| **Medic** | Civilian | Shields another player from danger and receives visual feedback when that shield is hit or broken. |
| **Snitch** | Civilian | Completes tasks to expose the killers. Progress stays visible beside the mood HUD, and killers are warned/glow the Snitch once they are within the configured task threshold. |
| **Seer** | Civilian | Receives a visual warning whenever someone dies. |
| **Time Master** | Civilian | Can rewind the round, track remaining rewinds, and freeze a looked-at player in place with configurable duration, cooldown, range, and uses. |
| **Tracker** | Civilian | Can track a configurable number of players at once, making those players glow for the Tracker. |
| **Altruist** | Civilian | Sacrifices themself to revive a dead body. |
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
- Snitch reveal-task count and killer-warning task threshold.
- Puppetmaster range and self-body kill behavior.
- Time Master Freeze duration, cooldown, range, and per-round uses.
- Scatter Brain cooldown, Tracker limits, Altruist range, Hungry/Thirsty carry limits, and last-death shield.

### Ability HUD

G'Express adds an ability HUD for role abilities.

- Cooldown bars appear near the hotbar.
- Roles with multiple active abilities can show multiple bars.
- Ability icons appear beside the bars.
- Time Master shows remaining rewinds beside the rewind bar.
- Scatter Brain, Tracker, Altruist, Pelican, Harlequin, Juggernaut, Puppetmaster, Warlock, The Silent, Medic, and Time Master abilities are represented when their role needs a bar.
- Pelican swallow progress renders as a dedicated top-corner counter.

### Tags & Custom Skins

G'Express adds server-side tags for community presentation and custom cosmetics.

- Host and Trusted tags can be managed through `/g host` and `/g trusted`.
- The Trusted tag is gold-yellow and gives Trusted players custom Coca-Cola Revolver and Chicken Stick Knife models.
- Dev-tagged players can access supporter-gated skin options.

### Amnesia Mode

Amnesia is a G'Express game mode based on modded murder.

- Killer roles are still assigned from the modded role pool.
- Killers do not see other killers as killer teammates through killer identity features.
- Other killers appear as civilian-like targets to killer instinct.
- Killers can kill other killers in this mode.

### Map Tools

G'Express includes map tools for creating and managing custom Harpy Express maps.

- `/g map` tools for creating, editing, listing, applying, and snapshotting map presets.
- Map-specific weather and fog overrides.
- Sandstorm and snow visual support.
- Ready-area train preview support.

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
