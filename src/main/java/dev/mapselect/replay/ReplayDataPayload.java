package dev.mapselect.replay;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded fragments of one compressed NBT message. */
public record ReplayDataPayload(int serial, int part, int count, byte[] bytes) implements CustomPayload {
    public static final int FRAGMENT_BYTES = 30000;
    public static final int MAX_FRAGMENTS = (ReplayArchive.MAX_COMPRESSED_BYTES + FRAGMENT_BYTES - 1) / FRAGMENT_BYTES;
    public static final Id<ReplayDataPayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "replay_data"));
    public static final PacketCodec<PacketByteBuf, ReplayDataPayload> CODEC = PacketCodec.of(
        (p, b) -> { b.writeVarInt(p.serial); b.writeVarInt(p.part); b.writeVarInt(p.count); b.writeByteArray(p.bytes); },
        b -> new ReplayDataPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(30000)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
