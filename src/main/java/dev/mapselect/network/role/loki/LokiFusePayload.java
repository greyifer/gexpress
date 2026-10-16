package dev.mapselect.network.role.loki;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record LokiFusePayload(int remaining) implements CustomPayload {
    public static final Id<LokiFusePayload> ID=new Id<>(Identifier.of("gexpress","loki_fuse"));
    public static final PacketCodec<PacketByteBuf,LokiFusePayload> CODEC=PacketCodec.of((p,b)->b.writeVarInt(p.remaining),b->new LokiFusePayload(b.readVarInt()));
    public Id<? extends CustomPayload> getId(){return ID;}
}
