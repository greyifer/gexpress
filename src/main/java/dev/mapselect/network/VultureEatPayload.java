package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to eat the living player the Vulture is looking at. */
public record VultureEatPayload() implements CustomPayload {
	public static final CustomPayload.Id<VultureEatPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "vulture_eat"));

	public static final PacketCodec<PacketByteBuf, VultureEatPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new VultureEatPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
