package dev.mapselect.network.role.loki;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record LokiStatePayload(UUID firstTarget,String firstName,int voices,int hex,int fuse,int cloneCooldown,int cloneRemaining) implements CustomPayload {
    public static final Id<LokiStatePayload> ID=new Id<>(Identifier.of("gexpress","loki_state"));
    public static final PacketCodec<PacketByteBuf,LokiStatePayload> CODEC=PacketCodec.of(
        (p,b)->{b.writeBoolean(p.firstTarget!=null);if(p.firstTarget!=null)b.writeUuid(p.firstTarget);b.writeString(p.firstName,64);b.writeVarInt(p.voices);b.writeVarInt(p.hex);b.writeVarInt(p.fuse);b.writeVarInt(p.cloneCooldown);b.writeVarInt(p.cloneRemaining);},
        b->new LokiStatePayload(b.readBoolean()?b.readUuid():null,b.readString(64),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
    public static LokiStatePayload empty(){return new LokiStatePayload(null,"",0,0,0,0,0);}
}
