package dev.mapselect.replay;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Set;

/** One-time conversion keeps full seek checkpoints out of the sequential playback decoder. */
final class ReplayPlaybackFile {
    private static final Set<String> BLOCK_TAGS = Set.of("blocks", "blockPositions", "blockStates", "blockPalette", "blockExtras", "blockDelta");
    private ReplayPlaybackFile() {}
    // ReplayArchive serializes conversion per archive, allowing different matches to load concurrently.
    static Path prepare(Path directory, Path original, ReplayArchive source, java.util.function.DoubleConsumer progress) throws IOException {
        Path ready = directory.resolve("frames.playback.grpl"), stamp = directory.resolve("frames.playback.source");
        String identity = identity(original);
        if (Files.isRegularFile(ready) && Files.isRegularFile(stamp) && Files.readString(stamp).equals(identity)) {
            progress.accept(1); return ready;
        }
        Path pending = directory.resolve("frames.playback.partial");
        var cursor = source.cursor();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pending)))) {
            for (int tick = 0; tick < source.frames.size(); tick++) {
                Set<Long> changed = cursor.move(tick);
                NbtCompound frame = cursor.frame(), linear = new NbtCompound();
                for (String key : frame.getKeys()) if (!BLOCK_TAGS.contains(key)) linear.put(key, frame.get(key));
                NbtList blocks = new NbtList();
                for (long position : changed) blocks.add(cursor.blocks.get(position));
                linear.put("blocks", blocks);
                ReplayArchive.writeFrame(out, linear);
                progress.accept((double)(tick + 1) / source.frames.size());
            }
            out.writeInt(-1);
        } catch (UncheckedIOException e) { throw e.getCause(); }
        Files.move(pending, ready, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(stamp, identity);
        return ready;
    }
    static String identity(Path original) throws IOException {
        return "1:" + Files.size(original) + ":" + Files.getLastModifiedTime(original).toMillis();
    }
    static NbtCompound linear(NbtCompound frame) {
        if (!frame.contains("blockDelta")) return frame;
        NbtCompound result = new NbtCompound();
        for (String key : frame.getKeys()) if (!BLOCK_TAGS.contains(key)) result.put(key, frame.get(key));
        NbtCompound delta = frame.getCompound("blockDelta");
        for (String key : delta.getKeys()) result.put(key, delta.get(key));
        return result;
    }
}
