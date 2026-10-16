package dev.mapselect.network.role.altruist;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AltruistFeedPayload(String line) implements CustomPayload {
	public static final Id<AltruistFeedPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "altruist_feed"));

	public AltruistFeedPayload {
		line = line == null ? "" : line;
	}

	public static final PacketCodec<PacketByteBuf, AltruistFeedPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeString(payload.line(), 160),
		buf -> new AltruistFeedPayload(buf.readString(160))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
