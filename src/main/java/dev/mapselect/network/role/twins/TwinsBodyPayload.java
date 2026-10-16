package dev.mapselect.network.role.twins;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record TwinsBodyPayload(UUID ownerId,UUID bodyId,boolean present) implements CustomPayload {
    public static final Id<TwinsBodyPayload> ID=new Id<>(Identifier.of(MapSelect.MOD_ID,"twins_body"));
    public static final PacketCodec<PacketByteBuf,TwinsBodyPayload> CODEC=PacketCodec.of(
        (p,b)->{b.writeUuid(p.ownerId);b.writeUuid(p.bodyId);b.writeBoolean(p.present);},
        b->new TwinsBodyPayload(b.readUuid(),b.readUuid(),b.readBoolean()));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
