package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to rewind the round for the Time Master. */
public record TimeMasterUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<TimeMasterUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "time_master_use"));

	public static final PacketCodec<PacketByteBuf, TimeMasterUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new TimeMasterUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
