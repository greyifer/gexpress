package dev.mapselect.network.role.counterfeiter;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CounterfeiterForgePayload(int choice) implements CustomPayload {
    public static final Id<CounterfeiterForgePayload> ID = new Id<>(Identifier.of("gexpress", "counterfeiter_forge"));
    public static final PacketCodec<PacketByteBuf, CounterfeiterForgePayload> CODEC =
        PacketCodec.of((payload, buf) -> buf.writeVarInt(payload.choice), buf -> new CounterfeiterForgePayload(buf.readVarInt()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
