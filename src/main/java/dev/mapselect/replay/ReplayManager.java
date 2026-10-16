package dev.mapselect.replay;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.game.GexpressWorlds;
import io.github.caspe.glib.api.room.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ReplayManager {
    private static final Map<UUID, Viewing> VIEWERS = new ConcurrentHashMap<>();
    private static final Set<UUID> ENTERING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, UUID> LOADING = new HashMap<>();
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();
    private static final ExecutorService READS = Executors.newFixedThreadPool(2, r -> { Thread t=new Thread(r,"Gexpress replay reader"); t.setDaemon(true); return t; });
    private static final ExecutorService BUFFERS = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "Gexpress replay buffer"); t.setDaemon(true); return t; });
    private static final List<ExecutorService> SENDS = java.util.stream.IntStream.range(0, 4)
        .mapToObj(index -> Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Gexpress replay encoder " + index); t.setDaemon(true); return t; })).toList();
    // Server-thread counters bound playback look-ahead while the encoder or connection is slow.
    private static final Map<ServerPlayerEntity, Integer> PENDING_SENDS = new IdentityHashMap<>();
    private static int serial;
    private ReplayManager() {}
    private static CompletableFuture<Void> deletion=CompletableFuture.completedFuture(null);
    public static boolean isDeleting(){return !deletion.isDone();}
    public static CompletableFuture<Void> setEnabled(MinecraftServer server,boolean enabled){
        if(isDeleting())return deletion;
        ReplayArchive.setEnabled(enabled);
        dev.mapselect.config.GexpressConfig.replayEnabled=enabled;dev.mapselect.config.GexpressConfig.save();
        if(!enabled){
            LOADING.clear();
            for(Viewing view:List.copyOf(VIEWERS.values()))if(view.player.getServer()==server)stop(view.player,true);
            for(var world:server.getWorlds())ReplayRecorder.finish(world);
            ReplayArchive.clearCaches();deletion=ReplayRecorder.deleteSaved(server);return deletion;
        }
        return CompletableFuture.completedFuture(null);
    }
    public static boolean isViewing(ServerPlayerEntity player) { return VIEWERS.containsKey(player.getUuid()); }
    public static boolean isReplayWorld(net.minecraft.world.World world) { return world.getRegistryKey().equals(ReplayStage.WORLD); }
    public static boolean roomAccess(ServerPlayerEntity player) { return ENTERING.contains(player.getUuid()) || isViewing(player); }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ReplayControlPayload.ID, ReplayControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReplayDataPayload.ID, ReplayDataPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReplayControlPayload.ID, (payload, context) ->
            context.server().execute(() -> control(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(ReplayManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            stop(handler.player, false); LOADING.remove(handler.player.getUuid()); LAST_REQUEST.remove(handler.player.getUuid());
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->recoverMode(handler.player)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Viewing view : List.copyOf(VIEWERS.values())) if (view.player.getServer() == server) stop(view.player, true);
            LOADING.clear(); LAST_REQUEST.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_SENDS.keySet().removeIf(player -> player.getServer() == server));
        ServerLifecycleEvents.SERVER_STARTED.register(server->ReplayArchive.setEnabled(dev.mapselect.config.GexpressConfig.replayEnabled));
        ReplayRecorder.register();
        ReplayStage.register();
    }
    private static boolean eligible(ServerPlayerEntity player) {
        if(!dev.mapselect.config.GexpressConfig.replayEnabled)return false;
        if (!player.hasPermissionLevel(2)) return false;
        if (isViewing(player)) return true;
        if (GlibRooms.session(player).isPresent()) return false;
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
        return game == null || !game.isRunning() || !GameFunctions.isPlayerAliveAndSurvival(player);
    }
    private static void control(ServerPlayerEntity player, ReplayControlPayload p) {
        if(!dev.mapselect.config.GexpressConfig.replayEnabled){error(player,"Replays are disabled.");return;}
        if (!ServerPlayNetworking.canSend(player, ReplayDataPayload.ID)) return;
        if (!player.hasPermissionLevel(2)) { LOADING.remove(player.getUuid()); stop(player, true); error(player, "Only operators can access replays."); return; }
        int now = player.getServer().getTicks();
        if (p.action().equals("list")) {
            if (now - LAST_REQUEST.getOrDefault(player.getUuid(), -100) < 20) return;
            LAST_REQUEST.put(player.getUuid(), now);
        }
        try {
            switch (p.action()) {
                case "list" -> catalog(player);
                case "open" -> open(player, p.value());
                case "stop" -> { LOADING.remove(player.getUuid()); stop(player, true); }
                default -> {
                    Viewing view = VIEWERS.get(player.getUuid()); if (view == null) return;
                    switch (p.action()) {
                        case "ready" -> { if (view.warmingUp) { view.warmingUp = false; view.clock.pause(false); view.epoch++; view.force = true; } }
                        case "seek" -> { view.pendingSeek = Integer.parseInt(p.value()); }
                        case "pause" -> { view.clock.pause(!view.clock.paused()); view.epoch++; view.force = true; }
                        case "speed" -> { view.clock.speed(Double.parseDouble(p.value())); view.epoch++; view.force = true; }
                        case "camera" -> { view.pov = p.value().isBlank() ? null : UUID.fromString(p.value()); view.force = true; }
                        default -> { return; }
                    }
                }
            }
        } catch (RuntimeException e) { error(player, "Invalid replay control"); }
    }
    public static void catalog(ServerPlayerEntity player) {
        if(!dev.mapselect.config.GexpressConfig.replayEnabled){error(player,"Replays are disabled.");return;}
        if (!player.hasPermissionLevel(2)) { error(player, "Only operators can access replays."); return; }
        if (!eligible(player)) { error(player, "Replays are available in the lobby or after you die."); return; }
        Path root = ReplayRecorder.root(player.getServer());
        READS.execute(() -> {
            NbtCompound message = new NbtCompound(); message.putString("type", "catalog"); NbtList items = new NbtList();
            try {
                if (Files.isDirectory(root)) try (var files = Files.list(root)) {
                    for (Path dir : files.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).limit(100).toList()) {
                        if (!Files.isRegularFile(dir.resolve("metadata.nbt"))) continue;
                        try { items.add(NbtIo.readCompressed(dir.resolve("metadata.nbt"), NbtSizeTracker.of(1024 * 1024))); }
                        catch (IOException e) { MapSelect.LOGGER.warn("Skipping unreadable replay {}", dir, e); }
                    }
                }
                message.put("items", items);
                player.getServer().execute(() -> { if (!player.isDisconnected() && eligible(player)) send(player, message); });
            } catch (Exception e) { player.getServer().execute(() -> error(player, "Could not list replays: " + e.getMessage())); }
        });
    }
    private static void open(ServerPlayerEntity player, String id) {
        if (!eligible(player)) { error(player, "Finish playing before opening a replay."); return; }
        if (LOADING.containsKey(player.getUuid())) return;
        if (!VIEWERS.containsKey(player.getUuid()) && VIEWERS.size() + LOADING.keySet().stream().filter(uuid -> !VIEWERS.containsKey(uuid)).count() >= 4) { error(player, "All four replay viewer slots are busy."); return; }
        Path dir = ReplayArchive.directory(ReplayRecorder.root(player.getServer()), id);
        UUID request = UUID.randomUUID(); LOADING.put(player.getUuid(), request);
        loading(player, "Reading replay", 0);
        READS.execute(() -> {
            try {
                java.util.concurrent.atomic.AtomicInteger reported = new java.util.concurrent.atomic.AtomicInteger(-1);
                ReplayArchive tape = ReplayArchive.load(dir, fraction -> {
                    int percent = (int)(fraction * 15);
                    if (reported.getAndSet(percent) == percent) return;
                    player.getServer().execute(() -> {
                        if (request.equals(LOADING.get(player.getUuid()))) loading(player, "Reading replay", percent);
                    });
                });
                tape.frames.getFirst();
                player.getServer().execute(() -> {
                    if (!request.equals(LOADING.get(player.getUuid()))) return;
                    if (player.isDisconnected() || !eligible(player)) { LOADING.remove(player.getUuid()); return; }
                    loading(player, "Building replay world", 15);
                    stop(player, true);
                    GameMode previous = player.interactionManager.getGameMode();
                    try {
                        ReplayStage.prepare(player.getServer(), tape,
                            () -> request.equals(LOADING.get(player.getUuid())) && !player.isDisconnected() && eligible(player), stage -> {
                                loading(player, "Entering replay", 96);
                                String location = "gexpress_replay_location|" + player.getWorld().getRegistryKey().getValue() + "|" + player.getX() + "|" + player.getY() + "|" + player.getZ() + "|" + player.getYaw() + "|" + player.getPitch();
                                player.addCommandTag(location);
                                player.addCommandTag("gexpress_replay_return_" + previous.getName());
                                Vec3d spawn = stage.spawn();
                                player.changeGameMode(GameMode.SPECTATOR);
                                player.teleport(stage.world, spawn.x, spawn.y, spawn.z, player.getYaw(), player.getPitch());
                                Vec3d offset = Vec3d.of(stage.anchor);
                                Viewing view = new Viewing(player, tape, previous, offset, stage); VIEWERS.put(player.getUuid(), view);
                                NbtCompound message = tape.metadata.copy(); message.putString("type", "start"); message.put("markers", tape.markers.copy());
                                message.putBoolean("warmup", true);
                                message.putDouble("offsetX", offset.x); message.putDouble("offsetY", offset.y); message.putDouble("offsetZ", offset.z);
                                send(player, message); render(view, false); LOADING.remove(player.getUuid());
                            }, ex -> {
                                if (!request.equals(LOADING.get(player.getUuid()))) return;
                                LOADING.remove(player.getUuid()); recoverMode(player); MapSelect.LOGGER.error("Could not stage replay {}", id, ex);
                                error(player, "Could not open replay: " + ex.getMessage());
                            }, percent -> {
                                if (request.equals(LOADING.get(player.getUuid()))) loading(player, "Building replay world", 15 + percent * 80 / 100);
                            });
                    } catch (Exception e) {
                        LOADING.remove(player.getUuid());
                        error(player, "Could not open replay: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                player.getServer().execute(() -> {
                    if (!request.equals(LOADING.get(player.getUuid()))) return;
                    LOADING.remove(player.getUuid()); error(player, "Could not load replay: " + e.getMessage());
                });
            }
        });
    }
    private static void tick(MinecraftServer server) {
        for (Viewing view : List.copyOf(VIEWERS.values())) {
            if (view.player.getServer() != server) continue;
            if (!view.player.hasPermissionLevel(2) || view.player.getServerWorld() != view.stage.world) { stop(view.player, true); continue; }
            if (PENDING_SENDS.getOrDefault(view.player, 0) >= 4) continue;
            if (view.pendingSeek != null) {
                view.clock.seek(view.pendingSeek); view.pendingSeek = null; view.epoch++; view.force = true;
                resetBuffer(view);
            }
            if (view.buffer == null) resetBuffer(view);
            fillBuffer(view);
            int target = view.displayed == null ? view.clock.tick() : view.clock.nextTick();
            if (view.warmingUp && !view.buffer.available(Math.min(view.clock.duration(), target + 20))) { buffering(view, true); continue; }
            if (target != view.last || view.displayed == null) {
                if (!view.buffer.available(target)) { buffering(view, true); continue; }
                ReplayFrameBuffer.Snapshot snapshot = view.buffer.takeThrough(target);
                if (snapshot == null) { buffering(view, true); continue; }
                buffering(view, false);
                if (view.displayed != null) view.clock.advance();
                boolean continuous = !view.force;
                view.displayed = snapshot; renderPrepared(view, continuous, snapshot);
            } else {
                buffering(view, false);
                if (!view.clock.paused()) view.clock.advance();
                if (view.force && view.displayed != null) renderPrepared(view, false,
                    new ReplayFrameBuffer.Snapshot(view.last, view.displayed.entities(), Map.of(), view.displayed.frame(), new NbtList(), new NbtList()));
            }
            fillBuffer(view);
        }
    }
    private static void render(Viewing view, boolean continuous) { resetBuffer(view); fillBuffer(view); }
    private static void resetBuffer(Viewing view) {
        view.buffer = new ReplayFrameBuffer(view.tape, view.clock.tick()); view.displayed = null; view.restoreBlocks = true;
    }
    private static void fillBuffer(Viewing view) {
        ReplayFrameBuffer buffer = view.buffer;
        if (buffer.running || !buffer.needsFill()) return;
        buffer.running = true;
        BUFFERS.execute(() -> {
            try { buffer.fill(() -> VIEWERS.get(view.player.getUuid()) == view && view.buffer == buffer); }
            catch (Exception ex) {
                view.player.getServer().execute(() -> {
                    if (VIEWERS.get(view.player.getUuid()) != view || view.buffer != buffer) return;
                    MapSelect.LOGGER.error("Replay buffering failed", ex);
                    error(view.player, "Replay playback failed: " + ex.getMessage()); stop(view.player, true);
                });
            } finally { buffer.running = false; }
        });
    }
    private static void buffering(Viewing view, boolean value) {
        if (view.buffering == value) return;
        view.buffering = value;
        NbtCompound message = new NbtCompound(); message.putString("type", "buffering"); message.putBoolean("buffering", value); send(view.player, message);
    }
    private static void renderPrepared(Viewing view, boolean continuous, ReplayFrameBuffer.Snapshot snapshot) {
        long appliedAt = System.nanoTime();
        int tick = snapshot.tick(); NbtList entityStates = snapshot.entities();
        try {
            if (view.pov != null) {
                BlockPos origin = BlockPos.fromLong(view.tape.metadata.getLong("origin"));
                for (NbtElement element : entityStates) {
                    NbtCompound entity = (NbtCompound)element;
                    if (!entity.getBoolean("replayPlayer") || !view.pov.equals(entity.getUuid("UUID"))) continue;
                    NbtList pos = entity.getList("Pos", NbtElement.DOUBLE_TYPE);
                    Vec3d camera = new Vec3d(pos.getDouble(0) - origin.getX() + view.offset.x, pos.getDouble(1) - origin.getY() + view.offset.y, pos.getDouble(2) - origin.getZ() + view.offset.z);
                    if (view.player.getPos().squaredDistanceTo(camera) > 64) view.player.teleport(view.stage.world, camera.x, camera.y, camera.z, view.player.getYaw(), view.player.getPitch());
                    break;
                }
            }
            Map<Long, NbtCompound> changed = snapshot.blocks();
            Set<Long> positions = new HashSet<>(changed.keySet());
            if (view.restoreBlocks) positions.addAll(view.blocks.keySet());
            view.restoreBlocks = false;
            for (long position : positions) {
                NbtCompound next = changed.get(position);
                if (Objects.equals(next, view.blocks.get(position))) continue;
                if (next == null) view.blocks.remove(position); else view.blocks.put(position, next);

                if (next == null) {
                    view.stage.update(BlockPos.fromLong(position), null);
                    NbtCompound reset = new NbtCompound(); reset.putLong("pos", position); reset.putBoolean("reset", true);
                } else {
                    view.stage.update(BlockPos.fromLong(position), next);
                }
            }

            NbtCompound message = new NbtCompound(); message.putString("type", "frame");
            message.putInt("tick", tick); message.putInt("epoch", view.epoch); message.putDouble("speed", view.clock.speed()); message.putBoolean("paused", view.clock.paused());
            message.put("entities", entityStates); // Scenery and block entities arrive through native chunk packets.
            NbtCompound frame = snapshot.frame();
            if (frame.contains("train")) message.put("train", frame.getCompound("train").copy());
            message.putString("mapEffect", frame.getString("mapEffect"));
            message.putLong("time", frame.getLong("time")); message.putBoolean("rain", frame.getBoolean("rain")); message.putBoolean("thunder", frame.getBoolean("thunder"));
            message.put("voice", continuous && view.clock.speed() == 1 ? snapshot.voice() : new NbtList());
            message.put("effects", continuous ? snapshot.effects() : new NbtList()); send(view.player, message);
            long applyMillis = (System.nanoTime() - appliedAt) / 1_000_000L;
            if (applyMillis >= 25) MapSelect.LOGGER.warn("Replay {}: applying tick {} took {} ms ({} block changes, recorded tick interval={} ms)",
                view.tape.metadata.getString("id"), tick, applyMillis, positions.size(), frame.getLong("captureIntervalMillis"));
            view.last=tick; view.force=false;
        } catch (Exception e) {
            MapSelect.LOGGER.error("Replay playback failed", e); error(view.player, "Replay playback failed: " + e.getMessage()); stop(view.player, true);
        }
    }
    public static void stop(ServerPlayerEntity player, boolean returnToWorld) {
        Viewing view = VIEWERS.remove(player.getUuid()); if (view == null) return;
        view.stage.close();
        // Also return disconnected viewers before their data is saved. Tags cover crash recovery.
        recoverMode(player);
        if (!player.isDisconnected()) { NbtCompound message=new NbtCompound(); message.putString("type","stop"); send(player,message); }
    }
    private static void recoverMode(ServerPlayerEntity player) {
        for (String tag : Set.copyOf(player.getCommandTags())) {
            if (!tag.startsWith("gexpress_replay_location|")) continue;
            try {
                String[] values = tag.split("\\|");
                var world = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of(values[1])));
                if (world == null) world = player.getServer().getOverworld();
                player.teleport(world, Double.parseDouble(values[2]), Double.parseDouble(values[3]), Double.parseDouble(values[4]), Float.parseFloat(values[5]), Float.parseFloat(values[6]));
            } catch (RuntimeException ex) { MapSelect.LOGGER.warn("Could not restore replay return position", ex); }
            player.removeCommandTag(tag);
        }
        for(GameMode mode:GameMode.values()) {
            String tag="gexpress_replay_return_"+mode.getName();
            if(player.getCommandTags().contains(tag)) { player.changeGameMode(mode); player.removeCommandTag(tag); }
        }
    }
    private static void loading(ServerPlayerEntity player, String phase, int percent) {
        NbtCompound message = new NbtCompound(); message.putString("type", "loading");
        message.putString("phase", phase); message.putInt("progress", Math.clamp(percent, 0, 100)); send(player, message);
    }
    private static void error(ServerPlayerEntity player, String text) {
        if (player.isDisconnected()) return;
        player.sendMessage(Text.literal(text), false);
        NbtCompound message=new NbtCompound(); message.putString("type","error"); message.putString("message",text); send(player,message);
    }
    private static void send(ServerPlayerEntity player, NbtCompound message) {
        if (!ServerPlayNetworking.canSend(player, ReplayDataPayload.ID)) return;
        int id = serial++;
        PENDING_SENDS.merge(player, 1, Integer::sum);
        // Message ownership transfers to this ordered queue; callers must not mutate it.
        SENDS.get(Math.floorMod(player.getUuid().hashCode(), SENDS.size())).execute(() -> {
            List<ReplayDataPayload> encoded;
            try {
                encoded = ReplayPacketEncoding.encode(id, message);
            } catch (Exception e) { MapSelect.LOGGER.error("Could not encode replay data", e); encoded = List.of(); }
            List<ReplayDataPayload> packets = encoded;
            if (!player.getServer().isRunning()) return;
            player.getServer().execute(() -> {
                try {
                    if (!player.isDisconnected()) for (var packet : packets) ServerPlayNetworking.send(player, packet);
                } finally { PENDING_SENDS.computeIfPresent(player, (key, count) -> count <= 1 ? null : count - 1); }
            });
        });
    }
    private static final class Viewing {
        final ServerPlayerEntity player; final ReplayArchive tape; final GameMode previous; final Vec3d offset; final ReplayStage stage; final ReplayClock clock; final ReplayArchive.Cursor cursor;
        Integer pendingSeek; int last=-1, epoch; boolean force=true, buffering, restoreBlocks, warmingUp=true; volatile ReplayFrameBuffer buffer; ReplayFrameBuffer.Snapshot displayed; UUID pov; Map<Long,NbtCompound> blocks=new HashMap<>();
        Viewing(ServerPlayerEntity player, ReplayArchive tape, GameMode previous, Vec3d offset, ReplayStage stage) {
            this.player=player; this.tape=tape; this.previous=previous; this.offset=offset; this.stage=stage; this.clock=new ReplayClock(tape.frames.size()-1); this.cursor=tape.cursor();
            this.clock.pause(true);
        }
    }
}
