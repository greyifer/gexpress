package dev.mapselect.replay;

import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Scenery uses one state palette and primitive arrays, not millions of repeated NBT trees. */
final class ReplayBlockPacking {
    private final LongArrayList positions = new LongArrayList();
    private final IntArrayList states = new IntArrayList();
    private final Map<NbtCompound, Integer> ids = new HashMap<>();
    private final NbtList palette = new NbtList(), extras = new NbtList();
    private void add(NbtCompound block) {
        NbtCompound state = block.getCompound("state");
        Integer id = ids.get(state);
        if (id == null) { id = ids.size(); ids.put(state, id); palette.add(state); }
        positions.add(block.getLong("pos")); states.add(id);
        if (block.contains("nbt") || !block.contains("state")) extras.add(block);
    }
    private void finish(NbtCompound frame) {
        frame.remove("blocks"); frame.putLongArray("blockPositions", positions.toLongArray());
        frame.putIntArray("blockStates", states.toIntArray()); frame.put("blockPalette", palette); frame.put("blockExtras", extras);
    }
    static NbtCompound pack(NbtCompound frame) {
        if (!frame.contains("blocks")) return frame;
        ReplayBlockPacking packing = new ReplayBlockPacking();
        for (NbtElement block : frame.getList("blocks", 10)) packing.add((NbtCompound)block);
        NbtCompound result = new NbtCompound();
        for (String key : frame.getKeys()) if (!key.equals("blocks")) result.put(key, frame.get(key));
        packing.finish(result); return result;
    }
    static void blocks(NbtCompound frame, java.util.function.Consumer<NbtCompound> accept) {
        blocks(frame, null, accept);
    }
    static void blocks(NbtCompound frame, Map<Long, NbtCompound> current, java.util.function.Consumer<NbtCompound> accept) {
        if (!frame.contains("blockPositions")) {
            for (NbtElement block : frame.getList("blocks", 10)) accept.accept((NbtCompound)block);
            return;
        }
        long[] positions = frame.getLongArray("blockPositions"); int[] states = frame.getIntArray("blockStates");
        NbtList palette = frame.getList("blockPalette", 10);
        if (positions.length != states.length) throw new IllegalArgumentException("Invalid packed replay blocks");
        Map<Long, NbtCompound> extras = new HashMap<>();
        for (NbtElement value : frame.getList("blockExtras", 10)) { NbtCompound extra = (NbtCompound)value; extras.put(extra.getLong("pos"), extra); }
        for (int i = 0; i < positions.length; i++) {
            if (states[i] < 0 || states[i] >= palette.size()) throw new IllegalArgumentException("Invalid replay block palette index");
            NbtCompound block = extras.get(positions[i]);
            NbtCompound old = current == null ? null : current.get(positions[i]);
            if (old != null && (block != null ? old.equals(block) : !old.contains("nbt") && old.getCompound("state").equals(palette.getCompound(states[i])))) continue;
            if (block == null) { block = new NbtCompound(); block.putLong("pos", positions[i]); block.put("state", palette.getCompound(states[i])); }
            accept.accept(block);
        }
    }
    /** Convert legacy checkpoints one block at a time so cumulative NBT allocation never explodes. */
    static NbtCompound readLegacy(byte[] compressed) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            if (in.readUnsignedByte() != 10) throw new IOException("Invalid replay root");
            in.readUTF(); NbtCompound frame = new NbtCompound();
            for (int type = in.readUnsignedByte(); type != 0; type = in.readUnsignedByte()) {
                String name = in.readUTF();
                if (name.equals("blocks") && type == 9) {
                    int elementType = in.readUnsignedByte(), count = in.readInt();
                    if (count < 0 || count > 4_000_000 || count > 0 && elementType != 10) throw new IOException("Invalid replay block list");
                    ReplayBlockPacking packing = new ReplayBlockPacking();
                    for (int i = 0; i < count; i++) packing.add((NbtCompound)NbtTypes.byId(10).read(in, NbtSizeTracker.of(16 * 1024 * 1024)));
                    packing.finish(frame);
                } else frame.put(name, NbtTypes.byId(type).read(in, NbtSizeTracker.of(256L * 1024 * 1024)));
            }
            return frame;
        }
    }
}
