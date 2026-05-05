package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpyFeedPayload(String line) implements CustomPayload {
	public static final Id<SpyFeedPayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "spy_feed"));

	public SpyFeedPayload {
		line = line == null ? "" : line;
	}

	public static final PacketCodec<PacketByteBuf, SpyFeedPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeString(payload.line(), 160),
		buf -> new SpyFeedPayload(buf.readString(160))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
