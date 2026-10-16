package dev.mapselect.network.role.loki;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;

public record LokiEffectsPayload(List<Effect> effects) implements CustomPayload {
    public record Effect(UUID player,int hex,int gravity) {}
    public LokiEffectsPayload { effects=List.copyOf(effects); }
    public static final Id<LokiEffectsPayload> ID=new Id<>(Identifier.of("gexpress","loki_effects"));
    public static final PacketCodec<PacketByteBuf,LokiEffectsPayload> CODEC=PacketCodec.of(
        (p,b)->{b.writeVarInt(p.effects.size());for(Effect e:p.effects){b.writeUuid(e.player);b.writeVarInt(e.hex);b.writeVarInt(e.gravity);}},
        b->{int size=b.readVarInt();if(size<0||size>512)throw new IllegalArgumentException("Too many Loki effects");List<Effect> list=new ArrayList<>();for(int i=0;i<size;i++)list.add(new Effect(b.readUuid(),b.readVarInt(),b.readVarInt()));return new LokiEffectsPayload(list);});
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
