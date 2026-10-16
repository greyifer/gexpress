package dev.mapselect.replay;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ReplayControlPayload(String action, String value) implements CustomPayload {
    public static final Id<ReplayControlPayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "replay_control"));
    public static final PacketCodec<PacketByteBuf, ReplayControlPayload> CODEC = PacketCodec.of(
        (p, b) -> { b.writeString(p.action, 16); b.writeString(p.value, 80); },
        b -> new ReplayControlPayload(b.readString(16), b.readString(80)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
