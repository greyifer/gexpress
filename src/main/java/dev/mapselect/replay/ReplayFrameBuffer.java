package dev.mapselect.replay;

import net.minecraft.nbt.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** One decoder producer, one server-thread consumer. Snapshots never share mutable cursor maps. */
final class ReplayFrameBuffer {
    static final int CAPACITY = 60;
    record Snapshot(int tick, NbtList entities, Map<Long, NbtCompound> blocks, NbtCompound frame,
                    NbtList voice, NbtList effects) {}
    private final ReplayArchive tape;
    private final ReplayArchive.Cursor cursor;
    private final Queue<Snapshot> queue = new ConcurrentLinkedQueue<>();
    private int next;
    private volatile int readyThrough;
    volatile boolean running;
    ReplayFrameBuffer(ReplayArchive tape, int start) {
        this.tape = tape; cursor = tape.cursor(); next = start; readyThrough = start - 1;
    }
    boolean available(int tick) { return readyThrough >= tick; }
    boolean needsFill() { return queue.size() < CAPACITY / 2 && next < tape.frames.size(); }
    void fill(java.util.function.BooleanSupplier wanted) {
        while (queue.size() < CAPACITY && next < tape.frames.size() && wanted.getAsBoolean()) {
            long started = System.nanoTime();
            Set<Long> changed = cursor.move(next);
            Map<Long, NbtCompound> blocks = new HashMap<>();
            for (long position : changed) blocks.put(position, cursor.blocks.get(position));
            NbtList entities = new NbtList(); cursor.entities.values().forEach(entities::add);
            NbtCompound frame = cursor.frame();
            long decodeMillis = (System.nanoTime() - started) / 1_000_000L;
            if (decodeMillis >= 50) dev.mapselect.MapSelect.LOGGER.warn("Replay {}: preparing tick {} took {} ms (checkpoint={}, changed blocks={}, original capture interval={} ms)",
                tape.metadata.getString("id"), next, decodeMillis, next % 100 == 0, changed.size(), frame.getLong("captureIntervalMillis"));
            queue.add(new Snapshot(next, entities, blocks, frame, frame.getList("voice", 10), frame.getList("effects", 10)));
            readyThrough = next++;
        }
    }
    Snapshot takeThrough(int tick) {
        if (!available(tick)) return null;
        Snapshot last = null;
        Map<Long, NbtCompound> blocks = new HashMap<>(); NbtList voice = new NbtList(), effects = new NbtList();
        while (queue.peek() != null && queue.peek().tick <= tick) {
            last = queue.remove(); blocks.putAll(last.blocks);
            last.voice.forEach(voice::add); last.effects.forEach(effects::add);
        }
        return last == null ? null : new Snapshot(last.tick, last.entities, blocks, last.frame, voice, effects);
    }
}
