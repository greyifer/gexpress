# Grey's Express 1.7.3

## Added

- Added editable three-line floating text blocks for map builders.
- Added room-name labels to Wathe doors after a named key is assigned to them.
- Added a two-point Red Ribbon placement tool for creating purchasable barriers between two clicked positions.
- Added an in-game skin editor for renaming imported gun and knife skins and adjusting every display position with a live preview.
- Added an Axiom-powered tutorial room editor with movable markers for player, weapon, and target placement.

## Changed

- Replaced the old Coin Barrier appearance with one continuous lead-style ceremonial Red Ribbon between two clicked endpoints while preserving its prices, labels, collision, and purchase behavior.
- Reworked the 3D model placement controls into clear move and rotate axes.
- Reworked the tag, level reward, and case editors to stay readable and scroll correctly at different GUI scales.
- Tutorial targets are now player-shaped living passengers instead of armor stands, and Escape opens a proper leave option.
- Importing or editing skins now refreshes only weapon models and textures without starting a global resource reload.

## Fixed

- Fixed KinsWathe's stamina bar starting from the wrong capacity for roles with additional stamina.
- Vengeful Spirit now revives at the death position when it is supported, or on the closest solid floor directly below it when it is in midair.
- Renaming an imported skin now updates its displayed name everywhere while preserving its command ID, unlocks, equipped state, and case rewards.
- Fixed imported 32x32 Blockbench textures using incorrect UV coordinates in Minecraft.
- Fixed overlapping controls in the Skin Model Editor and added the existing built-in gun and knife skins to its lists.
- Fixed skin imports and edits showing any loading screen; edits apply only after pressing Apply Now.

# Grey's Express 1.7.0

## Added

- Added an optional Lovers mode where each pair forms its own team and can only win with their own partner.
- Added an Import Skins tool to the Dev tab for Blockbench files that opens the skin folder, reloads models, and updates skin menus, cases, and commands.
- Added automatic map and play-area calculation with static terrain, moving terrain, full-map, and train modes.
- Added a tertiary ability keybind, defaulting to V, that lets Copycat cancel an active copied ability.
- Added an Always Muted preference for players who want the Muted modifier every round.

## Changed

- Cupid's win requirement is now based on the percentage of non-Cupid players linked as lovers.
- Copycat now keeps copied ability items visible until the ability expires or is cancelled, and receives items required by copied item-based abilities.
- Removed the extra Copycat cooldown that started after a copied ability ended.
- Changed dead players to enter dead spectator voice group one on each new death instead of reusing their previous group.
- Lovers now learn their partner immediately.
- Reduced the targeting range of Copycat, Godfather recruitment, Puppetmaster, Spy, Time Master Freeze, and Tracker to four blocks.
- Snitch now needs six completed tasks before revealing the killers.

## Fixed

- Fixed Blockbench skin imports rejecting valid gun filenames, generating invalid cube rotations and texture references, and collapsing multiple textures into one file.
- Fixed G'Express role abilities using a separate 30-second preparation lock instead of KinsWathe's configured Safe Preparation timer.
- Fixed a startup crash caused by preparing imported skin resources before Minecraft finished initializing its client options.
- Fixed recent bug reports not displaying correctly at 1x and 2x GUI scale, and restricted moderation actions to authorized staff and server operators.
- Fixed Vengeful Spirit failing to respawn after Copycat, Silent Shadow, Shadow March, or Psycho-related kills.
- Fixed Family Instinct highlighting non-Mafia players instead of only the local Mafia family.
- Fixed Dracula's Bat Form model disappearing and added reliable flight movement while transformed.
- Fixed Noelle's Roles Voodoo deaths being blocked by Silent, Guardian Angel, or Puppetmaster protection.
- Fixed Harlequin Masquerade names remaining disguised in the player list after Masquerade ended.
- Fixed Muted players being unable to buy notes when their role already provides a shop.
- Fixed Pelican swallowing a lover killing their partner or breaking the pair.
- Fixed Lovers wins being shared with unrelated lover pairs in independent-pair mode.
- Fixed player-target ability highlighting disagreeing with the server near the edge of targeting range.
- Fixed copied Seer abilities not being accepted by the server.
- Fixed lover soul-link deaths affecting players outside the actual connected pair.
- Fixed Altruist revives for Lovers, Muted players, Cupid, and Time Master state.
- Revived Cupid now has 30 seconds to create a new pair when no lovers remain.

# Grey's Express 1.6.7

## Added

- Added the Lovers modifier with soul-link deaths, lover HUD visibility config, and optional mixed-side pairings.
- Added Cupid as a neutral role that can link two players into a lover duo and win through a configurable number of alive lover duos.
- Added Vengeful Spirit as a civilian role that can respawn at their death spot and hunt their killer for a configurable revenge window.
- Added the Astronomikyu Plush block and item.
- Added a Seer compare ability that checks two nearby players and reports whether they are on the same team.

## Changed

- Updated ability HUD/keybind displays to follow the configured G'Express keybinds and show money costs as a single price.
- Changed instinct from hold-to-drain into a tap ability with a timed active window and recharge.
- Slowed Covenant blood drain and added bite cooldowns for vampire and Dracula roles.
- Changed Dracula's bat ability to spawn a bat entity for bat form.
- Let players inside a Pelican body open the guidebook with the configured guidebook key.

## Fixed

- Made plush sounds local to the player who clicks the plush.
- Fixed plush head-top texture orientation across plush models.
- Fixed spectator instinct before role reveal so player glows are green instead of role-colored, while keeping role colors after reveal.
- Removed guidebook wording that called out configurable values in role, modifier, and item descriptions.
- Removed unused source files, stray generated output, and orphaned Night Vision sync code.

# Grey's Express 1.5.9

## Changed
- Bumped the release version to 1.5.9.
- Replaced the Play of the Game dot-map replay/export path with real client-side gameplay footage capture that encodes the selected highlight directly to an MP4 file.
- Updated the Play of the Game tab to list and open saved MP4 videos from the local plays folder instead of rendering fake replay cards.
- Added FFmpeg auto-discovery for Winget installs so Play of the Game MP4 encoding works even before the launcher picks up a refreshed PATH.
- Added a bundled Java MP4 encoder fallback so Play of the Game exports still work when the player does not have FFmpeg installed.
- Added a bundled Flashback-style JavaCV/FFmpeg encoder path so Play of the Game MP4 export no longer depends on a player-installed FFmpeg executable.
- Added a per-selected-clip delete action to the Play of the Game tab, including cleanup for that clip's cached preview frames.
- Changed round-end Play of the Game to fade through an Overwatch-style title card, play the captured highlight frames, then fade back out before returning to the lobby.

## Fixed
- Fixed bundled Java Play of the Game exports writing MP4 files without a finalized movie atom, which made them appear unsupported or corrupted.
- Fixed the end-of-round Play of the Game flow opening the saved-video detail screen instead of the automatic highlight presentation.
- Fixed partial Play of the Game MP4 files being published after interrupted or failed encodes; videos are now written to temp files and validated before appearing in the plays folder.

# Grey's Express 1.5.8

## Added
- Added a Play of the Game system that captures round highlights, chooses the strongest kill window, plays it at round end, and saves it locally.
- Added a Play of the Game options tab where saved highlights can be rewatched or exported as MP4 files.

## Fixed
- Fixed safe-preparation ability blocking accidentally disabling broad killer features such as killer shop access, killer instinct, and money HUD state.
- Fixed starting shop balances so killer and custom killer-shop roles get their opening money from their assigned role instead of a temporary ability-gated feature check.
- Fixed killer-only and civilian-only modifier assignment checks to respect assigned custom killer roles directly.

# Grey's Express 1.5.7

## Added

- Added an autoclicker guard that kicks non-creative players when their click rate spikes far beyond normal play.

## Changed

- Blocked role ability use during safe preparation on both the client hotkey path and server payload checks.
- Disabled private message command aliases while a round is starting, active, or stopping.
- Let creative role testers use test roles without ability cooldowns or max-use limits.
- Disabled role and modifier testing commands while a game is running.

## Fixed

- Fixed Harlequin Masquerade not returning the Harlequin to the original activation position when shot or stabbed.
- Fixed Harlequin Masquerade not playing the Jevil laugh globally when broken by a shot or stab.
- Fixed Masquerade pulling sleeping players out of beds and leaving those beds unusable.
- Removed killer instinct from the Licensed Villain neutral role path.

# Grey's Express 1.5.6

## Added

- Added Acacia, Birch, Cherry, Dark Oak, Jungle, Mangrove, Oak, and Spruce Cabinet blocks using the new cabinet texture set.
- Added a dedicated Grey's Express creative tab with the Greyifer Plush as its icon.
- Added all G'Express special blocks and custom utility items to the new creative tab for easier map building and testing.
- Added cabinet blockstates, item models, loot tables, and axe-mining tags so the new cabinets place, render, drop, and break like normal wood blocks.
- Added persistent out-of-game G'Coin balances, admin give/remove/set commands, Players-tab editing, and a Skins-tab balance display.
- Added admin level reset commands for resetting a player's XP/level and clearing XP Roadmap claim history.
- Added earned level tags as a separate Tag Editor section, shown as a third player badge after normal tags.

## Changed

- Made Wathe couch sleeping toggleable per map preset, with creative players always allowed to use it for setup and testing.
- Reworked XP Roadmap reward setup into a structured editor with separate reward, description, and claim-command fields.
- Let XP Roadmap levels grant multiple visible rewards and run multiple claim commands from the new reward editor.
- Let Owner-tagged players access the Dev tab.
- Renamed the live Players-tab action from Tags to Edit since it now covers tags, XP, and G'Coin.
- Renamed the server role manager files/classes from Vulture/Trickster to Pelican/Harlequin while keeping existing network/config IDs compatible.
- Moved the player XP/level HUD to the bottom right and added the out-of-game G'Coin amount plus coin icon above it.
- Changed player level labels from `Lv` to `LvL` in shared player name displays and level editor UI.

## Fixed

- Fixed Snitch reveal HUDs exposing exact killer roles and killer-side Snitch warnings disappearing after the Snitch completes their tasks.
- Fixed Wathe couch sleeping facing the player away from the connected couch block.
- Fixed sneak-right-clicking Wathe couches with another couch item blocking normal couch placement.
- Fixed the G'Coin world component missing its Cardinal Components metadata declaration, which crashed clients during startup.
- Fixed Puppetmaster-controlled avatar deaths killing the Puppetmaster instead of the controlled target.
- Fixed Puppetmaster leaving behind a corpse decoy or invisible colliding controlled player, removed the max-use limit, and stopped injecting a temporary knife/inventory swap during control.

# Grey's Express 1.5.4

## Added

- Added /gexpress as a full alias for the /g command tree.
- Added a proper player-facing XP Roadmap menu with current progress, level cards, locked/unlocked states, and reward details.
- Added Dev-tab XP tuning for round XP, win XP, neutral win bonus XP, kill XP, civilian task XP, level curves, XP overrides, and level reward roadmap entries.
- Added Dev-tab grenade pass-through block settings for grenade line-of-sight exceptions.
- Added inline level and XP editing tools to the Players tab.
- Added ability HUD icons for newer role abilities.

## Changed

- Reorganized command groups into clearer buckets: game, roles, modifiers, setup, and admin.
- Reworked role and modifier distribution so configured role amounts behave as requested counts, side limits are respected, and modifiers spread more fairly across players.
- Reworked role/modifier tuning command paths and restored testing commands under the newer command structure.
- Reworked the tag editor and permissions UI to expose clearer, more complete permission controls.
- Reworked the 3D model placement screen so the preview model renders properly and left-click dragging rotates the preview.
- Rebalanced XP rewards: wins now grant +25 XP, neutral wins add another +25 XP, kills grant +10 XP, and civilian tasks grant +5 XP.

## Fixed

- Fixed private ability sounds playing globally for roles like Medic.
- Fixed a startup crash caused by the Harpy modifier null-guard mixin.
- Fixed modifier data sometimes returning null and crashing player rendering.
- Fixed Bodyguard revolvers dropping or being removed after gun kills.
- Fixed Muted notes and EOD Specialist pliers duplicating or being granted infinitely.
- Fixed Dracula bat transformation getting stuck or leaving the player unable to move properly.
- Fixed Skincrawler gunshot behavior: the first shot strips the disguise and hard-stuns them, while a shot in their own skin kills them.
- Fixed Takeover and Amnesia players missing sanity/tasks in custom game modes.
- Fixed C4 staying attached to spectators/dead players after the carrier dies.
- Improved Time Master rewind safety so damage is blocked during rewind playback and more round state is restored.

# Grey's Express 1.5.2

## Added

- Added a bundled `starting_map` preset using the default snow-map setup values, without a Fresh Air area.
- Added a Dev-tab model default export button that copies current C4, Spy Bug, and placement-preset tuning as source-ready Java defaults.
- Built-in tags now appear in the in-game Tag Editor and can have their color and hierarchy priority adjusted.

## Changed

- Owner now ranks above Dev in the default tag hierarchy.
- The Players tab no longer treats Passenger as a destructive tag assignment; Passenger remains the default fallback when no higher tag is active.

## Fixed

- Fixed setting/toggling Passenger clearing a player's other normal and custom tags.
- Fixed the Tag Editor only listing custom tags and hiding existing built-in tags.
- Fixed dev self-detection in the Players tab to also accept matching usernames, so the dev account is not locked out under mismatched UUID setups.

# Grey's Express 1.5.1

## Added

- Added the Creator tag, custom in-game tag creation, tag color/priority editing, and tag permission flags in the Dev tab.
- Added a client option for switching between 3D revolver skins and flat 2D gun-skin fallbacks.
- Added Bodyguard target glow, top-right protect tracking, and partner activity feed.
- Added a round-end player roster with heads, usernames, and round roles.

## Changed

- Reworked the Players tab tag picker so tags toggle on/off inline and the dev account can edit its own tags.
- Task and fake-task completion now reduces active ability cooldowns by 5 seconds.
- Scatter Brain now uses a broader collision/heightmap-based safe-spot search.

## Fixed

- Fixed Bodyguard issued revolvers dropping or being consumed by inventory cleanup after kills.
- Fixed Bodyguard civilian kills with the issued revolver draining sanity.
- Fixed Bodyguard target death handling: nearby Bodyguards die with their target, distant Bodyguards become Civilians.
- Fixed Lovers assignment so the Stupid Express Lovers modifier actually gets a pair.
- Fixed cooldown bars showing while dead or swallowed by Pelican.

# Grey's Express 1.5.0

## Added

- Added the Bodyguard civilian role with an assigned protect target and a temporary proximity revolver.
- Added a dedicated Skins tab for choosing unlocked Knife and Gun skins.
- Added `/g group skins give/remove/equip/list` commands for managing and equipping weapon skins.
- Added 3D revolver skins for Passenger, Cola, Gold, Jem, and Dev.

## Changed

- Moved the primary ability keybind into the G'Express keybind category.
- Tags can now stack, and player names display the two highest-priority tags.
- Tag editing is now dev-only.

## Fixed

- Dead round participants can no longer use G'Express abilities, and their modifiers are suspended until revival.
- Pelican can no longer affect dead players and keeps swallowed players inside across reconnects.
- Scatter Brain now searches nearby safe Y levels instead of failing on the player's exact height.
- Mafia members are restored to their original role when the Godfather dies.
- Spy bugs now report ability and kill interactions involving the bugged player.
- Spy, role ability, and passive-money targeting now consistently ignore dead players.
- Harlequin voice pitch now avoids unchanged pitch rolls and no longer snaps near-normal values back to normal.
- Fixed the Skins tab default preview inheriting the currently equipped skin.

# Grey's Express 1.4.8

## Added

- Added the Conversation task, which completes by standing near another living player on roughly the same Y level.
- Added task settings in the Game tab for Conversation enablement, chance, duration, radius, and vertical tolerance.
- Added `/g tasks test <outside|sleep|eat|drink|conversation> [players]` for forcing task testing.
- Added a Guidebook Tab keybind, defaulting to Tab, to switch between Roles and Modifiers.

## Changed

- Split test commands so roles, modifiers, and tasks now live under separate `/g roles`, `/g modifiers`, and `/g tasks` command groups.
- Updated the Players tab to show one Change tag button per player with an inline tag picker.

## Fixed

- Fixed Fresh Air sanity percentages so they add that percent of max sanity instead of targeting a sanity-bar percentage.
- Fixed Players tab scrolling overshooting and snapping back.
- Fixed guidebook arrow-key navigation wrapping from the last entry back to the first.

# Grey's Express 1.4.7

## Added

- Added `/g roles test task` commands for forcing or clearing Wathe mood tasks during testing.

## Fixed

- Fixed Harlequin's Dancing Carts using reset-template cart coordinates instead of the live pasted train copy.

# Grey's Express 1.4.6

## Changed

- Pelican swallows now add 30 seconds to the active round timer.
- Voice pitch effects now use playback-side OpenAL pitch per speaker instead of rewriting microphone PCM.

## Fixed

- Fixed Fresh Air areas placed on the reset-template train not counting after Wathe copies that train to the playable position.
- Fixed Fresh Air outside ambience state being resent every few ticks even when nothing changed.
- Fixed Trickster/Squeaker voice pitch causing glitchy microphone pops from packet-local resampling.

# Grey's Express 1.4.5

## Added

- Added an explicit Save button to the dev End Screen Layout editor.

## Changed

- The dev End Screen Layout editor now previews the round-end text layout directly instead of showing a grid panel and placeholder head squares.
- Dragging layout labels now preserves the grabbed mouse offset instead of snapping the label's anchor to the cursor.

## Fixed

- Fixed Mafia round-end player heads rendering far below the Mafia label because the custom offset was being applied inside Wathe's scaled head-render matrix.
- Fixed saved end-screen layout edits not being able to move the Mafia section above the automatic fallback position.

# Grey's Express 1.4.4

## Changed

- Mafia round-end results now place the Mafia section centered under the Wathe and neutral-mod result groups.
- Mafia end-screen text is brighter so it remains readable against normal round-end backgrounds.
- The dev End Screen Layout preview now resets old saved layouts to the corrected Wathe-style default anchors.

## Fixed

- Fixed Mafia player heads not being translated into the Mafia section on the round-end screen.
- Fixed the dev End Screen Layout preview still triggering Minecraft's screen blur through the inherited screen render path.

# Grey's Express 1.4.3

## Added

- Added a dev-only End Screen Layout preview that lets devs drag round-end role section anchors on a small grid.

## Changed

- Mafia round-end results now leave Wathe and Wathe Extended's normal renderer in control, only adding the Mafia section to the existing result layout.
- Map setup box commands now use Minecraft block-position arguments, so targeted block coordinate suggestions work like `setblock`.
- Fresh Air area setup now accepts two block-position corners directly on `freshair add`.

## Fixed

- Fixed Mafia end-screen role labels overlapping and pushing the results into a broken single-row layout.
- Fixed the dev End Screen Layout preview using Minecraft's blurred screen background.
- Fixed custom Fresh Air areas not reliably completing Wathe's Outside task by ticking Wathe's actual outside-task timer when the player is inside a configured Fresh Air area.

# Grey's Express 1.4.2

## Changed

- Config sync now uses a versioned network channel so future setting changes do not decode against an older packet layout.

## Fixed

- Fixed clients crashing on join when a server sent the older `gexpress:config_sync` packet layout.
- Fixed the Guardian Angel setting shifting the legacy config sync packet bytes before the bounty hunter settings.
- Fixed servers sending the new config sync packet to clients that have not advertised support for the new channel.

# Grey's Express 1.4.1

## Added

- Added the `takeover` game mode.
- Takeover spawns two opposing Godfathers, Lime side and Purple side.
- Takeover end screens now announce which Mafia side won.

## Changed

- Takeover assigns no neutral or killer roles through normal role assignment, leaving the round to the two rival Mafia sides and the remaining passengers.
- Mafia win handling now supports opposing Mafia families as separate sides instead of treating every Mafia player as one shared faction.

## Fixed

- Fixed Mafia black-and-white overlay behavior so it no longer depends on the short role-reveal HUD window.
- Fixed Mafia visual state recovering if the post-processing shader is cleared mid-round.
- Fixed Mafia family instinct and Mafia weather checks to stay tied to actual live round state.
- Fixed Puppetmaster and Warlock overlays ignoring hidden-HUD state.
- Fixed Puppetmaster local control cleanup leaving stale hotbar/body-pose state behind after control ends.

# Grey's Express 1.4.0

## Added

- Added a dead-player Guidebook keybind so spectators can open the guidebook while dead.
- Added keyboard navigation support for the guidebook so dead players can browse it without the mouse.
- Added Guardian Angel.
- Added a host setting for allowing Guardian Angel to secretly select killer, neutral, or Mafia players instead of only innocent-side players.
- Added dead-player voice group tracking and syncing for G'Express voice chat handling.
- Added client/server fresh-air ambience syncing for custom Fresh Air areas.
- Added voice-pitch support plumbing for G'Express voice effects.

## Changed

- Fresh Air custom areas now support both sanity reward percentages and whether Wathe outside ambience should play inside that area.
- Dead-player guidebook access and Guardian Angel shield usage now work from the normal G'Express client key handling flow.

## Fixed

- Fixed players being incorrectly placed into the dead voice group without actually dying.
- Fixed revived players staying stuck in the dead voice group after being brought back.
- Fixed custom Fresh Air areas not applying the fresh-air state the same way normal open-sky fresh air does.
- Fixed guidebook usage for dead spectators when no mouse interaction is available.

# Grey's Express 1.3.9.1

## Added

- Added a Mafia column to the round-end results screen.
- Fresh Air areas now apply a live `gexpress_fresh_air_area` command tag while players stand inside them.

## Changed

- Fresh Air area checks now treat configured corners as inclusive block areas, making small or single-block selections work reliably.
- Pelican death releases swallowed players back alive and resets their voice group.

## Fixed

- Fixed Mafia round-end data not syncing after it was recategorized.
- Fixed Puppetmaster controllers keeping inventory pickups made while controlling another player.
- Fixed Skincrawler disguises not carrying the stolen player's visible name and pronouns.
- Fixed role hover text still appearing while controlled or inside the Pelican.
- Fixed Juggernaut shield detection for knife and gun deaths and removed G'Express Lovers runtime logic.
- Suppressed the recursive unknown disconnect-packet error after disconnect encoding failures.

# Grey's Express 1.3.9

## Added

- Added a dedicated Mafia section in the Game tab.
- Added a dedicated Mafia section in the Wathe Extended guidebook.
- Added the newer G'Express roles to the Dev role-description override editor.

## Changed

- Vigilantes now use the Civilian passive-income bucket instead of a separate vigilante-only income setting.

## Fixed

- Fixed the Maps screen crash caused by Fresh Air Areas being added as a normal option instead of a list group.
- Civilian roles that do not use G'Express money HUD/shop handling no longer receive passive income by mistake.

# Grey's Express 1.3.8

## Added

- Added faction-specific passive income settings for Killers, Civilians, Neutrals, Vigilantes, and Mafia.

## Changed

- Passive income now runs through one G'Express-managed path instead of role managers granting it separately.
- Spy now shows its bug price on the standard ability bar as coins, matching the rest of the HUD.

## Fixed

- Spy bug cost is corrected back to 100 coins when an older saved config still has it at 200.
- Spy test-role usage now works outside a normal active survival round.
- Passenger tags now appear in the server tab list instead of falling back to plain usernames.

# Grey's Express 1.3.7

## Changed

- Spy now uses Wathe's normal StoreRenderer coin HUD instead of drawing a separate G'Express balance in the corner.
- Spy active bug timing now stays in the standard G'Express ability bar instead of adding a second custom timer line.
- Bounty Hunter and Mafia option text now consistently uses coins instead of mixing gold/coins wording.

## Removed

- Removed the old standalone Players screen now that player tags live directly inside the Players tab.

## Fixed

- Custom-shop and money-HUD checks are now separate, so Spy gets Wathe's coin display without being treated like a custom-shop role.

# Grey's Express 1.3.6

## Added

- Added the visible Spy Bug model on bugged players, with Dev-tab transform controls for sizing and placement.
- Added live Spy bug status syncing so the Spy HUD can show the active bug timer.

## Changed

- Removed the Builder and Designer tags from player tags, tag commands, and the Players tab.
- The map preset editor now exposes multiple Fresh Air areas directly, with each row including its sanity reward percentage.
- Spy bug planting now targets the player directly under your crosshair instead of nearby players in a loose cone.
- Spy can only keep one active bug at a time.

## Fixed

- Spy now shows their coin balance on screen with the coin icon.
- Spy ability bars no longer show the bug cost beside the cooldown bar; active bugs now display a timer instead.

# Grey's Express 1.3.5

## Added

- Added the Owner tag, colored `#196266`, with full built-in G'Express permissions.
- Added tag hierarchy support: Owner, Dev, Staff, Host, Trusted, then Passenger.
- Added Skincrawler, a killer role that steals fresh corpse skins and survives the first gunshot as a stun.
- Added Spy, a civilian role that spends coins to bug a player and receives task/interact feed updates.
- Added Squeaker, a modifier that raises the player's voice pitch.
- Added configurable Skincrawler, Spy, Squeaker, and Masquerade pitch settings to the game options tab.
- Fresh Air map presets now support multiple custom areas with per-area sanity reward percentages.

## Changed

- Harlequin Masquerade now warps living players' voice pitch instead of muting them.
- Fresh Air area derivation now copies all configured custom areas when extending map presets from neighboring maps.
- The Players tab and tab-list display now respect the new tag hierarchy and Owner tag.

## Fixed

- Fresh Air custom areas now apply their configured sanity reward when the Fresh Air task completes inside them.
- Time Master rewinds now snapshot and restore Skincrawler and Spy state.

# Grey's Express 1.3.4

## Added

- Added the Staff tag, colored `#79b9a9`.
- Added per-map Fresh Air areas for custom train sections that should count for Wathe's fresh-air task.

## Changed

- Players tag management now lives directly inside the Players tab instead of opening a separate screen.
- RTP slots added with `/g setup rtp add` now snap yaw to the nearest 90 degrees and persist back into the active map preset.

## Fixed

- Removing test modifiers now refreshes player dimensions, fixing Tiny-sized players staying tiny.
- Saving a train preset no longer wipes configured Dancing Carts regions when the live world snapshot has no cart list.
- Saving a train preset no longer rolls the active map's ready-area spawn/RTP slots back to stale saved values.

# Grey's Express 1.3.2

## Added

- Map presets now include a room key count for longer custom trains.
- `/g setup map edit <name> rooms <count>` sets the range of Wathe keys assigned as `Room 1` through `Room <count>`.

## Changed

- Removed G'Express keyed-door preset storage, commands, and map UI in favor of Wathe's normal door setup and a per-map key range.
- Map settings now import active Wathe areas, spawns, and RTP slots without touching door key names.

## Fixed

- Scatter Brain teleport now checks the player's real collision box, so valid train floors like carpets and slabs are no longer rejected.

# Grey's Express 1.3.1

## Added

- Juggernaut now has an animated stage HUD that shows the current stage and cumulative stage bonuses.
- Map presets can now save and reapply Wathe keyed-door data for longer custom trains.
- Map settings now include import buttons for active Wathe areas, spawns, RTP slots, and keyed doors.
- `/g setup map edit <name> keydoors ...` commands can snapshot, clear, set, and remove keyed door entries.

## Changed

- Mafia minimum player count is now shown with the global role assignment options instead of being hidden inside one role.
- Godfather, Mafioso, and Janitor settings are separated under their own role sections.
- Searching a role in the game options tab now keeps that role's child settings visible too.
- Night Vision now uses vanilla Night Vision with hidden particles and hidden effect icon instead of custom lightmap mixins.

## Fixed

- Night Vision should no longer flash from repeated effect add/remove timing.
- Mafioso and Janitor pricing/starting money settings no longer appear under Godfather.
- Map presets no longer need manual `lobbyArea`, `readyArea`, spawn, RTP, or keyed-door copying when importing active setup values.
- Opening the G'Express options screen no longer crashes on YACL builds where search state lives on the parent entry class.

# Grey's Express 1.3.0

## Added

- Pelican swallow requirement is now configurable from 10% to 100% of the round lobby.
- Mafia role assignment can now be gated by minimum lobby size, with separate starting gold for Godfather, Mafioso, and Janitor.
- Harlequin Masquerade and Dancing Carts cooldowns are now configurable in the game options tab.

## Changed

- Janitor now shops for Poison Vials instead of knives or guns.
- Janitor body cleanup is driven by the body the Janitor is looking at, with a private glow to make the target clear.
- Pelican progress now decreases when a swallowed player is spat back out.
- Night Vision now uses the synced G'Express lightmap without repeatedly removing vanilla Night Vision effects, preventing the add/remove flash.

## Fixed

- Harlequin no longer dies when shot or stabbed during active Masquerade; the hit breaks the Masquerade and plays the laugh at the hit location.
- Snitch task progress no longer resets while swallowed by the Pelican.
- Mafia black-and-white visuals stay active after being released from the Pelican.
- Config buttons that stage in-game commands now use the newer `/g setup` and `/g roles` command paths.

# Grey's Express 1.2.4

## Added

- Harlequin now plays the new laugh sound when Masquerade is broken by a knife or gun hit.
- Juggernaut now has a five-stage progression with configurable stage cooldown reduction and shield recharge.

## Changed

- Scatter Brain no longer teleports players onto `wathe:gold_ledge` support blocks.
- Bounty Hunter now waits until the failed-bounty penalty ends before assigning a new bounty.
- Puppetmaster control now keeps the controlling body hidden during position swaps.
- Night Vision now strips the vanilla night-vision status effect while using G'Express' synced modifier lighting to prevent flicker.
- Trusted Knife now uses the updated chicken leg texture.
- The Silent is protected from death while Shadow March is active.

## Fixed

- Puppetmaster control no longer routes damage/death from the abandoned body back into the Puppetmaster. If the controller body is killed during control, the controlled target dies instead.
- Harlequin Masquerade now clears skin swaps and voice mute immediately if the Harlequin is stabbed or shot during the active Masquerade.
- Time Master rewind snapshots now include Juggernaut shield recharge state.

# Grey's Express 1.2.3

## Added

- New Trusted tag with `/g trusted add/remove/list`, gold-yellow display text, and custom Trusted weapon models.
- Trusted players now use the Coca-Cola Revolver and Chicken Stick Knife textures.
- New killer role: Bounty Hunter. Bounty Hunters receive timed targets, earn configurable gold for killing the target, and receive configurable weapon cooldowns if the bounty timer expires.
- Game settings can now switch between fixed max Killer/Vigilante counts and scaled players-per-Killer/Vigilante role counts.
- Pelican now has a dedicated top-corner swallow progress counter.

## Changed

- Dev-tagged players continue to bypass supporter-gated skin checks so the dev tag can access all skins.
- Juggernaut and Pelican now use Wathe's fake-task mood, so neutral power roles receive fake task prompts like killers.
- Puppetmaster target range now applies even while role testing.
- Night Vision now fades in/out from the server-synced modifier state to avoid screen flashing.

## Fixed

- Trusted weapon ownership now stamps onto Knife/Revolver stacks like Host/Dev cosmetics.
- Time Master rewind snapshots now include Bounty Hunter bounty state.

# Grey's Express 1.2.2

## Added

- New `amnesia` game mode. It uses the modded role pool, but killers see other killers as civilian-like targets and can kill each other.
- New `_Iwy Plush` decorative plush block/item.
- Time Master Freeze now has a configurable per-round use count, defaulting to 3.

## Changed

- Puppetmaster's target menu now uses player heads with names underneath instead of full cards.
- Pressing the Puppetmaster ability key while the target menu is open now closes it.
- Time Master rewind now snapshots and restores Time Master freeze uses and Harlequin Dancing Carts uses if they happened inside the rewind window.
- Shop purchases no longer fail just because the bought item is currently on cooldown.

## Fixed

- Damaging the Puppetmaster's abandoned body no longer kills the controlled player. If self-body killing is disabled, the hit is ignored; if enabled, it kills or damages the Puppetmaster instead.

# Grey's Express 1.2.1

## Fixed

- Last Death Shield now applies only to the first player who died in the previous round, not every player who died that round.

# Grey's Express 1.2.0

## Added

- New killer role: Scatter Brain, with a configurable cooldown ability that scatters every living player, including themself, to safe random map positions.
- New civilian role: Tracker, with configurable range, cooldown, and maximum tracked targets. Tracked players glow for the Tracker.
- New civilian role: Altruist, who can sacrifice themself to revive a nearby dead body.
- New modifiers: Hungry, Thirsty, Muted, and Paranoid.
- Hungry and Thirsty add configurable food/drink carry limits from Wathe platters and trays.
- Muted blocks the player's microphone while still letting them hear others.
- Paranoid makes players always appear to be holding suspicious items.
- Host setting for giving the previous round's dead player a one-hit shield at the start of the next round.
- Puppetmaster now has a configurable target range.

## Changed

- G'Express role assignment now gives killer-side roles their Wathe starting income.
- G'Express assignment now supports a configurable max Vigilante count.
- Civilian-only G'Express modifiers now avoid killer and neutral power roles unless the modifier is Muted.
- Puppetmaster's target menu now shows live head cards with usernames, and updates as players enter or leave range.
- Puppetmaster control now applies the target-view state before teleporting, reducing the split-second skin flash.
- Harlequin skin swaps now also swap pronoun lookups while Masquerade is active.
- Harlequin Dancing Carts avoids self-swaps and avoids immediately repeating the same cart pair when another swap is available.
- Pelican belly voice now routes swallowed players and the Pelican into the belly channel.
- Time Master rewind now snapshots and restores Pelican belly state so swallowed players do not desync or softlock.
- Ability cooldown bars now keep rendering through round start/end fade overlays.

## Fixed

- Knife and bat deaths are blocked when both attacker and victim are killer-side players.
- Pelican swallowed players are no longer forcibly released during the round-end transition before the ready-area reset.
- Last-round shield blocks one knife or gun death, then breaks cleanly.

# Grey's Express 1.1.1

## Added

- Time Master now has Freeze, a secondary ability that locks the looked-at player in place for a configurable duration and range.
- Time Master Freeze keeps the target visually stuck in their current pose while blocking movement and actions.
- Time Master Freeze now has a configurable cooldown and its own cooldown bar.
- Snitch now has a configurable killer-warning threshold for how many tasks away from revealing they become visible to killers.
- Hosts can now configure a maximum Vigilante amount separately from the maximum killer amount.

## Changed

- Time Master rewind playback now uses smaller visual steps and interpolated movement so movement history appears smoother and denser.
- Time Master now shows held items, selected weapons, knife-ready poses, knife stabs, and revolver shots during rewind playback.
- Time Master rewind HUD now shows the number of rewinds remaining.
- Snitch mood-task text now shows only compact reveal progress like `1/3`.
- Snitch reveal progress now stays visible beside the mood icon even while Wathe is not showing an active task.
- Snitch reveal and killer warning info now render as stacked player-head HUD lines, similar to the Lovers partner readout.
- Revealed killers now glow for the Snitch, and warned Snitches glow for killers.
- Snitch reveal glows now use role colors for killers and Snitch yellow for Snitch warnings.
- Killer Snitch warnings now include the Snitch's name once the Snitch is within the configured task threshold.
- Juggernaut and Pelican now have Athletic/infinite stamina like other solo power roles.
- Dev role-description overrides now refresh live in the dev preview, G'Express game tab, and Wathe Extended guidebook.
- Harlequin Dancing Carts now has its own cooldown bar on the ability HUD.
- Juggernaut weapon cooldown now also appears on the ability HUD.
- Time Master visual playback now uses lightweight frame snapshots between full restore snapshots to reduce rewind tracking cost.
- The dev role-description editor now shows each role's current effective description and previews it in the hover details panel.
- Modifier amount controls are hidden unless a modifier has a real configurable max.
- Ability cooldown bars now stay visible while the round start/end fade is playing.
- Killers no longer get exact allied killer-role readouts at round start.
- Time Master cooldown state now avoids resyncing every tick once a player has already been initialized.
- Time Master rewind history now only records while a playable Time Master exists.
- Time Master weapon replay events now only record in rounds where a playable Time Master can use them.
- Snitch glow and killer-role lookup polling now do less work when nobody can use those effects.
- Ability HUD synced cooldown state now clears when the client changes worlds.
- Routine C4, Pliers, and Juggernaut loadout messages now log at debug level to reduce server log noise.
- Trickster has been renamed to Harlequin.
- Harlequin now has Dancing Carts, a secondary ability that shuffles the train's middle carts while leaving the front and back carts fixed.
- Dancing Carts carries players, dropped items, and other entities along with the cart they were inside.

## Fixed

- Rewound revolver shots and knife actions now restore the weapon in hand before replaying the sound and animation.
- Snitch reveal messages no longer show raw translation keys like `announcement.role.wathe.killer`.
- Snitch progress packets now resend reliably so the `(0/3)` style HUD progress is restored if the client enters the round after the first sync.
- G'Express role assignment now fills killer slots first, then neutral roles, then civilian roles, with leftover players remaining normal civilians.

# Grey's Express 1.1.0

## Added

- New civilian role: Time Master.
- Time Master rewind ability with configurable rewind time, cooldown, and max uses.
- Visible Time Master rewind effect with a light blue screen overlay and rewind playback.
- Dev-tab role description overrides for G'Express roles.
- C4 placement preset list in the dev tab.
- Surface-stuck C4 support for thrown C4.
- Pliers can now defuse C4 stuck to blocks.
- Pliers defuse now takes 3 seconds with visual/audio feedback.

## Changed

- C4 detonator now detonates one C4 at a time, starting with the most recently placed.
- C4 block placement now uses the first collided surface and keeps the back against the surface.
- Puppetmaster now takes over target pronouns and modifiers while controlling them.
- Players controlled by Puppetmaster can no longer move their camera/arm weirdly.
- Players inside the Pelican body can no longer use G'Express abilities.

## Fixed

- C4 not attaching correctly to players from right-click or thrown hits.
- Thrown C4 not attaching/sticking correctly to blocks.
- C4 floating with air gaps on ceiling/block surfaces.
- C4 side-sticking visually instead of back-sticking.
- Detonator saying there was no C4 when thrown C4 existed.
- Dev tab crash from list options.
- C4 detonator model issues from the earlier broken export path.

# Grey's Express 1.0.0

Welcome aboard the first release of **Grey's Express**, a Fabric 1.21.1 addon for **Wathe** and **Wathe Extended**, built for **The Harpy Express: Extended**.

This release adds a new set of roles, modifiers, items, host tools, voice chat rules, map presets, and train-focused chaos systems.

## Roles

### Killer Roles

**Bomb Specialist**  
A demolition killer who brings C4 into the match. C4 can be attached to players, appears on their back, and detonates after its fuse. Bomb Specialist also has Killer Instinct and Athletic.

**The Silent**  
A stealth killer built around Shadow March. The Silent enters a shadow form, moves quietly, then snaps back to where the ability started once it ends. Includes Killer Instinct and Athletic.

**Warlock**  
A killer who marks a player, then kills through that mark. The mark is placed with the ability key, and Hex Kill is triggered with Secondary Ability. After a successful Hex Kill, the mark disappears. Includes Killer Instinct and Athletic.

**Trickster**  
A chaos killer that scrambles identities. Masquerade shuffles living players' skins and mutes voice chat while active, making every witness a little less sure of themselves. Includes Killer Instinct and Athletic.

**Puppetmaster**  
A control-based killer who can take over another living player. The Puppetmaster leaves behind a vulnerable body while controlling, and the controlled player receives a red overlay. Includes Killer Instinct and Athletic.

### Civilian Roles

**Medic**  
A support role that can shield another player. The shield can block dangerous attacks, and the Medic receives visual feedback when their shield is hit or broken.

**Snitch**  
An investigator role that reveals the killers after enough tasks are completed. When the Snitch is close to finishing, killers are warned and can see them glowing.

**Seer**  
A civilian role with a death sense. When someone dies, the Seer gets a red flash across their screen.

### Neutral Roles

**Juggernaut**  
A solo neutral role that starts armed with a Knife and Revolver. The Juggernaut begins with a long weapon cooldown, but each kill reduces future cooldowns. Wins by being the last player alive.

**Pelican**  
A solo neutral role that swallows living players. Swallowed players see through the Pelican with a grey overlay and can only speak with the Pelican and other swallowed players. The Pelican can release players one by one, and all swallowed players are released if the Pelican dies. Wins by eating 80% of the lobby.

## Modifiers

**EOD Specialist**  
Starts with Pliers and can defuse C4 attached to players. Defusing has a wrong-wire chance, so shaky hands may make things worse.

**Short-sighted**  
Limits visibility of players, dropped items, and bodies past a certain range.

**Night Vision**  
Improves vision in dark areas without applying the vanilla Night Vision status effect. Softer than full vanilla brightness.

**Lovers**  
Links two players together. If one lover dies, the other dies too.

## Items

**C4**  
Attachable explosive charge that appears on a player's back, beeps, then detonates after its fuse.

**C4 Detonator**  
Used with C4 detonation gameplay and includes custom model support.

**Pliers**  
Used by EOD Specialists to defuse C4. Defuse attempts can fail through the wrong-wire chance.

**Grenade**  
Added as part of the shop and economy item set.

## Gameplay Systems

- Custom G'Express role assignment.
- Max killer amount support.
- Civilian fallback distribution after special roles are assigned.
- Shared Secondary Ability keybind.
- Ability cooldown bars near the hotbar.
- Multiple cooldown bars for roles with more than one active ability.
- Ability icons beside cooldown bars.
- Killer roles can identify other killer roles.
- Voice chat integration for role effects.
- Normal players can be prevented from creating voice chat groups.
- Operators can still create voice chat groups.
- Pelican belly voice isolation.
- Trickster voice mute during Masquerade.

## Host Tools And Settings

- G'Express Options screen.
- Role and modifier tuning.
- Role chances and role amounts.
- Modifier chances and modifier amounts.
- C4 price, fuse, first-beep delay, and wrong-wire settings.
- Medic shield behavior settings.
- Role settings for The Silent, Warlock, Juggernaut, Trickster, Puppetmaster, Pelican, and Snitch.
- Client-side ability bar size and position settings.
- Dev visual tuning for C4 back placement, Medic flashes, Short-sighted range, and Silent opacity.

## Map Tools

- `/g map` tools for creating, editing, listing, applying, and snapshotting map presets.
- Train preset support.
- RTP slot management.
- Host management commands.
- Map-specific weather and fog overrides.
- Sandstorm and snow visuals.
- Ready-area train preview support.
