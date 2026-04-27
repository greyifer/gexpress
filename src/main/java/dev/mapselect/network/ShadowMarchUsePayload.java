package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to trigger The Silent's Shadow March ability. */
public record ShadowMarchUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<ShadowMarchUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "shadow_march_use"));

	public static final PacketCodec<PacketByteBuf, ShadowMarchUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new ShadowMarchUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
