package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to freeze the player the Time Master is looking at. */
public record TimeMasterFreezeUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<TimeMasterFreezeUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "time_master_freeze_use"));

	public static final PacketCodec<PacketByteBuf, TimeMasterFreezeUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new TimeMasterFreezeUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
