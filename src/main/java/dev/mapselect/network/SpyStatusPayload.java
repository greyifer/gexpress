package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpyStatusPayload(int remainingTicks, String targetName) implements CustomPayload {
	public static final Id<SpyStatusPayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "spy_status"));

	public SpyStatusPayload {
		remainingTicks = Math.max(0, remainingTicks);
		targetName = targetName == null ? "" : targetName;
	}

	public static final PacketCodec<PacketByteBuf, SpyStatusPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.remainingTicks());
			buf.writeString(payload.targetName(), 64);
		},
		buf -> new SpyStatusPayload(buf.readVarInt(), buf.readString(64))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
