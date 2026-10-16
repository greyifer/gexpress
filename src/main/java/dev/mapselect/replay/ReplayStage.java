package dev.mapselect.replay;

import dev.mapselect.MapSelect;
import io.github.caspe.glib.api.room.RoomBlock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Sparse replay scenery in a separate void dimension, independent of virtual-room volume limits. */
final class ReplayStage {
    static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("gexpress", "replay"));
    private static final int BLOCKS_PER_TICK = 65536;
    private static final long WORK_BUDGET_NANOS = 8_000_000L;
    private final Map<StateKey, BlockState> stateCache = new HashMap<>();
    private record StateKey(String id, Map<String, String> properties) {}
    private static final List<Runnable> JOBS = new ArrayList<>();
    final ServerWorld world;
    final BlockPos anchor;
    ReplayArchive tape;
    private final Map<BlockPos, BlockState> initial = new HashMap<>();
    private final Map<BlockPos, byte[]> initialNbt = new HashMap<>();
    private final Set<BlockPos> placed = new HashSet<>();
    private boolean closed, staged;
    private final Set<BlockPos> mutations = new HashSet<>();
    private static ReplayStage cached;

    private ReplayStage(ServerWorld world, BlockPos anchor, ReplayArchive tape) {
        this.world = world; this.anchor = anchor; this.tape = tape;
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<Runnable> jobs = List.copyOf(JOBS);
            long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
            for (int i = 0; i < jobs.size(); i++) {
                jobs.get(Math.floorMod(server.getTicks() + i, jobs.size())).run();
                if (System.nanoTime() >= deadline) break;
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> { JOBS.clear(); cached = null; });
    }

    static void prepare(MinecraftServer server, ReplayArchive tape, BooleanSupplier wanted,
                        Consumer<ReplayStage> ready, Consumer<Exception> failure, IntConsumer progress) throws IOException {
        ServerWorld world = server.getWorld(WORLD);
        if (world == null) throw new IOException("Replay dimension is missing. Restart the server with the updated mod and its built-in datapack enabled.");
        if (cached != null && cached.world == world && cached.tape.metadata.getString("id").equals(tape.metadata.getString("id"))) {
            ReplayStage reused = cached; cached = null; reused.closed = false; reused.tape = tape;
            progress.accept(100); ready.accept(reused); return;
        }
        if (tape.scene.dimensions().height() > world.getHeight()) throw new IOException("Replay is taller than the replay dimension");
        // Persist the allocation before writing blocks: a crashed staging area is never reused.
        Path counter = ReplayRecorder.root(server).resolve("stage-sequence.txt");
        long next = Files.exists(counter) ? Long.parseLong(Files.readString(counter).trim()) : 0;
        BlockPos sourceOrigin = BlockPos.fromLong(tape.metadata.getLong("origin"));
        int sourceY = sourceOrigin.getY();
        int baseY = sourceY >= world.getBottomY() && sourceY + tape.scene.dimensions().height() <= world.getTopY() ? sourceY : world.getBottomY();
        // Preserve height: Wathe distinguishes scenery from train chunks at world Y=64.
        BlockPos anchor = alignedAnchor(next, sourceOrigin).withY(baseY);
        Path pendingCounter = counter.resolveSibling("stage-sequence.tmp");
        Files.writeString(pendingCounter, Long.toString(next + 1), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(pendingCounter, counter, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(pendingCounter, counter, StandardCopyOption.REPLACE_EXISTING); }
        ReplayStage stage = new ReplayStage(world, anchor, tape);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, server);
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
        Iterator<RoomBlock> blocks = tape.scene.blocks().iterator();
        Runnable job = new Runnable() {
            int completed, reported = -1;
            public void run() {
                try {
                    if (!wanted.getAsBoolean()) { JOBS.remove(this); stage.close(); failure.accept(new IOException("Replay loading cancelled")); return; }
                    long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
                    for (int i = 0; i < BLOCKS_PER_TICK && blocks.hasNext(); i++) {
                        stage.place(blocks.next()); completed++;
                        if ((i & 63) == 63 && System.nanoTime() >= deadline) break;
                    }
                    int percent = (int)(100L * completed / Math.max(1, tape.scene.blocks().size()));
                    if (percent != reported) { progress.accept(percent); reported = percent; }
                    if (!blocks.hasNext()) { JOBS.remove(this); stage.staged = true; ready.accept(stage); }
                } catch (Exception ex) { JOBS.remove(this); stage.close(); failure.accept(ex); }
            }
        };
        JOBS.add(job);
    }

    static BlockPos anchor(long sequence) throws IOException {
        if (sequence < 0 || sequence >= 3500L * 3500) throw new IOException("Replay stage allocation limit reached");
        return new BlockPos((int)(sequence % 3500) * 8192, 0, (int)(sequence / 3500) * 8192);
    }

    /** Sodium's Wathe scenery shader repeats in absolute X, unlike the vanilla renderer. */
    static BlockPos alignedAnchor(long sequence, BlockPos origin) throws IOException {
        BlockPos allocated = anchor(sequence);
        int x = allocated.getX() - Math.floorMod(allocated.getX() - Math.floorMod(origin.getX(), 1536), 1536);
        int z = allocated.getZ() - Math.floorMod(allocated.getZ() - Math.floorMod(origin.getZ(), 16), 16);
        return new BlockPos(x, origin.getY(), z);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void place(RoomBlock block) throws IOException {
        StateKey key = new StateKey(block.blockId(), block.properties());
        BlockState state = stateCache.get(key);
        if (state == null) {
        Identifier id = Identifier.tryParse(block.blockId());
        if (id == null || !Registries.BLOCK.containsId(id)) throw new IOException("Missing replay block " + block.blockId());
        state = Registries.BLOCK.get(id).getDefaultState();
        for (var value : block.properties().entrySet()) {
            Property prop = state.getBlock().getStateManager().getProperty(value.getKey());
            if (prop == null) throw new IOException("Missing replay block property " + value.getKey());
            var parsed = prop.parse(value.getValue());
            if (parsed.isEmpty()) throw new IOException("Invalid replay block property " + value.getValue());
            state = state.with(prop, (Comparable)parsed.get());
        }
        stateCache.put(key, state);
        }
        BlockPos pos = anchor.add(block.position().x(), block.position().y(), block.position().z());
        initial.put(pos, state);
        write(pos, state);
        if (block.blockEntityNbt().isPresent()) {
            byte[] bytes = block.blockEntityNbt().orElseThrow(); initialNbt.put(pos, bytes);
            NbtCompound nbt = NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(bytes)), NbtSizeTracker.of(16 * 1024 * 1024));
            applyNbt(pos, nbt);
        }
    }

    void update(BlockPos source, NbtCompound update) {
        if (!closed) mutations.add(source.toImmutable());
        BlockPos origin = BlockPos.fromLong(tape.metadata.getLong("origin"));
        BlockPos local = source.subtract(origin);
        if (!tape.scene.dimensions().contains(new io.github.caspe.glib.api.room.RoomPosition(local.getX(), local.getY(), local.getZ()))) return;
        BlockPos pos = anchor.add(local);
        if (update == null) {
            write(pos, initial.getOrDefault(pos, Blocks.AIR.getDefaultState()));
            byte[] original = initialNbt.get(pos);
            if (original != null) {
                try { applyNbt(pos, NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(original)), NbtSizeTracker.of(16 * 1024 * 1024))); }
                catch (IOException ex) { throw new UncheckedIOException(ex); }
            }
        } else {
            write(pos, NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK), update.getCompound("state")));
            if (update.contains("nbt")) applyNbt(pos, update.getCompound("nbt"));
        }
    }

    private void write(BlockPos pos, BlockState state) {
        placed.add(pos.toImmutable());
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
    }

    private void applyNbt(BlockPos pos, NbtCompound saved) {
        var entity = world.getBlockEntity(pos);
        if (entity == null) return;
        NbtCompound nbt = saved.copy(); nbt.putInt("x", pos.getX()); nbt.putInt("y", pos.getY()); nbt.putInt("z", pos.getZ());
        entity.read(nbt, world.getRegistryManager()); entity.markDirty();
        world.getChunkManager().markForUpdate(pos);
    }

    Vec3d spawn() {
        var entities = tape.frames.getFirst().getList("entities", NbtElement.COMPOUND_TYPE);
        BlockPos origin = BlockPos.fromLong(tape.metadata.getLong("origin"));
        for (NbtElement element : entities) {
            NbtCompound entity = (NbtCompound)element;
            if (!entity.getBoolean("replayPlayer") || entity.getBoolean("replayDead")) continue;
            NbtList pos = entity.getList("Pos", NbtElement.DOUBLE_TYPE);
            return new Vec3d(anchor.getX() + pos.getDouble(0) - origin.getX(), anchor.getY() + pos.getDouble(1) - origin.getY(), anchor.getZ() + pos.getDouble(2) - origin.getZ());
        }
        return Vec3d.ofCenter(anchor.add(0, 2, 0));
    }

    void close() {
        if (closed) return; closed = true;
        if (staged) {
            Iterator<BlockPos> restore = mutations.iterator();
            JOBS.add(new Runnable() {
                public void run() {
                    long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
                    for (int i = 0; i < BLOCKS_PER_TICK && restore.hasNext(); i++) {
                        update(restore.next(), null);
                        if ((i & 63) == 63 && System.nanoTime() >= deadline) break;
                    }
                    if (!restore.hasNext()) {
                        JOBS.remove(this); mutations.clear();
                        ReplayStage previous = cached; cached = ReplayStage.this;
                        if (previous != null) previous.destroy();
                    }
                }
            });
        } else destroy();
    }
    private void destroy() {
        Iterator<BlockPos> cleanup = placed.iterator();
        JOBS.add(new Runnable() {
            public void run() {
                long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
                for (int i = 0; i < BLOCKS_PER_TICK && cleanup.hasNext(); i++) {
                    world.setBlockState(cleanup.next(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
                    if ((i & 63) == 63 && System.nanoTime() >= deadline) break;
                }
                if (!cleanup.hasNext()) { JOBS.remove(this); placed.clear(); initial.clear(); initialNbt.clear(); stateCache.clear(); }
            }
        });
    }
}
