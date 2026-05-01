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
