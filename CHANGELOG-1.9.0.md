# G'Express 1.9.0

## Added

- Added Loki, a new killer role with four abilities:
  - Voice Swap swaps two players' voice locations for 2 minutes. 45-second cooldown.
  - Warp activates after 15 seconds, then flips the player's view, gravity and movement controls for 30 seconds. 30-second cooldown.
  - Trickster's Fuse activates after 15 seconds, then starts its 30-second fuse. They can pass it by right-clicking someone else, but can't give it straight back to the previous player or pass it to Loki. Passing doesn't reset the timer. When it runs out, they explode using Wathe's grenade explosion. 1-minute cooldown.
  - Clone lets you control a copy of yourself for up to 3 minutes. You can open doors, but can't buy, carry or use items. If the clone dies, it leaves a body and you return to your original body. Your original body can still be killed. 90-second cooldown.
- Added a fourth ability keybind. Default: C.
- Added Loki's ability icons, cooldown bars and guidebook entries. His cooldowns start when you use each ability.
- Added Counterfeiter, a killer who can forge one item per match:
  - Master Key: works, but breaks after 3 successful door uses.
  - Trapper Trap: gives one real role for a caught player other than its owner. The rest of the report is fake.
  - Medical Kit and Pill: fake items with no functionality. The fake Pan can be raised on right-click, but cannot stun anyone.
- Counterfeiter's choices use the new inventory slot texture. Items only appear if their mod is installed. Revolver, Lockpick, Firecracker and Defense Vial aren't available.
- Added an OP-only settings wall panel for game settings, maps, trains, roles and modifiers.
- Added settings categories, bigger controls and hover highlights. Role cards show Enabled, Chance and Count together.
- Added server-stored replays with player POVs, voice chat, playback controls and kill markers.
- Added a grenade throw preview showing its path and landing point.
- Added Eth4n1 and Domekiqn plushes with Designer credits.
- Added Seafox__ and ReddishAcorn plushes with Builder credits.
- Added Carpet bot testing commands under /g test.

## Changed

- Removed Sovereign of Adumbration, Diaphanous and Nihility.
- Killers can kill other killers again. Mafia members can also kill their own family members.
- Only Godfather appears as a mafia choice at the start of Draft. Recruiting still works during the match.
- Grenades travel 20% further per tick and no longer have random throw spread.
- Replays are now OP-only.
- Improved replay performance with lots of players and when several people load a replay at once.
- Previously loaded replays load faster. The two most recently loaded matches are cached.
- Restored the Wathe Extended menu layout with Maps & Trains and Players tabs. Kept the Skins & Inventory shortcut in G'Lib.
- Updated EvieEveee's plush skin.
- Moved Counterfeiter's forge slots higher so they don't overlap Guesser.
- Any role can win as the last player alive, including Doomsayer.
- Vengeful Spirit stays alive after killing their killer during the revenge timer.
- Rebuilt the Twins' second body as a player entity. Its hearing distance is now 16 blocks.
- Removed the dark backgrounds and colored lines from several role displays.
- Cleaned up duplicate commands and shortcuts.
- Disabled /me during matches.

## Fixed

- Fixed Loki's sliced-up Warp view and made all Warp effects end together. Removed Fuse flame particles. Clone bodies now show the owner's level and tags. Counterfeiter keeps the forged option bright and darkens the unused choices.

- Smoothed replay player movement, head turns and body rotation. Packet batches no longer shorten movement interpolation, and buffering no longer snaps players to the next position.

- Fixed replay voices being heard from anywhere on the map.
- Moved replay voice decoding off the render thread and reduced delayed audio.
- Fixed replay selection sometimes being ignored after opening the menu.
- Fixed viewer slots being counted incorrectly when switching replays.
- Fixed flickering on the settings panel.
- Fixed Loki clones being unable to open doors while still being able to buy and use items.
- Fixed the server startup crash caused by Loki's clone interaction mixin.
- Fixed starting items duplicating when moved between slots.
- Fixed shielded Juggernauts getting weapon cooldowns after being shot.
- Cataloger now keeps their list after being revived.
- Fixed Vengeful Spirit dying when returning while crouched or getting stuck after an early revive.
- Dead Pelicans can no longer chat. Swallowed players can no longer use instinct from other addons.
- The Twins' second body now dies when its owner dies.
- Multiple Godfathers now have separate families.
- Fixed the end screen showing the wrong winner.

