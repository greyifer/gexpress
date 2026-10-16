# Testing with Carpet bots

Stand in your configured map's ready area, with no other players or bots in that world, then run a preset. You need admin permissions and Carpet's `/player` command enabled. If Carpet cannot spawn the test names, check its `allowSpawningOfflinePlayers` setting.

Setup creates `GE_Test1`, `GE_Test2` and `GE_Test3`, starts the existing test game mode and puts you in spectator. It tries to place the bots near each other on safe ground. If the map has no nearby space, they keep their normal match spawn positions.

| Command | GE_Test1 | GE_Test2 | GE_Test3 |
|---|---|---|---|
| `/g test setup juggernaut_shield` | Juggernaut with Medic shield | Medic | Killer |
| `/g test setup juggernaut_last_round_shield` | Juggernaut with last-round shield | Medic | Killer |
| `/g test setup vengeful_revenge` | Vengeful Spirit | Killer | Civilian |
| `/g test setup godfather_families` | Godfather | Another Godfather | Civilian |
| `/g test setup twins` | Twins | Killer | Civilian |
| `/g test setup pelican` | Pelican | Civilian | Killer |

## Commands

- `/g test role <players> <role>` assigns a role. Use the full role ID, such as `gexpress:vengeful_spirit`, to avoid addon name clashes. `@s` targets yourself.
- `/g test action <bot> shoot [target]` selects the bot's gun and fires at its aim, or aims at the supplied target first.
- `/g test action <bot> stab [target]` selects its knife and stabs the aimed player within 3 blocks.
- `/g test action <bot> ability [target]` runs the role's server ability. Currently supports Medic, Pelican, Twins, Juggernaut and Tracker. Addon abilities and abilities that need a client menu aren't supported by this command.
- `/g test action <bot> crouch`, `stand`, `stop` or `look <target>` controls the bot through Carpet.
- `/g test revive <player>` revives a dead player in place, removes their corpse and clears Vengeful's pending return state.
- `/g test state <player>` shows role, family, shields, swallowed state, gun/knife cooldown fractions, reported G'Express ability cooldowns and the Vengeful timer. Addon cooldowns aren't included.
- `/g test reset` ends the preset, disconnects its bots and restores your saved inventory, health, hunger, mood, shop state, role, modifiers, position and game mode. A manually started test match resets to the usual lobby instead.
- `/g test cooldown clear [players]` clears supported ability cooldowns. It doesn't clear weapon cooldowns; wait for those when testing normal weapon behavior.

Actions and revives only work during an active test match. They reject real players as bot actors. Role assignment still works in the lobby and test matches. Existing Carpet bots work with these commands too; you can start their match with `/g game start test generic`.

Shooting and stabbing use Wathe's actual server packet handlers. Carpet bots have no client to send those packets themselves. The commands check held weapons, cooldowns and a normal aim ray first, so walls and range still matter. Shields, death hooks, revenge and weapon cooldown changes run through the normal handlers. A requested ability can still be rejected by its usual role rules; inspect the resulting state.

## Quick checks

**Juggernaut:** run either shield preset, then `/g test action GE_Test3 shoot GE_Test1`. Inspect `/g test state GE_Test1` before and after: the shield should disappear without resetting the Juggernaut's cooldowns.

**Vengeful:** run the revenge preset, then `/g test action GE_Test2 stab GE_Test1`. If needed, use vanilla `/tp` to put them within knife range first. After the Spirit returns, make it kill `GE_Test2` and check that it survives past the revenge timer. To check an early revive, use `/g test revive GE_Test1` before the return timer ends, then kill it again once the attacker's knife is ready.

**Godfathers:** run the family preset and inspect both Godfathers with `/g test state`. Their family IDs should differ. Check shooting between them once the gun is ready.

**Twins:** run the Twins preset, use `/g test action GE_Test1 ability` to switch bodies, then have `GE_Test2` kill the owner. The other body should disappear too. With no target argument, a bot's gun shoots whatever player or Twin body it is looking at.

**Pelican:** run the Pelican preset and use `/g test action GE_Test1 ability GE_Test2` within swallow range. Test chat and instinct yourself by assigning your own role and leaving spectator with `/gamemode adventure`. Finish with `/g test reset`.

## Command cleanup

Use `/g test role <players> <role>` instead of the old role-first or `role set` forms, and `/g test reset` instead of `/g test clear`. Modifier add/remove commands now take players first. `/g test task set` was removed; use `/g test task <task> [players]`.

Use `/g admin ...` instead of `/g admin dev ...`, `/g admin diagnostics player` instead of `debugplayer`, preset `duplicate` instead of `copy`, and tuning `max` instead of `amount`. Nested `tuning role`/`tuning modifier` shortcuts and map `edit <name> rename` were removed. The remaining rename command is `/g setup map rename <oldName> <newName> confirm`.

`/g force` remains for live or queued role/modifier overrides; `/g test` is for test state. `/gexpress` redirects to the same `/g` tree.

These changes have build and command-tree checks. The scenarios still need an in-game run with your Carpet/modpack versions. Saved player state is held in memory for the test session, not as a permanent backup.
