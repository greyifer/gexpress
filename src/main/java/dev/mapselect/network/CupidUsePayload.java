package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CupidUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<CupidUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "cupid_use"));

	public static final PacketCodec<PacketByteBuf, CupidUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new CupidUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
