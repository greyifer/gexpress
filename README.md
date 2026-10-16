# G'Express

**G'Express** is an addon for **Wathe** and **Wathe: Extended**, made for **The Harpy Express: Extended Modpack**.

It adds G'Express-specific roles, modifiers, items, blocks, commands, map presets, train presets, host tools, progression systems, and voice chat features for custom Harpy Express rounds.

G'Express is mainly built for our community server. It may work outside of **The Harpy Express: Extended**, but that is not the main target.

Community Discord:
[G'Network](https://discord.gg/3tw5HNKqBm)

---

## Requirements

G'Express is made for:

* **Minecraft 1.21.1**
* **Fabric**
* **Java 21**
* **Fabric API 0.109.0+**
* **Wathe 1.3.2+**
* **Wathe: Extended 3.4.132+**
* **Harpy Mod Loader 1.2.4-h1.3+**
* **GeckoLib 4.7.3+**
* **YetAnotherConfigLib 3.8.0+**
* **Cardinal Components**

The mod should be installed on both the **client** and the **server**.

**Simple Voice Chat** is optional, but recommended for the voice-related features.

---

## Features

* Extra G'Express roles and modifiers.
* C4, Pliers, Spy Bugs, Keycards, cases, bullets, and training weapons.
* Keycard Readers, food platters, drink trays, floating text, decorative blocks, map blocks, and plushies.
* Map presets for custom Harpy Express maps.
* Train presets for saving and restoring train setups.
* RTP slots for custom random spawn locations.
* Host and admin tools for managing rounds.
* Automatic game windows and lobby polls.
* G'Coins, XP, levels, player tags, cases, and weapon skins.
* G'Express ability HUD with cooldown bars and ability icons.

---

## Roles

### Killer

* **Bomb Specialist** - Plants C4 on players.
* **The Silent** - Uses Shadow March to move quietly before returning to the starting position.
* **Warlock** - Marks a player and kills through that mark.
* **Harlequin** - Scrambles appearances, voices, and train carts.
* **Puppetmaster** - Controls another living player.
* **Bounty Hunter** - Receives timed targets and earns coins for successful bounty kills.
* **Skincrawler** - Steals the appearance of a fresh corpse.
* **The Painter** - Alters information on bodies and players, and can create painted doorways.
* **Scatter Brain** - Teleports living players to random valid positions.

### Civilian

* **Medic** - Shields another player from one attack.
* **Snitch** - Completes tasks to reveal the killers.
* **Seer** - Senses deaths and can compare two players.
* **Time Master** - Rewinds the round and can freeze players.
* **Tracker** - Marks selected players.
* **Altruist** - Revives a player by sacrificing themself.
* **Spy** - Plants Spy Bugs and receives information from them.
* **Bodyguard** - Protects an assigned player.
* **Sniffer** - Detects poisoned food and drinks.
* **The Twins** - Has a second body and can swap places with it.
* **Vengeful Spirit** - Returns briefly after death to take revenge.

### Neutral

* **Juggernaut** - Starts armed and wins by being the last player alive.
* **Pelican** - Swallows living players.
* **Cupid** - Links players together as Lovers.
* **Copycat** - Copies another player's role ability for a limited time.

### Mafia

* **Godfather** - Recruits players into the Mafia.
* **Mafioso** - Recruited Mafia enforcer.
* **Janitor** - Cleans bodies and uses poison-related tools.
* **Pickpocket** - Steals coins from nearby players.
* **Burglar** - Uses tools such as Crowbars and Lockpicks.

### Covenant

* **Dracula** - Leads the Covenant and turns victims into Vampires.
* **Vampire** - Serves Dracula and must keep feeding.

---

## Modifiers

* **EOD Specialist** - Starts with Pliers and can defuse C4.
* **Short-sighted** - Hides players, dropped items, and bodies beyond a set distance.
* **Night Vision** - Improves visibility in dark areas.
* **Lovers** - Links two players together.
* **Muted** - Can hear voice chat but cannot speak.
* **Squeaker** - Raises the player's voice pitch.
* **Tall** - Makes the player taller.
* **Hungry** - Lets the player carry extra food from platters.
* **Thirsty** - Lets the player carry extra drinks from trays.
* **Paranoid** - Makes players appear as if they are holding weapons.

---

## Items

* C4
* C4 Detonator
* Pliers
* Spy Bug
* G'Express Case
* Bullet
* Basic Keycard
* Staff Keycard
* VIP Keycard
* Vault Keycard
* Training Knife
* Training Revolver
* Music Discs

---

## Blocks

### Interactive

* Keycard Reader
* Floating Text
* Red Ribbon
* Large Wood Door
* Golden Food Platter
* Golden Drink Tray

### Decorative / Map Building

* Sand Layer
* Red Sand Layer
* Fake Suspicious Sand
* Fake Suspicious Gravel
* Pebble Block
* Cabinets
* Moquette panels, slabs, and stairs
* Olive, Plum, and Ash wood variants
* Herringbone blocks
* Ledges
* Ornament blocks
* Plushies

---

## Commands

All commands are under `/g` and `/gexpress`.

### Game

| Command                | Purpose                                   |
| ---------------------- | ----------------------------------------- |
| `/g game start`        | Starts a round.                           |
| `/g game start dryrun` | Checks the round setup before starting.   |
| `/g game end`          | Ends the current round.                   |
| `/g game end force`    | Force-finalizes the current round.        |
| `/g game auto`         | Starts an automatic game window.          |
| `/g game auto end`     | Ends the active automatic game window.    |
| `/g poll`              | Lets players vote on lobby/game settings. |
| `/g open`              | Opens the server for joining.             |
| `/g close`             | Closes the server from joining.           |

### Setup

| Command              | Purpose                                                                                    |
| -------------------- | ------------------------------------------------------------------------------------------ |
| `/g setup checklist` | Checks whether the current setup is ready.                                                 |
| `/g setup map`       | Creates, edits, applies, validates, backs up, and restores map presets.                    |
| `/g setup train`     | Saves, lists, shows, validates, duplicates, renames, backs up, and restores train presets. |
| `/g setup rtp`       | Manages random teleport slots.                                                             |

### Roles, Modifiers & Testing

| Command               | Purpose                                                                                                            |
| --------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `/g roles tuning`     | Changes role chances and role amounts.                                                                             |
| `/g modifiers tuning` | Changes modifier chances and modifier amounts.                                                                     |
| `/g force`            | Forces, clears, or rerolls roles and modifiers.                                                                    |
| `/g test`             | Test tools for roles, modifiers, C4, overlays, tasks, cooldowns, Pelican states, Time Master effects, and presets. |
| `/g pelican leave`    | Lets a swallowed player leave the Pelican when allowed.                                                            |

### Skins & Progression

| Command   | Purpose                                                               |
| --------- | --------------------------------------------------------------------- |
| `/g skin` | Opens, buys, equips, lists, gives, or removes weapon skins and cases. |

### Admin

| Command                | Purpose                                                                                          |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| `/g admin host`        | Adds, removes, and lists hosts.                                                                  |
| `/g admin trusted`     | Adds, removes, and lists trusted players.                                                        |
| `/g admin voice`       | Manages voice mutes, dead voice, and voice status.                                               |
| `/g admin tag`         | Creates, edits, previews, assigns, removes, and resets player tags.                              |
| `/g admin gcoin`       | Gives, removes, or sets player coins.                                                            |
| `/g admin keycard`     | Gives keycards.                                                                                  |
| `/g admin skin`        | Admin tools for skins and cases.                                                                 |
| `/g admin config`      | Exports, imports, diffs, applies, and lists config presets.                                      |
| `/g admin diagnostics` | Shows diagnostics for setup, presets, round state, compatibility, localization, and performance. |
| `/g admin debugplayer` | Shows debug information for a player.                                                            |
| `/g admin dev`         | Developer and live-tuning tools.                                                                 |

---

## Map Presets

G'Express map presets are used to save and apply custom Harpy Express map setups.

Presets can store areas, spawn points, weather/fog settings, ready train previews, RTP positions, spectator spawns, static reset settings, and default train presets.

---

## Train Presets

Train presets save train reset templates and cart setups.

They are used by setup tools and cart-related gameplay such as Harlequin's Dancing Carts.

---

## Progression

G'Express adds server progression features for community play.

* **G'Coins** are used by several role and shop systems.
* **XP and levels** can be earned through rounds, wins, kills, tasks, and other actions.
* **Player tags** can be created and assigned by hosts.
* **Weapon skins** can be unlocked, equipped, and managed through cases and admin tools.

---

## Credits

Special thanks to [RAT / doctor4t](https://modrinth.com/user/RAT) for creating [The Last Voyage of the Harpy Express](https://modrinth.com/modpack/harpy-express), the original Harpy Express concept, **Wathe: Murder Mystery**, and the videos that inspired this project.

Special thanks to [Rezelyn](https://modrinth.com/user/Rezelyn) for [The Harpy Express: Extended](https://modrinth.com/modpack/the-harpy-express-extended) and [Wathe: Extended](https://modrinth.com/mod/wathe-extended), which this addon was made for.

G'Express is an addon for that ecosystem, not a replacement for it.
- [Watch the first Harpy Express video on YouTube](https://www.youtube.com/watch?v=jOjo3WRIMe8)
- [Watch the second Harpy Express video on YouTube](https://www.youtube.com/watch?v=0Mee_7gmsmQ)

---

## Building from source

Install a Java 21 or newer JDK and run `.\gradlew.bat build` from the repository root on Windows. The included Gradle wrapper downloads the required Gradle version.

For the full command reference, see [COMMANDS.md](COMMANDS.md). Release notes are in [CHANGELOG.md](CHANGELOG.md).

---

## License

G'Express is licensed under the GNU General Public License v3.0 or later (`GPL-3.0-or-later`). See [LICENSE](LICENSE).
