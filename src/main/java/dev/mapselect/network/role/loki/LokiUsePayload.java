package dev.mapselect.network.role.loki;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record LokiUsePayload(int ability, UUID target) implements CustomPayload {
    public static final Id<LokiUsePayload> ID=new Id<>(Identifier.of("gexpress","loki_use"));
    public static final PacketCodec<PacketByteBuf,LokiUsePayload> CODEC=PacketCodec.of(
        (p,b)->{b.writeVarInt(p.ability);b.writeBoolean(p.target!=null);if(p.target!=null)b.writeUuid(p.target);},
        b->new LokiUsePayload(b.readVarInt(),b.readBoolean()?b.readUuid():null));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
