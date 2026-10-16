# Role fixes in 1.8.13

Install `build/libs/Gexpress-1.8.13.jar` on both the server and clients. Winner announcements and Twin-body ownership use updated networking, so both sides need the same version.

Changes:

- Dead Pelicans lose their chat bypass; swallowed passengers retain belly chat. `/me` is blocked while a match is running or stopping.
- Swallowed players receive a final instinct and glow veto after the installed addons' overrides. The inspected pack contains KinsWathe, Noelle's Roles, Starry Express, Stupid Express, Wathe Extended, and HML.
- Juggernaut loadout removal and kill-credit cooldown changes run after confirmed deaths, leaving Medic- and previous-round-shield hits alone.
- Loadout checks include cursor-held items while moving between slots. This covers Juggernaut, Godfather, Bodyguard, Vengeful Spirit, Puppetmaster, draft weapons, and last-stand weapons.
- Cataloger retains the original dossier across death and revival.
- Vengeful Spirit revives with standing collision clearance, reset sneaking and fall distance. External revival clears the pending revenge and permits a later revenge attempt. Killing the designated killer completes vengeance and leaves the spirit alive.
- One remaining living participant wins regardless of role or personal objective. Swallowed players still count as living. Team titles describe the winning side rather than the viewer's role; solo winner names are sent by the server. Nonwinning neutral roles no longer inherit the winner's result.
- Twins hearing relay drops from 48 to 16 blocks. Owner death retires the other body. New bodies use native `ServerPlayerEntity` physics, inventory, tracking, equipment, and skins through an inert connection, without registering a logged-in user. Actors are excluded from match counts and end rosters. Late joiners receive player profiles before spawn packets. Legacy mob bodies are retired at round cleanup.
- Different Godfathers have different family roots, can fight opposing families, and cannot win together merely because everyone remaining is Mafia.
- Altruist, Bodyguard, Cupid, and Vengeful Spirit HUD text no longer has the plain dark panel and colored strips.
- Replay recording includes native Twin actors even though they are excluded from round-player counts.

## Validation

The Gradle build and 65 tests pass. Regression coverage includes cursor ownership during inventory moves and victory checks with multiple Godfather families. Minecraft registry bootstrapping requires Fabric's runtime transformations; cursor tests exercise the shared ownership calculation without trying to bootstrap an untransformed Minecraft runtime.

Live multiplayer validation remains necessary for the actor lifecycle and addon interactions. Check:

1. Shoot a shielded Juggernaut; weapons remain in their moved slots and cooldowns do not restart.
2. Move starting gear through the cursor while role ticks run; no second item appears.
3. Kill a crouching Vengeful Spirit, revive them externally during the pending timer, then kill them again. Separately complete revenge and verify they remain alive after the deadline.
4. Revive a Cataloger; compare all five names and clues with the original dossier.
5. Swallow addon roles with instinct and attempt both highlighting and ability packets. A dead Pelican must not broadcast normal chat. Try signed `/me` and `minecraft:me` as living and dead players.
6. Test different last-survivor roles, including Doomsayer below the guess goal, and verify the same winner appears to every client.
7. Run two Godfathers with recruits; cross-family combat works and the round continues while opposing families survive.
8. Spawn and swap a Twin, join late, kill the owner, change rounds, and review the replay. Verify skins, equipment, body removal, player counts, and the 16-block hearing boundary.
