package dev.mapselect.network.config;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Small independent config channel for Doomsayer settings. */
public record DoomsayerConfigPayload(int guessesToWin, boolean diesOnWrongGuess)
		implements CustomPayload {
	public static final Id<DoomsayerConfigPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "doomsayer_config"));

	public static final PacketCodec<PacketByteBuf, DoomsayerConfigPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(payload.guessesToWin());
			buf.writeBoolean(payload.diesOnWrongGuess());
		},
		buf -> new DoomsayerConfigPayload(buf.readVarInt(), buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
