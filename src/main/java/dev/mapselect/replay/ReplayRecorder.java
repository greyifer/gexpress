package dev.mapselect.replay;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import io.github.caspe.glib.api.room.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class ReplayRecorder {
    private static final Map<ServerWorld, Recording> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<ServerWorld> ATTEMPTED = ConcurrentHashMap.newKeySet();
    private static final Map<ServerWorld, String> STATUS = new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(512), r -> { Thread t = new Thread(r, "Gexpress replay files"); t.setDaemon(true); return t; });
    private ReplayRecorder() {}
    public static CompletableFuture<Void> deleteSaved(net.minecraft.server.MinecraftServer server){
        var done=new CompletableFuture<Void>();
        // Same writer queue as frame writes and finalization: deleted files cannot be recreated by queued saves.
        try{IO.execute(()->{
            try{ReplayArchive.deleteStorage(server.getSavePath(WorldSavePath.ROOT),root(server));done.complete(null);}
            catch(Exception error){MapSelect.LOGGER.error("Could not delete saved replays",error);done.completeExceptionally(error);}
        });}catch(RejectedExecutionException error){done.completeExceptionally(error);}
        return done;
    }
    public static boolean isRecording(ServerWorld world) { return ACTIVE.containsKey(world); }
    public static Path root(net.minecraft.server.MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("gexpress-replays");
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { Files.createDirectories(root(server)); MapSelect.LOGGER.info("Replay recordings directory: {}", root(server).toAbsolutePath().normalize()); }
            catch (IOException e) { MapSelect.LOGGER.error("Could not create replay recordings directory: {}", root(server), e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ACTIVE.clear(); ATTEMPTED.clear(); STATUS.clear(); });
        GameEvents.ON_FINISH_INITIALIZE.register(ReplayRecorder::begin);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> { if (world instanceof ServerWorld sw) finish(sw); });
        ServerTickEvents.END_WORLD_TICK.register(ReplayRecorder::tick);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            Recording r = ACTIVE.get(world);
            if (r != null) scan(chunk, r.area, pos -> r.dirty.add(pos.toImmutable()));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : List.copyOf(ACTIVE.keySet())) if (world.getServer() == server) finish(world);
            try { IO.submit(() -> {}).get(30, TimeUnit.SECONDS); }
            catch (Exception e) { MapSelect.LOGGER.error("Replay save did not finish before shutdown", e); }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof PlayerEntity player) death(player,
                source.getAttacker() instanceof PlayerEntity killer ? killer : null);
        });
    }
    private static void begin(World raw, GameWorldComponent game) {
        if (!dev.mapselect.config.GexpressConfig.replayEnabled || !(raw instanceof ServerWorld world)) return;
        finish(world);
        ATTEMPTED.add(world);
        Path dir = null;
        try {
            Files.createDirectories(root(world.getServer()));
            String id = "match_" + Instant.now().toEpochMilli() + "_" + UUID.randomUUID().toString().substring(0, 8);
            dir = ReplayArchive.directory(root(world.getServer()), id);
            Files.createDirectories(dir);
            Box area = dev.doctor4t.wathe.cca.MapVariablesWorldComponent.KEY.get(world).getPlayArea();
            if (area == null || world.getPlayers().isEmpty()) throw new IOException("Match has no play area or players");
            // Wathe renders terrain tiles below and beside the train, outside the playable carriages.
            Box sceneArea = area;
            if (game.getMapEffect() instanceof dev.doctor4t.wathe.game.mapeffect.HarpyExpressTrainMapEffect) {
                sceneArea = ReplayCaptureBounds.sceneryArea(area, world.getBottomY());
            }
            ReplayCaptureBounds bounds = ReplayCaptureBounds.of(sceneArea, world.getBottomY(), world.getTopY());
            BlockPos min = bounds.min(), max = bounds.max();
            RoomDimensions dims = bounds.dimensions();
            VirtualRoom.Builder builder = VirtualRoom.builder(RoomId.of(id), "Match " + Instant.now(), dims);
            Set<BlockPos> blockEntities = new HashSet<>();
            List<BlockPos> occupied = new ArrayList<>();
            // Empty/unloaded chunks do not cost a cubic-volume scan or generate new terrain.
            // Chunks loaded later are captured by the CHUNK_LOAD hook.
            Box capture = bounds.box();
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    var chunk = world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk != null) scan(chunk, capture, pos -> {
                        if (occupied.size() >= 2_000_000) throw new IllegalArgumentException("Replay scene exceeds two million occupied blocks");
                        occupied.add(pos.toImmutable());
                    });
                }
            }
            for (BlockPos pos : occupied) {
                BlockState state = world.getBlockState(pos);
                if (state.isAir()) continue;
                Map<String, String> props = new TreeMap<>();
                state.getEntries().forEach((p, v) -> props.put(p.getName(), property(p, v)));
                byte[] nbt = null;
                BlockEntity be = world.getBlockEntity(pos);
                if (be != null) {
                    blockEntities.add(pos.toImmutable());
                    NbtCompound data = be.createNbtWithIdentifyingData(world.getRegistryManager());
                    data.putInt("x", pos.getX()-min.getX()); data.putInt("y", pos.getY()-min.getY()); data.putInt("z", pos.getZ()-min.getZ());
                    nbt = ReplayArchive.encode(data);
                }
                builder.block(new RoomBlock(new RoomPosition(pos.getX()-min.getX(), pos.getY()-min.getY(), pos.getZ()-min.getZ()),
                    Registries.BLOCK.getId(state.getBlock()).toString(), props, nbt));
            }
            NbtCompound meta = new NbtCompound();
            if(blockEntities.size()>4096) throw new IOException("Play area exceeds the replay limit of 4096 block entities");
            meta.putBoolean("packedBlocks", true); meta.putInt("version", ReplayArchive.VERSION); meta.putString("id", id);
            meta.putString("date", Instant.now().toString()); meta.putString("dimension", world.getRegistryKey().getValue().toString());
            meta.putLong("origin", min.asLong());
            NbtList roster = new NbtList();
            for (ServerPlayerEntity player : world.getPlayers()) {
                if(ReplayManager.isViewing(player) || (!game.getRoles().containsKey(player.getUuid()) && !player.isSpectator() && !area.contains(player.getPos()))) continue;
                NbtCompound p = new NbtCompound(); p.putUuid("UUID", player.getUuid()); p.putString("name", player.getGameProfile().getName()); roster.add(p);
            }
            meta.put("players", roster);
            Recording recording = new Recording(world, capture, min, dir, meta, builder.build(), blockEntities);
            ACTIVE.put(world, recording);
            recording.enqueue(() -> {
                GsaveFiles.saveAtomic(recording.dir.resolve("scene.gsave"), recording.scene);
                recording.out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(recording.dir.resolve("frames.partial"))));
                recording.playbackOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(recording.dir.resolve("frames.playback.partial"))));
            });
            report(world, "Recording " + id + " (" + dims + ", " + occupied.size() + " occupied blocks)");
            captureFrame(world);
            world.getPlayers().forEach(p -> p.sendMessage(net.minecraft.text.Text.literal("This match is being recorded, including voice chat."), false));
        } catch (Exception e) {
            report(world, "Recording failed: " + e.getMessage());
            if (dir != null) try { Files.writeString(dir.resolve("recording-error.txt"), e.toString()); }
            catch (IOException writeError) { MapSelect.LOGGER.error("Could not write replay failure details", writeError); }
            MapSelect.LOGGER.error("Could not start match replay recording", e);
            world.getPlayers().forEach(p -> p.sendMessage(net.minecraft.text.Text.literal("Replay recording unavailable: " + e.getMessage()), false));
        }
    }
    private static void scan(net.minecraft.world.chunk.WorldChunk chunk, Box area, java.util.function.Consumer<BlockPos> accept) {
        var sections = chunk.getSectionArray();
        int x0 = chunk.getPos().getStartX(), z0 = chunk.getPos().getStartZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            int y0 = chunk.getBottomY() + sectionIndex * 16;
            if (section == null || section.isEmpty() || y0 >= area.maxY || y0 + 16 <= area.minY) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (section.getBlockState(x, y, z).isAir()) continue;
                BlockPos pos = new BlockPos(x0 + x, y0 + y, z0 + z);
                if (area.intersects(new Box(pos))) accept.accept(pos);
            }
        }
    }
    private static void report(ServerWorld world, String message) {
        STATUS.put(world, message);
        MapSelect.LOGGER.info("Replay {}: {}", world.getRegistryKey().getValue(), message);
        try {
            Path root = root(world.getServer()); Files.createDirectories(root);
            Files.writeString(root.resolve("recording-status-" + world.getRegistryKey().getValue().toString().replaceAll("[^a-zA-Z0-9_-]", "_") + ".txt"), Instant.now() + "\n" + message + "\n");
        } catch (IOException ex) { MapSelect.LOGGER.error("Could not save replay recording status", ex); }
    }
    public static String status(ServerWorld world) {
        String matches = STATUS.entrySet().stream().filter(e -> e.getKey().getServer() == world.getServer())
            .sorted(java.util.Comparator.comparing(e -> e.getKey().getRegistryKey().getValue().toString()))
            .map(e -> e.getKey().getRegistryKey().getValue() + ": " + e.getValue()).collect(java.util.stream.Collectors.joining("\n"));
        return "Replay folder: " + root(world.getServer()).toAbsolutePath().normalize() + "\n" + (matches.isEmpty() ? "Waiting for a match" : matches)
            + "\nFile writer queue: " + IO.getQueue().size();
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static String property(Property p, Comparable v) { return p.name(v); }
    public static void changed(ServerWorld world, BlockPos pos) {
        Recording r = ACTIVE.get(world);
        if (r != null && r.contains(pos)) r.dirty.add(pos.toImmutable());
    }
    public static void voice(ServerWorld world, UUID speaker, byte[] opus, float distance) {
        Recording r = ACTIVE.get(world);
        if (r == null || !r.roster.contains(speaker) || opus.length == 0 || opus.length > 4096) return;
        synchronized (r.voices) {
            if (r.voices.size() >= 512) return;
            NbtCompound v = new NbtCompound(); v.putUuid("speaker", speaker); v.putByteArray("opus", opus.clone()); v.putFloat("distance", distance); r.voices.add(v);
        }
    }
    public static void effect(ServerWorld world, Vec3d position, NbtCompound data) {
        Recording r=ACTIVE.get(world); if(r==null || !r.area.contains(position) || r.effects.size()>=4096) return;
        data.putDouble("x",position.x); data.putDouble("y",position.y); data.putDouble("z",position.z); r.effects.add(data);
    }
    public static void death(PlayerEntity victim, PlayerEntity killer) {
        if (!(victim.getWorld() instanceof ServerWorld world)) return;
        Recording r = ACTIVE.get(world); if (r == null || !r.roster.contains(victim.getUuid())) return;
        if (r.lastDeath.getOrDefault(victim.getUuid(), -1) == r.tick) return;
        r.lastDeath.put(victim.getUuid(), r.tick);
        NbtCompound marker = new NbtCompound(); marker.putInt("tick", r.tick);
        marker.putString("victim", victim.getGameProfile().getName());
        marker.putString("killer", killer == null ? "" : killer.getGameProfile().getName());
        r.deaths.add(marker); r.markers.add(marker.copy());
    }
    private static void tick(ServerWorld world) {
        if(!dev.mapselect.config.GexpressConfig.replayEnabled)return;
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE && !ATTEMPTED.contains(world)) begin(world, game);
        if (game.getGameStatus() == GameWorldComponent.GameStatus.INACTIVE || game.getGameStatus() == GameWorldComponent.GameStatus.STARTING) {
            finish(world); ATTEMPTED.remove(world); return;
        }
        try { captureFrame(world); }
        catch (Exception ex) {
            Recording recording = ACTIVE.get(world);
            if (recording != null) recording.failed = true;
            report(world, "Recording stopped: " + ex.getMessage());
            MapSelect.LOGGER.error("Replay frame capture failed", ex);
            finish(world);
        }
    }
    private static void captureFrame(ServerWorld world) {
        Recording r = ACTIVE.get(world); if (r == null) return;
        captureFrame(r);
    }
    private static void captureFrame(Recording r) {
        ServerWorld world = r.world;
        if (r.failed || r.tick >= 144000) {
            String reason=r.failed?"Replay recording stopped because its file writer failed. Check the server log.":"Replay recording reached the two-hour limit.";
            world.getPlayers().forEach(p->p.sendMessage(net.minecraft.text.Text.literal(reason),false));
            finish(world); return;
        }
        NbtCompound frame = new NbtCompound(); frame.putInt("tick", r.tick);
        boolean key = r.tick % 100 == 0; frame.putBoolean("key", key);
        Map<UUID, NbtCompound> current = new LinkedHashMap<>();
        for (Entity entity : world.iterateEntities()) {
            boolean twin = entity instanceof dev.mapselect.entity.TwinFakePlayerEntity;
            boolean player = entity instanceof ServerPlayerEntity && !twin;
            if (player && !r.roster.contains(entity.getUuid())) continue;
            if (!player && !entity.getBoundingBox().intersects(r.area)) continue;
            if (entity.isRemoved()) continue;
            NbtCompound data = new NbtCompound();
            if (player || twin) entity.writeNbt(data); else if (!entity.saveNbt(data)) continue;
            data.putString("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
            data.putUuid("UUID", entity.getUuid());
            data.putString("replayPose",entity.getPose().name()); data.putBoolean("replaySneak",entity.isSneaking());
            data.putBoolean("replaySprint",entity.isSprinting()); data.putBoolean("replayInvisible",entity.isInvisible());
            ReplayEntityState.capture(entity, data);
            if(entity instanceof LivingEntity living) {
                data.putFloat("replayHead",living.headYaw); data.putFloat("replayBody",living.bodyYaw);
                data.putFloat("replaySwing",living.handSwingProgress);
                data.putFloat("replayLimb",living.limbAnimator.getSpeed());
                data.putBoolean("replayUsing",living.isUsingItem()); data.putString("replayHand",living.getActiveHand().name());
            }
            if (entity instanceof ServerPlayerEntity p) {
                data.putBoolean("replayPlayer", true); data.putString("replayName", p.getGameProfile().getName());
                data.putBoolean("replayDead", twin ? !p.isAlive() : !GameFunctions.isPlayerAliveAndSurvival(p));
                NbtList textures = new NbtList();
                p.getGameProfile().getProperties().get("textures").forEach(prop -> {
                    NbtCompound texture = new NbtCompound(); texture.putString("value", prop.value());
                    if (prop.signature() != null) texture.putString("signature", prop.signature()); textures.add(texture);
                }); data.put("replayTextures", textures);
            }
            current.put(entity.getUuid(), data);
        }
        NbtList entities = new NbtList();
        current.forEach((uuid, data) -> { if (key || !data.equals(r.previous.get(uuid))) entities.add(data); });
        NbtList removed = new NbtList();
        for (UUID uuid : r.previous.keySet()) if (!current.containsKey(uuid)) removed.add(NbtHelper.fromUuid(uuid));
        frame.put("entities", entities); frame.put("removed", removed); r.previous = current;
        r.dirty.addAll(r.blockEntities);
        NbtList updates = new NbtList();
        for (BlockPos pos : r.dirty) {
            NbtCompound block = new NbtCompound(); block.putLong("pos", pos.asLong());
            block.put("state", NbtHelper.fromBlockState(world.getBlockState(pos)));
            BlockEntity be = world.getBlockEntity(pos);
            if (be != null) { block.put("nbt", be.createNbtWithIdentifyingData(world.getRegistryManager())); r.blockEntities.add(pos); }
            if (!block.equals(r.blocks.get(pos.asLong()))) { r.blocks.put(pos.asLong(), block); updates.add(block); }
        }
        r.dirty.clear();
        if (key) {
            NbtCompound delta = new NbtCompound(); delta.put("blocks", updates);
            frame.put("blockDelta", ReplayBlockPacking.pack(delta));
        }
        if (key) { updates = new NbtList(); for (NbtCompound block : r.blocks.values()) updates.add(block); }
        frame.put("blocks", updates);
        synchronized (r.voices) { NbtList voice = new NbtList(); r.voices.forEach(voice::add); r.voices.clear(); frame.put("voice", voice); }
        NbtList deaths = new NbtList(); r.deaths.forEach(deaths::add); r.deaths.clear(); frame.put("deaths", deaths);
        NbtList effects=new NbtList(); r.effects.forEach(effects::add); r.effects.clear(); frame.put("effects",effects);
        NbtCompound train = new NbtCompound();
        dev.doctor4t.wathe.cca.TrainWorldComponent.KEY.get(world).writeToNbt(train, world.getRegistryManager());
        frame.put("train", train);
        dev.doctor4t.wathe.api.WatheMapEffects.MAP_EFFECTS.forEach((id, effect) -> {
            if (effect == GameWorldComponent.KEY.get(world).getMapEffect()) frame.putString("mapEffect", id.toString());
        });
        long capturedAt = System.nanoTime();
        frame.putLong("captureIntervalMillis", r.lastCapture == 0 ? 50 : (capturedAt - r.lastCapture) / 1_000_000L);
        r.lastCapture = capturedAt;
        frame.putLong("time", world.getTimeOfDay()); frame.putBoolean("rain", world.isRaining()); frame.putBoolean("thunder", world.isThundering());
        r.tick++;
        r.enqueue(() -> {
            ReplayArchive.writeFrames(r.out, r.playbackOut, frame);
        });
    }
    public static void finish(ServerWorld world) {
        Recording r = ACTIVE.remove(world); if (r == null) return;
        if (!r.failed && r.tick < 144000 && GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.INACTIVE) {
            try { captureFrame(r); }
            catch (Exception ex) { r.failed = true; report(world, "Final replay frame failed: " + ex.getMessage()); MapSelect.LOGGER.error("Could not capture final replay frame", ex); }
        }
        // Closing is queued on the same writer so all previous frames reach disk first.
        Runnable close = () -> {
            try {
                if (r.out == null) return;
                r.out.writeInt(-1); r.out.close();
                if (r.playbackOut != null) { r.playbackOut.writeInt(-1); r.playbackOut.close(); }
                if (r.failed) return;
                Files.move(r.dir.resolve("frames.partial"), r.dir.resolve("frames.grpl"), StandardCopyOption.REPLACE_EXISTING);
                Files.move(r.dir.resolve("frames.playback.partial"), r.dir.resolve("frames.playback.grpl"), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(r.dir.resolve("frames.playback.source"), ReplayPlaybackFile.identity(r.dir.resolve("frames.grpl")));
                r.meta.putInt("duration", r.tick-1);
                r.meta.put("markers",r.markers);
                NbtIo.writeCompressed(r.meta, r.dir.resolve("metadata.partial"));
                Files.move(r.dir.resolve("metadata.partial"), r.dir.resolve("metadata.nbt"), StandardCopyOption.REPLACE_EXISTING);
                report(world, "Saved replay " + r.meta.getString("id") + " to " + r.dir.toAbsolutePath().normalize());
            } catch (Exception e) { report(world, "Replay save failed: " + e.getMessage()); MapSelect.LOGGER.error("Could not finalize replay {}", r.dir, e); }
        };
        try { IO.execute(close); }
        catch (RejectedExecutionException e) { r.failed = true; MapSelect.LOGGER.error("Replay writer overloaded; archive remains incomplete", e); }
    }
    private static final class Recording {
        final ServerWorld world; final Box area; final BlockPos origin; final Path dir; final NbtCompound meta; final VirtualRoom scene;
        final Set<BlockPos> dirty = new HashSet<>(), blockEntities;
        final Set<UUID> roster = new HashSet<>();
        final Map<UUID, Integer> lastDeath = new HashMap<>();
        final Map<Long, NbtCompound> blocks = new LinkedHashMap<>();
        final List<NbtCompound> voices = new ArrayList<>(), deaths = new ArrayList<>(), effects=new ArrayList<>();
        final NbtList markers=new NbtList();
        Map<UUID, NbtCompound> previous = Map.of();
        int tick; long lastCapture; volatile boolean failed; DataOutputStream out, playbackOut;
        Recording(ServerWorld world, Box area, BlockPos origin, Path dir, NbtCompound meta, VirtualRoom scene, Set<BlockPos> blockEntities) {
            this.world=world; this.area=area; this.origin=origin; this.dir=dir; this.meta=meta; this.scene=scene; this.blockEntities=blockEntities;
            for(NbtElement element:meta.getList("players",NbtElement.COMPOUND_TYPE)) roster.add(((NbtCompound)element).getUuid("UUID"));
        }
        boolean contains(BlockPos pos) { return area.intersects(new Box(pos)); }
        void enqueue(IoTask task) {
            if (failed) return;
            try { IO.execute(() -> { if (failed) return; try { task.run(); } catch (Exception e) { failed=true; report(world, "Replay file write failed: " + e.getMessage()); MapSelect.LOGGER.error("Replay write failed: {}", dir, e); } }); }
            catch (RejectedExecutionException e) { failed=true; MapSelect.LOGGER.error("Replay writer queue full: {}", dir); }
        }
    }
    @FunctionalInterface private interface IoTask { void run() throws Exception; }
}
