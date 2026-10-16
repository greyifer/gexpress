package dev.mapselect.network.role.spy;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpyListenStatePayload(int energyTicks, int maximumTicks, boolean listening) implements CustomPayload {
	public static final Id<SpyListenStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "spy_listen_state"));

	public SpyListenStatePayload {
		maximumTicks = Math.max(1, maximumTicks);
		energyTicks = Math.max(0, Math.min(maximumTicks, energyTicks));
	}

	public static final PacketCodec<PacketByteBuf, SpyListenStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.energyTicks());
			buf.writeVarInt(payload.maximumTicks());
			buf.writeBoolean(payload.listening());
		},
		buf -> new SpyListenStatePayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
