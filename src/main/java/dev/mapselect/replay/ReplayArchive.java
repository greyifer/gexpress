package dev.mapselect.replay;

import io.github.caspe.glib.api.room.GsaveFiles;
import io.github.caspe.glib.api.room.VirtualRoom;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned, server-owned archives. No object deserialization or client-provided paths. */
public final class ReplayArchive {
    private record SceneCache(Path path, long modified, long size, VirtualRoom room) {}
    private static volatile SceneCache sceneCache;
    private static VirtualRoom scene(Path directory) throws IOException {
        Path path = directory.resolve("scene.gsave").toAbsolutePath().normalize();
        long modified = Files.getLastModifiedTime(path).toMillis(), size = Files.size(path);
        SceneCache cached = sceneCache;
        if (cached != null && cached.path.equals(path) && cached.modified == modified && cached.size == size) return cached.room;
        VirtualRoom room = GsaveFiles.load(path); if(loadsEnabled)sceneCache = new SceneCache(path, modified, size, room); return room;
    }
    private record ArchiveCache(String stamp, ReplayArchive archive) {}
    private static final Map<Path, ArchiveCache> ARCHIVES = new LinkedHashMap<>(4, 0.75F, true);
    private static final java.util.concurrent.ConcurrentMap<Path, Object> LOAD_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean loadsEnabled=true;
    public static void setEnabled(boolean enabled){loadsEnabled=enabled;if(!enabled)clearCaches();}
    private static final java.util.concurrent.locks.ReentrantReadWriteLock STORAGE_LOCK=new java.util.concurrent.locks.ReentrantReadWriteLock();
    public static void deleteStorage(Path saveRoot,Path replayRoot)throws IOException{
        STORAGE_LOCK.writeLock().lock();
        try{ReplayStorageDeletion.purge(saveRoot,replayRoot);clearCaches();}finally{STORAGE_LOCK.writeLock().unlock();}
    }
    public static void clearCaches(){synchronized(ARCHIVES){ARCHIVES.clear();}sceneCache=null;}
    public static final int VERSION = 1;
    public static final int MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;
    public static final long MAX_NBT_BYTES = 256L * 1024 * 1024;
    public final VirtualRoom scene;
    public final NbtCompound metadata;
    public final List<NbtCompound> frames;
    private final List<NbtCompound> forwardFrames;
    public final NbtList markers = new NbtList();

    ReplayArchive(VirtualRoom scene, NbtCompound metadata, List<NbtCompound> frames) {
        this(scene, metadata, frames, frames);
    }
    ReplayArchive(VirtualRoom scene, NbtCompound metadata, List<NbtCompound> frames, List<NbtCompound> forwardFrames) {
        this.scene = scene; this.metadata = metadata; this.frames = frames; this.forwardFrames = forwardFrames;
        for (NbtElement marker : metadata.getList("markers",NbtElement.COMPOUND_TYPE)) markers.add(marker.copy());
    }
    public static Path directory(Path root, String id) {
        if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid replay ID");
        return root.resolve(id);
    }
    public static ReplayArchive load(Path directory) throws IOException {
        return load(directory, progress -> {});
    }
    public static ReplayArchive load(Path directory, java.util.function.DoubleConsumer progress) throws IOException {
        STORAGE_LOCK.readLock().lock();
        try{
        if(!loadsEnabled)throw new IOException("Replays are disabled");
        Path key = directory.toAbsolutePath().normalize();
        synchronized (LOAD_LOCKS.computeIfAbsent(key, ignored -> new Object())) {
            StringBuilder stamp = new StringBuilder();
            for (String name : List.of("metadata.nbt", "frames.grpl", "scene.gsave")) {
                Path file = key.resolve(name);
                stamp.append(Files.size(file)).append(':').append(Files.getLastModifiedTime(file).toMillis()).append(';');
            }
            synchronized (ARCHIVES) {
                ArchiveCache cached = ARCHIVES.get(key);
                if (cached != null && cached.stamp.equals(stamp.toString())) { progress.accept(1); return cached.archive; }
            }
            ReplayArchive archive = loadUncached(key, progress);
            synchronized (ARCHIVES) {
                if(loadsEnabled)ARCHIVES.put(key, new ArchiveCache(stamp.toString(), archive));
                while (ARCHIVES.size() > 2) ARCHIVES.remove(ARCHIVES.keySet().iterator().next());
            }
            return archive;
        }
        }finally{STORAGE_LOCK.readLock().unlock();}
    }
    private static ReplayArchive loadUncached(Path directory, java.util.function.DoubleConsumer progress) throws IOException {
        NbtCompound meta = NbtIo.readCompressed(directory.resolve("metadata.nbt"), NbtSizeTracker.of(1024 * 1024));
        if (meta.getInt("version") != VERSION) throw new IOException("Unsupported replay version");
        VirtualRoom scene = scene(directory);
        Path file=directory.resolve("frames.grpl");
        double indexStart = 0;
        if (!meta.getBoolean("packedBlocks")) {
            Path converted = directory.resolve("frames.compact.grpl");
            if (!Files.exists(converted)) {
                indexStart = 0.8;
                long sourceSize = Files.size(file), consumed = 0;
                Path pending = directory.resolve("frames.compact.partial");
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
                     DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pending)))) {
                    for (int i = 0; ; i++) {
                        int size = input.readInt();
                        if (size == -1) { output.writeInt(-1); break; }
                        if (size <= 0 || size > MAX_COMPRESSED_BYTES || i >= 144000) throw new IOException("Invalid legacy replay frame size");
                        byte[] bytes = input.readNBytes(size); if (bytes.length != size) throw new EOFException("Incomplete replay frame");
                        writeFrame(output, ReplayBlockPacking.readLegacy(bytes));
                        consumed += size + 4L; progress.accept(0.8 * consumed / Math.max(1, sourceSize));
                    }
                }
                Files.move(pending, converted, StandardCopyOption.REPLACE_EXISTING);
            }
            file = converted;
        }
        final Path frameFile = file;
        final double progressStart = indexStart;
        List<NbtCompound> frames = readFrames(frameFile, fraction -> progress.accept(progressStart + (1 - progressStart) * 0.2 * fraction));
        ReplayArchive source = new ReplayArchive(scene, meta, frames);
        Path playback = ReplayPlaybackFile.prepare(directory, frameFile, source, fraction -> progress.accept(progressStart + (1 - progressStart) * (0.2 + 0.75 * fraction)));
        List<NbtCompound> forward = readFrames(playback, fraction -> progress.accept(progressStart + (1 - progressStart) * (0.95 + 0.05 * fraction)));
        if (forward.size() != frames.size()) throw new IOException("Replay playback frame count is invalid");
        return new ReplayArchive(scene, meta, frames, forward);
    }
    private static List<NbtCompound> readFrames(Path frameFile, java.util.function.DoubleConsumer progress) throws IOException {
        List<Long> offsets=new ArrayList<>(); List<Integer> lengths=new ArrayList<>();
        // Frames are compressed separately so seeking never loads a whole match into memory.
        try (RandomAccessFile in=new RandomAccessFile(frameFile.toFile(),"r")) {
            while (true) {
                int size = in.readInt();
                if (size == -1) break;
                if (size <= 0 || size > MAX_COMPRESSED_BYTES) throw new IOException("Replay frame exceeds size limit");
                long end=in.getFilePointer()+size;
                if(end>in.length()) throw new EOFException("Incomplete replay frame");
                offsets.add(in.getFilePointer()); lengths.add(size); in.seek(end);
                progress.accept((double)end / Math.max(1, in.length()));
                if (offsets.size() > 144000) throw new IOException("Replay exceeds two hours");
            }
        }
        if (offsets.isEmpty()) throw new IOException("Empty replay");
        List<NbtCompound> frames=new AbstractList<>() {
            private final Map<Integer,NbtCompound> cache=new LinkedHashMap<>(128,0.75F,true);
            private long cachedBytes;
            @Override public synchronized NbtCompound get(int index) {
                Objects.checkIndex(index,offsets.size()); NbtCompound cached=cache.get(index); if(cached!=null) return cached;
                try(RandomAccessFile in=new RandomAccessFile(frameFile.toFile(),"r")) {
                    in.seek(offsets.get(index)); byte[] bytes=new byte[lengths.get(index)]; in.readFully(bytes);
                    NbtCompound frame=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtSizeTracker.of(MAX_NBT_BYTES));
                    if(frame.getInt("tick")!=index) throw new IOException("Replay frame timestamp is invalid");
                    cache.put(index,frame); cachedBytes += frame.getSizeInBytes();
                    while (cache.size() > 128 || cachedBytes > 64L * 1024 * 1024 && cache.size() > 1) {
                        var oldest = cache.entrySet().iterator(); var removed = oldest.next();
                        cachedBytes -= removed.getValue().getSizeInBytes(); oldest.remove();
                    }
                    return frame;
                } catch(IOException e) { throw new UncheckedIOException(e); }
            }
            @Override public int size() { return offsets.size(); }
        };
        return frames;
    }
    public static byte[] encode(NbtCompound nbt) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(nbt, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    public static byte[] compress(NbtCompound nbt) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, bytes);
        return bytes.toByteArray();
    }
    public static void writeFrame(DataOutputStream out, NbtCompound frame) throws IOException {
        byte[] bytes = compress(ReplayBlockPacking.pack(frame));
        writeBytes(out, bytes);
    }
    static void writeFrames(DataOutputStream original, DataOutputStream playback, NbtCompound frame) throws IOException {
        NbtCompound packed = ReplayBlockPacking.pack(frame);
        byte[] bytes = compress(packed);
        writeBytes(original, bytes);
        NbtCompound linear = ReplayPlaybackFile.linear(packed);
        writeBytes(playback, linear == packed ? bytes : compress(linear));
    }
    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        if (bytes.length > MAX_COMPRESSED_BYTES) throw new IOException("Replay frame exceeds 64 MB compressed size limit");
        out.writeInt(bytes.length); out.write(bytes);
    }
    /** Per-viewer state: forward playback applies each delta once without copying scenery. */
    public final class Cursor {
        public final Map<UUID, NbtCompound> entities = new LinkedHashMap<>();
        public final Map<Long, NbtCompound> blocks = new LinkedHashMap<>();
        private int position = -1;
        private NbtCompound frame;
        NbtCompound frame() { return frame; }
        public void invalidate() { position = -1; }
        public Set<Long> move(int target) {
            target = Math.clamp(target, 0, frames.size() - 1);
            Set<Long> changed = new HashSet<>();
            boolean reconstruct = position < 0 || target < position || target - position > 100;
            if (position < 0 || target < position || target - position > 100) {
                changed.addAll(blocks.keySet()); entities.clear(); blocks.clear(); position = target - target % 100 - 1;
            }
            for (int i = position + 1; i <= target; i++) {
                NbtCompound frame = reconstruct ? frames.get(i) : forwardFrames.get(i);
                this.frame = frame;
                for (NbtElement element : frame.getList("entities", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound entity = (NbtCompound)element; entities.put(entity.getUuid("UUID"), entity);
                }
                for (NbtElement removed : frame.getList("removed", NbtElement.INT_ARRAY_TYPE)) entities.remove(NbtHelper.toUuid(removed));
                ReplayBlockPacking.blocks(!reconstruct && frame.contains("blockDelta") ? frame.getCompound("blockDelta") : frame, blocks, block -> {
                    long key = block.getLong("pos");
                    if (!block.equals(blocks.put(key, block))) changed.add(key);
                });
            }
            position = target; return changed;
        }
    }
    public Cursor cursor() { return new Cursor(); }
    /** Rebuild from the nearest checkpoint, including cumulative block changes. */
    public NbtCompound state(int tick) {
        tick = Math.clamp(tick, 0, frames.size() - 1);
        int start = tick - tick % 100;
        Map<UUID, NbtCompound> entities = new LinkedHashMap<>();
        Map<Long, NbtCompound> blocks = new LinkedHashMap<>();
        for (int i = start; i <= tick; i++) {
            NbtCompound frame = frames.get(i);
            for (NbtElement element : frame.getList("entities", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entity = (NbtCompound) element;
                entities.put(entity.getUuid("UUID"), entity);
            }
            for (NbtElement element : frame.getList("removed", NbtElement.INT_ARRAY_TYPE))
                entities.remove(NbtHelper.toUuid(element));
            ReplayBlockPacking.blocks(frame, block -> blocks.put(block.getLong("pos"), block));
        }
        NbtCompound result = new NbtCompound();
        NbtList entityList = new NbtList(); entities.values().forEach(e -> entityList.add(e.copy()));
        NbtList blockList = new NbtList(); blocks.values().forEach(b -> blockList.add(b.copy()));
        result.put("entities", entityList); result.put("blocks", blockList);
        return result;
    }
}
