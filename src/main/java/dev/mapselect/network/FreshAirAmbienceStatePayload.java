package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client local fresh-air area ambience state. */
public record FreshAirAmbienceStatePayload(boolean playOutsideAmbience) implements CustomPayload {
	public static final CustomPayload.Id<FreshAirAmbienceStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "fresh_air_ambience_state"));

	public static final PacketCodec<PacketByteBuf, FreshAirAmbienceStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeBoolean(payload.playOutsideAmbience()),
		buf -> new FreshAirAmbienceStatePayload(buf.readBoolean())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
