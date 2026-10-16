package dev.mapselect.network.role.counterfeiter;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CounterfeiterStatePayload(boolean active, boolean spent, int choices,int selected) implements CustomPayload {
    public static final Id<CounterfeiterStatePayload> ID = new Id<>(Identifier.of("gexpress", "counterfeiter_state"));
    public static final PacketCodec<PacketByteBuf, CounterfeiterStatePayload> CODEC = PacketCodec.of(
        (payload, buf) -> { buf.writeBoolean(payload.active); buf.writeBoolean(payload.spent); buf.writeVarInt(payload.choices);buf.writeVarInt(payload.selected); },
        buf -> new CounterfeiterStatePayload(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),buf.readVarInt()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
