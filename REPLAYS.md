# Match replays

Completed matches are recorded automatically on the server. Open **G'Express → Past Matches**, press **K**, or run **/g replay**. Watching is available in the lobby and after death. Each viewer has their own playback clock and camera, with up to four simultaneous viewers.

Default controls are configurable in Minecraft's keybindings:

| Key | Action |
| --- | --- |
| K | Open past matches |
| P | Pause or resume |
| Left / Right | Back or forward five seconds |
| − / = | Change speed: 0.25×, 0.5×, 1×, 2×, 4×, 8× |
| Tab | Cycle through recorded player POVs, including dead spectators |
| F | Free spectator camera |
| R | Open clickable timeline and playback buttons |

The compact timeline and transport buttons appear at the bottom center. Mouse controls open automatically when playback starts. Play/pause, five-second jumps, speed, POV, free camera and exit are available on the panel. Red marks show deaths, with the killer and victim available on hover. Drag or click the timeline with the controls open to seek. Escape closes controls; **Exit replay** returns to the original location and game mode.

## Recording and storage

Archives live in `<server save root>/gexpress-replays/<match-id>/` on the server. This is the root Minecraft save folder, which may contain several worlds on a WorldManager server. The folder is created at server startup, and its absolute path is printed in the server log. Run **`/g replay status`** in game, or **`g replay status`** in the console, to see the exact path and recording/save status for each game world.

- `scene.gsave`: the initial map blocks and block-entity data, including loaded Wathe train scenery tiles.
- `frames.compact.grpl`: automatically converted compact data for older recordings; the original stays untouched. Conversion is done once on the replay reader thread.
- `frames.grpl`: individually compressed tick records with checkpoints every five seconds; playback reads frames on demand with a cache capped at 128 frames or 64 MB (one larger frame may be retained).
- `frames.playback.grpl`: a separate stream containing only scenery changes for forward playback, so the full five-second checkpoints are decoded only when seeking. New recordings write this alongside the archive; existing recordings generate it once during loading. `frames.playback.source` ties that generated cache to the original file.
- `metadata.nbt`: format version, timestamp, duration, player roster, source dimension, origin, and death markers.

The recorder captures server-side entity NBT, equipment, positions, view directions, poses, animations, player skin properties, spectator movement, block changes, block-entity updates, weather, server-broadcast sounds and particles, and Simple Voice Chat microphone packets from the recorded roster. Players receive a recording notice at match start. Voice remains audible at normal speed; it is muted at other speeds, and codecs reset when seeking or pausing. The voice mix includes all recorded speakers rather than reconstructing each listener's proximity or group routing.

Files stay on the server. Clients receive playback snapshots and audio for the selected position. Incomplete recordings retain `.partial` files and never appear in the library. Capture failures leave a `recording-error.txt` in the match directory, and the root folder contains per-world recording status files. No automatic retention policy is applied.

Initial scenery is captured sparsely from loaded chunk sections, skipping air. For Wathe train matches, the capture also includes terrain below and beside the play area. Train speed, scenery time, snow, fog and map effect are recorded every tick. Pausing and seeking use that recorded state. Older archives remain readable, but scenery state missing from them requires a new recording. Sections loaded later during the match are added to the recording. This avoids generating unused terrain or allocating memory based on the volume of empty space. Areas whose chunks never load during the match have no recorded scenery. Recording also watches active match state as a fallback if an addon skips the normal initialization callback, and saves before the normal lobby reset.

## Current limits and verification

Playback uses the normal Wathe/Sodium world renderer and G'Lib's `.gsave` archive format. Chunks and recorded entities share the renderer's original framebuffer; replay playback does not replace the main render target. Recorded train state and map effects are protected from the staging world's live component updates. Scenery time is sampled once per rendered frame, and recorded weather and daylight are applied before the lightmap update. Scenery is staged in the separate **`gexpress:replay`** void dimension, so it no longer needs to fit G'Lib's small interactive-room volume limit. Each viewer receives a separate staging area. Scenery loads in batches, and selecting a POV moves the spectator's chunk-loading position to that player. Replay block entities do not simulate on the server. Block checkpoints use a shared state palette and primitive position/state arrays rather than repeating NBT for every block. Archive decoding and state reconstruction run on the replay reader thread. Playback keeps up to 60 decoded frames ahead and consumes them in order. If decoding falls behind, the timeline holds its position and displays Buffering rather than skipping forward. Walking animation is reconstructed from recorded horizontal travel, including for older recordings. Unchanged persistent entity data is reused instead of deserializing the full entity every tick. New recordings include a small block delta alongside each full checkpoint so forward playback can skip rescanning unchanged checkpoint scenery. Slow frame preparation and application are logged with the replay tick; new recordings also store the original capture interval. Seeking replaces the old buffer; pause and speed changes retain valid decoded frames. Forward playback applies recorded changes incrementally instead of reconstructing the full scenery history every tick. Scenery updates use native chunk packets instead of duplicating block data in replay messages. Recorded entities render independently of vanilla entity chunk tracking and physics. Dead spectator POVs remain selectable without drawing spectator heads in the scene. Exiting restores the viewer's original world, position and game mode; stored return tags also handle a disconnect or restart.

Archive bounds support **4,096 blocks horizontally per axis**, **1,024 blocks vertically**, **two million initially occupied blocks**, and **4,096 initial block entities**, subject to the `.gsave` format's 64 MB payload limit. Empty space does not count as occupied blocks. Bounds outside the source world's vertical build range are clipped. Each compressed frame and playback message supports up to 64 MB, with a 256 MB NBT decoding budget. The writer and reader use the same frame limit. Matches remain limited to two hours. Unsupported scenes report an explicit error rather than disappearing without a folder.

Replace the existing 1.8.13 jar on **both server and clients** with the rebuilt jar, then restart the server. A restart is needed to load the replay dimension from the mod's built-in datapack. Scenery staging uses a short time budget per tick and caches parsed block states. The most recently closed staging area is restored and retained for reuse when that replay is reopened; each active viewer still has an independent area. Larger scenes take longer to stage on their first opening. Opening a replay shows a Minecraft-style loading bar with archive-reading and scenery-building progress. Cancel returns to the replay library and clears the pending load. Loading and replay controls do not blur the screen. Press R to unlock the mouse and use the replay buttons; R or Escape returns to camera movement.

Playback holds its first frame while the client loads and builds scenery, then resumes after terrain rendering settles. The initial decoder also prepares one second ahead before sending that first frame. The replay staging coordinates preserve Wathe's 1,536-block scenery period and chunk alignment. Wathe's Sodium scenery offsets are calculated in the recording's original coordinates, including its negative coordinates and the lower and side terrain tiles, rather than in the translated staging area. Vanilla scenery rendering continues through Wathe's existing renderer.

This is server-state reconstruction. It does not reproduce every player's original client HUD, private UI, shader settings, or client-only mod effects. Particles are regenerated from their recorded parameters. Transient sounds and particles play while advancing; seeking reconstructs persistent state. First-person hands use recorded equipment and swing state rather than an exact copy of the player's client renderer.

Automated tests cover playback timing, speed validation, path validation, archive round trips, incomplete-file rejection, large capture bounds, independent staging coordinates, and entity/block reconstruction across rewind, removal, revival, and checkpoint boundaries. The server log confirmed that the old volume check caused recording to fail. A live multiplayer match with the updated jar is still needed to verify recording, the new playback dimension, rendering and voice end to end.
