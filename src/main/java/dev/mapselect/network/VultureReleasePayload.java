package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to release one player from the Vulture's belly. */
public record VultureReleasePayload() implements CustomPayload {
	public static final CustomPayload.Id<VultureReleasePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "vulture_release"));

	public static final PacketCodec<PacketByteBuf, VultureReleasePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new VultureReleasePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
