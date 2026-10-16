package dev.mapselect.replay;

import net.minecraft.nbt.NbtCompound;
import java.io.IOException;
import java.util.*;

/** Pure encoding step, safe to run away from either gameplay thread. */
final class ReplayPacketEncoding {
    private ReplayPacketEncoding() {}
    static List<ReplayDataPayload> encode(int serial, NbtCompound message) throws IOException {
        byte[] bytes = ReplayArchive.compress(message);
        if (bytes.length > ReplayArchive.MAX_COMPRESSED_BYTES) throw new IOException("Replay message exceeds 64 MB");
        int size = ReplayDataPayload.FRAGMENT_BYTES;
        int count = (bytes.length + size - 1) / size;
        List<ReplayDataPayload> packets = new ArrayList<>(count);
        for (int part = 0; part < count; part++) packets.add(new ReplayDataPayload(serial, part, count,
            Arrays.copyOfRange(bytes, part * size, Math.min(bytes.length, (part + 1) * size))));
        return packets;
    }
}
