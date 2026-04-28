package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server request to trigger the Harlequin's Dancing Carts ability. */
public record TricksterDancingCartsPayload() implements CustomPayload {
	public static final CustomPayload.Id<TricksterDancingCartsPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "trickster_dancing_carts"));

	public static final PacketCodec<PacketByteBuf, TricksterDancingCartsPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {},
		buf -> new TricksterDancingCartsPayload()
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
