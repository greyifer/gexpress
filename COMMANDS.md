# G'Express Command Reference

All G'Express commands are under `/g`. `/gexpress` is a full alias for the same command tree.
Use the command paths below. Redundant aliases have been removed; `/gexpress` still redirects to `/g`. See [Carpet testing](CARPET_TESTING.md) for bot scenarios and examples.

| Command | Purpose |
|---|---|
| `/g replay` | Opens the past-match replay library. |
| `/g replay status` | Shows the exact replay folder, recording/save status across game worlds, and file-writer queue. Available to admins and the server console. |
| `/g game start <mode> <effect> [minutes] [seconds]` | Starts a G'Express round. Modes include `modded`, `draft`, `amnesia`, `takeover`, `test`, `discovery`, `loose_ends`, and `murder`; effects include `generic`, `day`, `lobby`, `night`, and `sundown`. Draft mode gives each player 20 seconds to choose from three enabled roles or a random role. |
| `/g game start dryrun <mode> <effect> [minutes] [seconds]` | Previews a round start without starting the game. |
| `/g game end` | Ends the current round. |
| `/g game end force` | Force-finalizes the current round. |
| `/g setup checklist` | Checks map, train, RTP, and preset validation readiness before a round. |
| `/g setup map create <corner1> <corner2> <name>` | Creates a saved map preset from two corners. |
| `/g setup map delete <name>` | Deletes a saved map preset. |
| `/g setup map duplicate <source> <target>` | Copies a saved map preset to a new name. |
| `/g setup map edit <name> corners <corner1> <corner2>` | Updates a map preset's overall corners. |
| `/g setup map edit <name> weather <type>` | Sets the map preset weather override. |
| `/g setup map edit <name> fogcolor <hex>` | Sets or clears the map preset fog color. |
| `/g setup map edit <name> wholemap <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the full map bounds. |
| `/g setup map edit <name> playarea <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the playable area bounds. |
| `/g setup map edit <name> template <minX> <minY> <minZ> <maxX> <maxY> <maxZ>` | Edits the reset template bounds. |
| `/g setup map edit <name> freshair add <corner1> <corner2> [sanityPercent] [outsideSounds]` | Adds a Fresh Air task area with optional sanity reward and outside ambience. |
| `/g setup map edit <name> freshair list` | Lists saved Fresh Air task areas. |
| `/g setup map edit <name> freshair clear` | Clears saved Fresh Air task areas. |
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
| `/g setup map validate [name]` | Validates one map preset, or all map presets when no name is provided. |
| `/g setup map validate issues` | Validates all map presets and only lists presets with errors or warnings. |
| `/g setup map backup <name> list` | Lists backups created before map preset edits, renames, and deletes. |
| `/g setup map backup <name> restore <backupId> confirm` | Restores a map preset backup. |
| `/g setup map set <name> [trainPreset]` | Applies a map preset, optionally with a train preset. |
| `/g setup map default <name> <trainPreset>` | Sets the default train preset for a map preset. |
| `/g setup map snapshot <name>` | Snapshots active Wathe areas, spawns, and RTP slots into a preset. |
| `/g setup map rename <oldName> <newName> confirm` | Renames a map preset with an explicit confirmation. |
| `/g setup train preset save <name>` | Saves the current train reset template as a train preset. |
| `/g setup train preset delete <name>` | Deletes a train preset. |
| `/g setup train preset duplicate <source> <target>` | Copies a train preset to a new name. |
| `/g setup train preset rename <oldName> <newName> confirm` | Renames a train preset and updates map defaults that pointed to it. |
| `/g setup train preset validate [name]` | Validates one train preset, or all train presets when no name is provided. |
| `/g setup train preset validate issues` | Validates all train presets and only lists presets with errors or warnings. |
| `/g setup train preset backup <name> list` | Lists backups created before train preset edits, renames, and deletes. |
| `/g setup train preset backup <name> restore <backupId> confirm` | Restores a train preset backup. |
| `/g setup train preset list` | Lists train presets. |
| `/g setup train preset show <name>` | Shows train preset bounds, offset, RTP slots, and cart count. |
| `/g setup rtp add [x] [y] [z]` | Adds an RTP slot at your position or explicit coordinates. |
| `/g setup rtp remove <id>` | Removes an RTP slot by id. |
| `/g setup rtp removenearest` | Removes the nearest RTP slot. |
| `/g setup rtp list` | Lists RTP slots. |
| `/g setup rtp clear` | Clears all RTP slots. |
| `/g setup rtp enable` / `/g setup rtp disable` | Enables or disables RTP. |
| `/g test c4 attach <players>` | Admin-attaches C4 to players. |
| `/g test c4 remove <players>` | Removes attached C4 from players. |
| `/g test c4 clear` | Clears all attached C4. |
| `/g roles tuning <id> chance <value>` | Sets a role assignment chance. |
| `/g roles tuning <id> max <value>` | Sets a role assignment maximum. |
| `/g test setup <scenario>` | Spawns three Carpet bots and starts a test match. Scenarios: `juggernaut_shield`, `juggernaut_last_round_shield`, `vengeful_revenge`, `godfather_families`, `twins`, `pelican`. |
| `/g test action <bot> <action> [target]` | Makes a Carpet bot shoot, stab, crouch, stand, stop, use an ability or look at a target. |
| `/g test revive <player>` | Revives a dead player in a test match without starting another round. |
| `/g test state <player>` | Shows role, family, shields, weapon/ability cooldowns and Vengeful timer. |
| `/g test reset` | Ends a preset test, removes its bots and restores your saved player state. A manually started test match resets to the lobby. Outside a match, clears test state in the current world. |
| `/g test role <players> <role>` | Assigns a test role. |
| `/g test role clear [players]` | Clears test roles. |
| `/g modifiers tuning <id> chance <value>` | Sets a modifier assignment chance. |
| `/g modifiers tuning <id> max <value>` | Sets a modifier assignment maximum. |
| `/g test modifier add <players> <modifier>` | Adds a test modifier. Suggestions hide modifiers the selected player already has. |
| `/g test modifier remove <players> <modifier>` | Removes a test modifier. Suggestions show only modifiers the selected player has. |
| `/g test modifier clear [players]` | Clears test modifiers. |
| `/g test preset save <name> [player]` | Saves a runtime QA preset from a player's current role and modifiers. |
| `/g test preset apply <name> [players]` | Applies a saved runtime QA preset to selected players. |
| `/g test preset list` / `/g test preset delete <name>` / `/g test preset clear` | Lists or manages runtime QA presets. |
| `/g test task <outside|sleep|eat|drink|conversation> [players]` | Forces a task for testing. |
| `/g test task clear [players]` | Clears current tasks. |
| `/g roles pelican leave` | Lets a swallowed player climb out of the Pelican. |
| `/g test pelican release [players]` | Releases selected players from Pelicans, or yourself if no players are given. |
| `/g admin host add <players>` | Adds host permission to players. |
| `/g admin host remove <players>` | Removes host permission from players. |
| `/g admin host list` | Lists saved hosts. |
| `/g admin trusted add <players>` | Adds the Trusted tag to players. |
| `/g admin trusted remove <players>` | Removes the Trusted tag from players. |
| `/g admin trusted list` | Lists saved Trusted players. |
| `/g admin voice mute <players>` / `/g admin voice mute all` | Voice-mutes players or all non-privileged players. |
| `/g admin voice unmute <players>` / `/g admin voice unmute all` | Removes voice mutes. |
| `/g admin voice list` | Lists voice-muted players. |
| `/g admin voice status [players]` | Shows voice mute, dead voice group, and voice connection status. |
| `/g force role reroll [players]` | Rerolls live or queued roles for selected players. |
| `/g force role <role> [players]` | Sets a live role or queues it for the next match. |
| `/g force role clear [players]` | Clears a forced role. |
| `/g force modifier add <players> <modifier>` | Adds a live modifier or queues it for the next match. |
| `/g force modifier remove <players> <modifier>` | Removes a live or queued modifier. |
| `/g force modifier clear [players]` | Clears forced modifiers. |
| `/g force modifier reroll [players]` | Rerolls live or queued modifiers for selected players. |
| `/g admin leaderboard get <players>` | Shows every persisted leaderboard statistic for selected online or cached players. |
| `/g admin leaderboard set <players> <stat> <value>` | Sets a leaderboard statistic. Longest-alive values use seconds. |
| `/g admin leaderboard add <players> <stat> <value>` | Adds to a leaderboard statistic. |
| `/g admin leaderboard remove <players> <stat> <value>` | Removes from a leaderboard statistic without going below zero. |
| `/g admin leaderboard reset <players>` | Resets all leaderboard statistics for selected players. |
| `/g admin c4back offset x/y/z <value>` | Tunes the C4 back model offset. |
| `/g admin c4back rotation x/y/z <value>` | Tunes the C4 back model rotation. |
| `/g admin c4back slant <value>` | Tunes the C4 back model diagonal slant. |
| `/g admin c4back scale <value>` | Tunes the C4 back model size. |
| `/g admin c4preset add [values]` | Adds a C4 placement preset from current settings or explicit values. |
| `/g admin c4preset list` | Lists C4 placement presets. |
| `/g admin c4preset remove <index>` | Removes a C4 placement preset. |
| `/g admin c4preset clear` | Clears C4 placement presets. |
| `/g admin roledesc <role> set <description>` | Overrides a G'Express role guidebook description. |
| `/g admin roledesc <role> clear` | Clears a role description override. |
| `/g admin shortsighted range <value>` | Tunes Short-sighted visibility range. |
| `/g admin medicshield blockFlashTicks <value>` | Tunes Medic shield block flash duration. |
| `/g admin medicshield breakFlashTicks <value>` | Tunes Medic shield break flash duration. |
| `/g admin medicshield blockFlashAlpha <value>` | Tunes Medic shield block flash opacity. |
| `/g admin medicshield breakFlashAlpha <value>` | Tunes Medic shield break flash opacity. |
| `/g admin silentshadow alpha <value>` | Tunes The Silent shadow opacity. |
| `/g admin traincart <preset> <corner1> <corner2>` | Adds a Dancing Carts region to a train preset. |
| `/g admin traincart <preset> list` | Lists Dancing Carts regions for a train preset. |
| `/g admin traincart <preset> remove <index>` | Removes a Dancing Carts region from a train preset. |
| `/g admin traincart <preset> clear` | Clears Dancing Carts regions from a train preset. |
| `/g admin diagnostics config` | Shows a focused config summary for hosts and staff. |
| `/g admin diagnostics compat` | Shows which optional compatibility integrations are loaded or using fallbacks. |
| `/g admin diagnostics presets` | Shows map/train preset counts, validation issue counts, and backup counts. |
| `/g admin diagnostics round` | Shows round status, role assignment count, tracked participants, and living/dead counts. |
| `/g admin diagnostics player <players>` | Inspects selected players, including role, modifiers, round state, economy, tags, position, and voice status. |
| `/g admin diagnostics localization` | Checks expected G'Express role/modifier localization keys. |
| `/g admin tag preview <players>` | Shows effective tags and permissions for selected players. |
| `/g admin config export <name>` | Exports the current G'Express config to a named JSON snapshot. |
| `/g admin config import <name>` | Imports a named G'Express config snapshot and syncs it to clients. |
| `/g admin config import <name> dryrun` | Checks that a config snapshot can be read and migrated without applying it. |
| `/g admin config diff <name>` | Compares a named config snapshot against the current config. |
| `/g admin config list` | Lists saved G'Express config snapshots. |
