# G'Express

**G'Express is a community-made addon for Wathe and Wathe: Extended, designed specifically for The Harpy Express: Extended server.**

This mod was created for our community Harpy Express server. The server is not run by doctor4t, and some features are built around our own custom server setup rather than general-purpose Wathe servers.

G'Express adds new roles, modifiers, items, map tools, train tools, host settings, and gameplay systems for custom murder mystery rounds aboard the Harpy Express.

Using this mod outside of **The Harpy Express: Extended** may work, but it is not the intended environment.

---

## Commands

All G'Express commands are documented in [COMMANDS.md](COMMANDS.md).

## Settings panel

Use `/give @s gexpress:settings_panel` to obtain the Settings Panel. Place matching, equally facing blocks in a rectangle to form a joined wall display, up to 16 by 16 blocks. A wider panel gives setting names more room.

Right-click the top arrows to switch between Game, Maps & Trains, Roles, and Modifiers; use the bottom arrows to page through settings. Each row has minus and plus controls. Only vanilla operators with permission level 2 or higher can interact, including changing pages; host or trusted tags alone do not grant access.

Game and role changes are saved as you click; role assignment changes apply to subsequent rounds. Role and modifier pages include enable switches, chance percentages, and maximum counts. Maps & Trains edits the selected saved preset's room count, weather, couch sleeping, terrain mode, play-area mode, and linked train preset. Use its **Apply map & train** plus control to activate the selected preset.

---

## Roles

In Draft mode, Godfather is the only Mafia role available at the start. Mafioso, Janitor, Pickpocket, and Burglar are excluded from the draft's visible choices, random picks, and fallback assignments; Godfather can still recruit them during the round.

| Name | Side | Description |
|---|---|---|
| **Bomb Specialist** | Killer | A killer role that can attach C4 to players. Can be defused by eligible players. |
| **The Silent** | Killer | Phases horizontally through blocks during Shadow March without footsteps or weapon use, then returns to a safe standing position near their origin. |
| **Warlock** | Killer | Marks a player, then uses that mark to kill another player standing close to them. |
| **Harlequin** | Killer | Masquerade swaps skins, mutes participants, and scatters them to preflighted safe map positions; Dancing Carts rearranges the train. |
| **Puppetmaster** | Killer | Takes control of another living player while leaving their own body vulnerable. |
| **Bounty Hunter** | Killer | Receives a timed bounty target. Killing the bounty pays bonus gold; missing the timer applies weapon cooldowns. |
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
| **Pelican** | Neutral | Can swallow living players. Eaten players see through the Pelican and share a belly voice channel. After two minutes, the Pelican can digest them from inventory slots, with a 30-second digestion cooldown. |
| **Doomsayer** | Neutral | Guesses living players' roles from the inventory and wins after reaching the configurable correct-prophecy goal. Wrong guesses either remove progress or kill the Doomsayer. |

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

### G'Express Menu

G'Express registers a workspace in G'Lib's shared animated menu. The workspace is split into Personal, Server Setup, and Developer sections, with permission-aware entries for gameplay, players, maps, train carts, and development tools.

Detailed editors open from those entries and retain the existing host and server permission checks.

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
- Tracker limits, Altruist range, Hungry/Thirsty carry limits, Squeaker pitch, Pelican swallow percentage, and first-death shield.

### Ability HUD

G'Express adds an ability HUD for role abilities.

- Cooldown bars appear near the hotbar.
- Roles with multiple active abilities can show multiple bars.
- Ability icons appear beside the bars.
- Time Master shows remaining rewinds beside the rewind bar.
- Skincrawler, Spy, Tracker, Altruist, Pelican, Harlequin, Juggernaut, Puppetmaster, Warlock, The Silent, Medic, and Time Master abilities are represented when their role needs a bar.
- Pelican swallow progress renders as a dedicated top-corner counter.

### Tags & Custom Skins

G'Express adds server-side tags for community presentation and custom cosmetics.

- Host and Trusted tags can be managed through `/g group host` and `/g group trusted`.
- Owner, Staff, Host, Creator, Trusted, and custom tags can be managed through `/g group tag`.
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

To build from source on Windows, install a Java 21 or newer JDK and run `.\gradlew.bat build` from the repo root. You do not need a separate Gradle install; the checked-in wrapper downloads the right Gradle distribution.

---

## Credits

Special thanks to [RAT / doctor4t](https://modrinth.com/user/RAT) for creating [The Last Voyage of the Harpy Express](https://modrinth.com/modpack/harpy-express), the original Harpy Express concept, **Wathe: Murder Mystery**, and the videos that inspired this project.

Special thanks to [Rezelyn](https://modrinth.com/user/Rezelyn) for [The Harpy Express: Extended](https://modrinth.com/modpack/the-harpy-express-extended) and [Wathe: Extended](https://modrinth.com/mod/wathe-extended), which this addon was made for.

G'Express is an addon for that ecosystem, not a replacement for it.

---

## License

G'Express is licensed under the GNU General Public License v3.0 or later (`GPL-3.0-or-later`). See [LICENSE](LICENSE).

Counterfeiter is a killer role with one forge per match. Forge choices are Master Key, Trapper Trap, Medical Kit, Pill and Pan, shown when their source mods are installed. The Master Key opens Wathe doors and breaks after three successful uses. The trap uses Trapper's normal collection and report behavior but gives exactly one truthful role for a captured player other than its owner; every other reported role is false. Medical Kits, Pills and Pans are inert props. Reconnecting does not reset the forge, and a full hotbar does not spend it. Revolver, Lockpick, Firecracker and Defense Vial are excluded.

### Loki

Loki is a killer with four abilities (default keys R, B, V and C). Approach and look at a living player to target them, as with Seer. Voices selects two distinct players and swaps where their Simple Voice Chat audio comes from for 120 seconds (45-second cooldown). Warp turns a target's view upside down, adds distortion through Veil and reverses controls for 60 seconds, with upward gravity for the first 30 seconds (30-second cooldown). Trickster's Fuse gives a nearby non-Loki player a 30-second fuse (60-second cooldown). Right-click another player to pass it without resetting its timer. You cannot pass directly back to your previous giver, or to Loki. When time runs out, Wathe's grenade explodes at the holder, using its normal nearby-player kills and protection hooks. Transfers trigger no pranks. Loki uses the shared cooldown bars and four custom 16-pixel HUD icons.

Fourth Ability instantly puts you in control of a clone for up to 180 seconds (90-second cooldown). Its inventory is empty; item use, pickup and buying are blocked. Your original body stays vulnerable and keeps your inventory. Press Fourth Ability again to return early. Killing the clone leaves a corpse with your appearance exactly where it died and returns you to the original body. Expiry or voluntary return does not leave a corpse. All cooldowns start immediately on use; changing the fourth key is supported in Controls.

Operators can use `/g replay off` to stop replay recording and playback, `/g replay on` to enable them again, and `/g replay status` to check the setting. The setting survives restarts. Turning replays off deletes all saved replays and partial recordings, and clears replay caches.
