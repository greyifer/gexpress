package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BodyguardFeedPayload(String line) implements CustomPayload {
	public static final Id<BodyguardFeedPayload> ID = new Id<>(Identifier.of(MapSelect.MOD_ID, "bodyguard_feed"));

	public BodyguardFeedPayload {
		line = line == null ? "" : line;
	}

	public static final PacketCodec<PacketByteBuf, BodyguardFeedPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeString(payload.line(), 160),
		buf -> new BodyguardFeedPayload(buf.readString(160))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
