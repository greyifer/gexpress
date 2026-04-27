package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to trigger the Trickster's skin shuffle. */
public record TricksterUsePayload() implements CustomPayload {
	public static final CustomPayload.Id<TricksterUsePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "trickster_shuffle"));

	public static final PacketCodec<PacketByteBuf, TricksterUsePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new TricksterUsePayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
